package org.wy.engine.dashboard

import com.wy.layout.AlignItem
import org.wy.engine.*
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.animation.DefaultFrameSource
import org.wy.engine.animation.FrameSubscription
import org.wy.engine.helper.ButtonBase
import org.wy.engine.helper.DialogBase
import org.wy.engine.helper.DropdownBase
import org.wy.engine.helper.OverlayBase
import org.wy.engine.helper.Placement
import org.wy.engine.helper.ScrollBarStyle
import org.wy.engine.helper.SimpleScrollNode
import org.wy.engine.helper.dialog
import org.wy.engine.helper.drag
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.GrowChild
import org.wy.engine.layout.LayoutDirection
import org.wy.lib.StoreRef
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.memo
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sign
import kotlin.math.sqrt

// ════════════════════════════════════════════════════
internal fun StateHolder<Node, List<Node>>.dTrades(trades: () -> List<Trade>) {
    object : SimpleScrollNode(this) {
        override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f
        override val scrollBarStyle: ScrollBarStyle get() = DARK_SCROLL_BAR
        override val contentGap: Float get() = 0f

        override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
            dRow(gap = 8f, height = 24f, padX = 10f) {
                dText({ "Price" }, 9f, { D_TEXT3 }, 600)
                dSpacer()
                dText({ "Amount" }, 9f, { D_TEXT3 }, 600, width = 72f, align = TextAlign.END)
                dText({ "Time" }, 9f, { D_TEXT3 }, 600, width = 56f, align = TextAlign.END)
            }
            trades().forEach { t ->
                dRow(gap = 8f, height = 20f, padX = 10f) {
                    dText(
                        { fmtPrice(t.price) }, 10.5f,
                        { if (t.buyerMaker) D_DOWN else D_UP }
                    )
                    dSpacer()
                    dText({ fmtQty(t.qty) }, 10.5f, { D_TEXT2 }, width = 72f, align = TextAlign.END)
                    dText(
                        { fmtClockHms(t.time) }, 10f, { D_TEXT3 },
                        width = 56f, align = TextAlign.END
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════
// 十三、盘口（复刻 orderbook.ooc + orderbook-rows.ts）
//
// 卖盘在上（远→近）、最新价居中、买盘在下（近→远），底部 B/S 比例条。
// 表头工具行：三个模式图标（Both / Bids / Asks）+ 精度选择；
// 窗格标题栏 ⋯ 菜单：Avg.&Sum / Ratio / Rounding / Amount|Cumulative / Animations。
// ════════════════════════════════════════════════════
internal const val BOOK_ROW_H = 18f

/**
 * 盘口行。[row] / [rounding] 走 lambda 通道：argChildren 只执行一次，
 * 传值是启动快照；lambda 在文本/深度条每次求值时读活信号（renderForEach
 * 的 EachTime.value 会登记依赖），行情心跳推送新档位后行内容才会跟着刷新。
 */
internal fun StateHolder<Node, List<Node>>.dBookRow(
    row: () -> BookMath.Row,
    rounding: () -> Boolean,
    onClick: () -> Unit
): ButtonBase = object : ButtonBase(this) {
    override val directionJustify: DirectionJustify get() = DirectionJustify.start
    override val alignItem: AlignItem get() = AlignItem.center
    override val alignFix: Boolean get() = true
    override val argHeight: LayoutSize get() = LayoutSize(BOOK_ROW_H, false)

    override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
        if (direction == Direction.x) 10f else 0f

    override fun onClick() = onClick()

    override fun drawContent(canvas: PlatformCanvas) {
        // 深度条贴右生长（先画，文字压在 relative 层上）
        val r = row()
        val barW = (outerWidth - 20f) * r.percent / 100f
        if (barW > 0.5f) {
            canvas.fillRect(
                outerWidth - 10f - barW, 0f, barW, outerHeight,
                if (r.ask) D_DOWN_BG else D_UP_BG
            )
        }
        if (hovered || pressed) canvas.fillRect(0f, 0f, outerWidth, outerHeight, D_HOVER)
    }

    override fun drawFocusRing(canvas: PlatformCanvas) {
        strokeOuterRing(canvas, 1f, 2f, D_ACCENT, 1.5f)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        dText({ fmtPrice(row().price) }, 10f, { if (row().ask) D_DOWN else D_UP })
        dSpacer()
        dText({ fmtQty(row().qty) }, 10f, { D_TEXT2 }, align = TextAlign.END, grow = true)
        dText(
            { fmtTotal(row().total, !rounding()) }, 10f, { D_TEXT3 },
            width = 62f, align = TextAlign.END
        )
    }
}

/** 模式图标（Both / Bids only / Asks only），16×16 viewBox 缩放到按钮内。 */
internal fun StateHolder<Node, List<Node>>.dModeIconButton(
    value: String,
    current: () -> String,
    onPick: (String) -> Unit
): ButtonBase = object : ButtonBase(this) {
    override val directionJustify: DirectionJustify get() = DirectionJustify.center
    override val argWidth: LayoutSize get() = LayoutSize(20f, false)
    override val argHeight: LayoutSize get() = LayoutSize(20f, false)

    override fun onClick() = onPick(value)

    override fun drawContent(canvas: PlatformCanvas) {
        val on = current() == value
        if (hovered || pressed) fillOuterRoundRect(canvas, 3f, D_HOVER)
        val s = outerWidth / 16f
        val alpha = if (on) 1f else 0.4f
        fun rect(x: Float, y: Float, w: Float, h: Float, color: ColorInt) {
            if (h <= 0f) return
            canvas.fillRect(x * s, y * s, w * s, h * s, color.withAlpha(alpha))
        }
        // 左块：both = 上卖下买两块；单侧模式 = 合成一整条
        val both = current() == "both"
        val topH = if (both) 4.66f else 10.66f
        val topColor = if (value == "bids") D_UP else D_DOWN
        rect(2.66f, 2.66f, 4.66f, topH, topColor)
        if (both) rect(2.66f, 8.66f, 4.66f, 4.66f, D_UP)
        // 右块：三行挂单
        val rows = D_TEXT2
        rect(8.66f, 2.66f, 4.66f, 2.66f, rows)
        rect(8.66f, 6.66f, 4.66f, 2.66f, rows)
        rect(8.66f, 10.66f, 4.66f, 2.66f, rows)
    }

    override fun drawFocusRing(canvas: PlatformCanvas) {
        strokeOuterRing(canvas, 1f, 4f, D_ACCENT, 1.5f)
    }
}

internal fun StateHolder<Node, List<Node>>.dOrderbook(
    depth: () -> Depth?,
    ticker: () -> Ticker?,
    mode: StoreRef<String>,
    avgSum: StoreRef<Boolean>,
    ratio: StoreRef<Boolean>,
    rounding: StoreRef<Boolean>,
    cumulative: StoreRef<Boolean>,
    step: StoreRef<Double>,
    rowsPerSide: () -> Int,
    onPickPrice: (Double) -> Unit
) {
    val g = consume(engineGlobalContext)!!

    /** 当前视图：深度 + 精度 + 条口径，三个开关都参与同一次 bookView。 */
    fun viewOf(): BookMath.View =
        BookMath.view(depth(), rowsPerSide(), step.value, cumulative.value)

    object : RectNode(this), FlexParam, GrowChild {
        override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.y
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val alignFix: Boolean get() = true
        override val gap: Float get() = 0f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            // ── 表头工具行：模式图标 + 精度 ──
            dRow(gap = 6f, height = 22f, padX = 6f, justify = DirectionJustify.between) {
                dRow(gap = 2f, height = 20f) {
                    dModeIconButton("both", { mode.value }) { mode.value = it }
                    dModeIconButton("bids", { mode.value }) { mode.value = it }
                    dModeIconButton("asks", { mode.value }) { mode.value = it }
                }
                // 精度选择（锚点 = 本按钮）
                val stepBtn = object : ButtonBase(this), GrowChild {
                    override val debugName: String get() = "盘口精度按钮"
                    override val directionJustify: DirectionJustify get() = DirectionJustify.center
                    override val argHeight: LayoutSize get() = LayoutSize(18f, false)
                    // 精度按钮必须按标签内容测量出确定宽度，否则被 between 行回读 outerSize → 重入崩溃。
                    private val stepTextWidth by memo {
                        buildParagraph(
                            listOf(RichTextSpan(fmtStep(viewOf().step), RichTextStyle(fontSize = 10f))),
                            Float.MAX_VALUE
                        ).width
                    }
                    override val argWidth: LayoutSize get() = LayoutSize(stepTextWidth, true)
                    override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                        if (direction == Direction.x) 7f else 0f

                    override fun onClick() {
                        // 锚点 = 本按钮自身（匿名对象即 LayoutNode）；
                        // 不能在自身初始化里引用局部名，先捕获 this
                        val anchor: LayoutNode = this
                        g.appendPop { pop ->
                            dMenu(
                                anchor = anchor, width = 130f,
                                // 锚点在表行右端：菜单右缘与锚点右缘对齐
                                placement = Placement.BottomEnd,
                                onClose = { g.removePop(pop) }
                            ) {
                                dMenuLabel("Price aggregation")
                                BookMath.steps(BookMath.bestPrice(depth())).forEach { s ->
                                    dMenuItem({ fmtStep(s) }) { step.value = s }
                                }
                            }
                        }
                    }

                    override fun drawContent(canvas: PlatformCanvas) {
                        strokeOuterRoundRect(canvas, 3f, if (hovered) D_LINE else D_LINE_SOFT, 1f)
                        if (hovered || pressed) fillOuterRoundRect(canvas, 3f, D_HOVER)
                    }

                    override fun drawFocusRing(canvas: PlatformCanvas) {
                        strokeOuterRing(canvas, 1f, 4f, D_ACCENT, 1.5f)
                    }

                    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                        dText({ fmtStep(viewOf().step) }, 10f, { D_TEXT2 })
                    }
                }
            }

            // ── 列头 ──
            dRow(gap = 8f, height = 18f, padX = 10f) {
                dText({ "Price" }, 9f, { D_TEXT3 }, 600)
                dSpacer()
                dText({ "Amount" }, 9f, { D_TEXT3 }, 600, align = TextAlign.END, grow = true)
                dText({ "Total" }, 9f, { D_TEXT3 }, 600, width = 62f, align = TextAlign.END)
            }

            // ── 卖盘（远价在上，最优价贴中间行）──
            val asksBlock = object : RectNode(this), FlexParam, GrowChild {
                override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.y
                override val directionJustify: DirectionJustify get() = DirectionJustify.end
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val alignFix: Boolean get() = true
                override val gap: Float get() = 0f
                override val hide: Boolean get() = mode.value == "bids"

                override fun draw(canvas: PlatformCanvas) {
                    // 对齐 web 的 overflow-hidden：行数下限 3 大于块高时行会溢出，
                    // 裁在自己的块内，避免压到列头 / 中间带。
                    canvas.save()
                    canvas.clipRect(0f, 0f, outerWidth, outerHeight)
                    super.draw(canvas)
                    canvas.restore()
                }

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    // 行列表走 renderForEach：forEach lambda 里读 viewOf()（心跳
                    // rev / 窗口高 / 精度 / 条口径都是它的依赖），信号一变按
                    // 下标 key 复用重建行；argChildren 一次性契约下仍保持动态。
                    renderForEach({ callback ->
                        viewOf().asks.forEachIndexed { i, r -> callback(i, r) }
                    }) { _, each ->
                        dBookRow({ each.value }, { rounding.value }) {
                            onPickPrice(each.value.price)
                        }
                    }
                }
            }

            // ── 卖盘 Avg.&Sum（对齐 web：卖盘汇总紧贴中间带上沿）──
            renderForEach({ callback ->
                if (avgSum.value) callback(0, Unit)
            }) { _, _ ->
                dRow(gap = 8f, height = 15f, padX = 10f) {
                    dText({ "AVG ${fmtPrice(BookMath.summary(viewOf().asks).avg)}" }, 9f, { D_DOWN })
                    dSpacer()
                    dText({ "Σ ${fmtTotal(BookMath.summary(viewOf().asks).sum, !rounding.value)}" }, 9f, { D_TEXT3 })
                }
            }

            // ── 中间分割带：最新价 + 涨跌箭头 + 价差 ──
            // 对齐 web（orderbook.ooc）：夹在卖盘汇总与买盘之间，整宽淡底色 + 上下 1px 描边；
            // 内容仍全部走 lambda 读活信号（心跳 / 切币后原地刷新）。
            object : RectNode(this), FlexParam, GrowChild {
                override val debugName: String get() = "盘口中间带"
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.x
                override val directionJustify: DirectionJustify get() = DirectionJustify.start
                override val alignItem: AlignItem get() = AlignItem.center
                override val alignFix: Boolean get() = true
                override val gap: Float get() = 8f
                override val argHeight: LayoutSize get() = LayoutSize(28f, false)

                override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                    if (direction == Direction.x) 10f else 0f

                override fun draw(canvas: PlatformCanvas) {
                    fillOuterRect(canvas, D_BAND)
                    canvas.fillRect(0f, 0f, outerWidth, 1f, D_LINE_SOFT)
                    canvas.fillRect(0f, outerHeight - 1f, outerWidth, 1f, D_LINE_SOFT)
                    super.draw(canvas)
                }

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    val upOf = { (ticker()?.change ?: 0.0) >= 0 }
                    dText({ fmtPrice(ticker()?.last) }, 15f, { if (upOf()) D_UP else D_DOWN }, 600)
                    dText({ if (upOf()) "↑" else "↓" }, 10f, { if (upOf()) D_UP else D_DOWN })
                    dSpacer()
                    dText(
                        {
                            val d = depth()
                            val ask = d?.asks?.firstOrNull()?.price ?: 0.0
                            val bid = d?.bids?.firstOrNull()?.price ?: 0.0
                            if (ask > 0 && bid > 0) "Spread ${fmtPrice(ask - bid)}" else "Spread --"
                        },
                        9.5f, { D_TEXT3 }
                    )
                }
            }

            // ── 买盘（最优价贴中间行）──
            val bidsBlock = object : RectNode(this), FlexParam, GrowChild {
                override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.y
                override val directionJustify: DirectionJustify get() = DirectionJustify.start
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val alignFix: Boolean get() = true
                override val gap: Float get() = 0f
                override val hide: Boolean get() = mode.value == "asks"

                override fun draw(canvas: PlatformCanvas) {
                    // 同卖盘：overflow-hidden 兜底，行溢出时裁在块内（不压到比例条）。
                    canvas.save()
                    canvas.clipRect(0f, 0f, outerWidth, outerHeight)
                    super.draw(canvas)
                    canvas.restore()
                }

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    renderForEach({ callback ->
                        viewOf().bids.forEachIndexed { i, r -> callback(i, r) }
                    }) { _, each ->
                        dBookRow({ each.value }, { rounding.value }) {
                            onPickPrice(each.value.price)
                        }
                    }
                }
            }

            // ── 买盘 Avg.&Sum（对齐 web：买盘汇总在买盘下方）──
            renderForEach({ callback ->
                if (avgSum.value) callback(0, Unit)
            }) { _, _ ->
                dRow(gap = 8f, height = 15f, padX = 10f) {
                    dText({ "AVG ${fmtPrice(BookMath.summary(viewOf().bids).avg)}" }, 9f, { D_UP })
                    dSpacer()
                    dText({ "Σ ${fmtTotal(BookMath.summary(viewOf().bids).sum, !rounding.value)}" }, 9f, { D_TEXT3 })
                }
            }

            // ── B/S 比例条（renderForEach：开关关闭时不建）──
            renderForEach({ callback ->
                if (ratio.value) callback(0, Unit)
            }) { _, _ ->
                dRow(gap = 6f, height = 16f, padX = 10f) {
                    dText({ "B ${fmtShare(viewOf().buyRatio)}%" }, 9f, { D_UP }, 600, width = 42f)
                    // 轨道：卖色淡底；填充：买色，宽度 = 买盘占比
                    // 纯叶子节点（无子节点）不要挂 layout：一旦声明成 FlexObject，
                    // argSize 会按「由子节点撑起」求值，无子节点 → 宽度恒为 0（轨道不显示）。
                    object : RectNode(this), GrowChild {
                        override fun argGrow(direction: Direction): Float =
                            if (direction == Direction.x) 1f else 0f
                        override val argHeight: LayoutSize get() = LayoutSize(3f, false)

                        override fun draw(canvas: PlatformCanvas) {
                            fillOuterRoundRect(canvas, 1.5f, D_DOWN.withAlpha(0.28f))
                            val bidShare = viewOf().buyRatio
                            val w = (outerWidth * bidShare).toFloat().coerceIn(0f, outerWidth)
                            if (w > 0.5f) {
                                canvas.save()
                                canvas.clipRRect(0f, 0f, outerWidth, outerHeight, 1.5f)
                                canvas.fillRect(0f, 0f, w, outerHeight, D_UP.withAlpha(0.75f))
                                canvas.restore()
                            }
                        }
                    }
                    dText(
                        { "${fmtShare(1.0 - viewOf().buyRatio)}% S" },
                        9f, { D_DOWN }, 600, width = 44f, align = TextAlign.END
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════
// 十四、模拟下单（复刻 ticket.ooc）
//
// 纯 UI 演示：买入 / 卖出 tab、限价（点盘口优先，否则跟最新价）、
// Amount(USDT) 输入、25/50/75/MAX（BALANCE = 10000）、Est. qty、
// 提交只出提示（note），不接任何交易接口。
// ════════════════════════════════════════════════════
