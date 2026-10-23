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
// 金融看板（深色行情终端）
//
// 复刻自 ooc example 的 finance-dashboard（pages/finance-dashboard.ooc）：
//   三列可拖拽终端 = header（交易对 / 周期 / DEMO / 状态）
//   + 左列（自选 Watchlist + Market stats）
//   + 中列（K 线 Chart + Market activity: Depth|Trades）
//   + 右列（Order book + Paper order）+ footer 免责声明。
//
// 与蓝本的两点约定差异：
//   1. 配色按中式行情惯例：涨红跌绿（买盘红 / 卖盘绿），异于 ooc 的
//      emerald/rose（涨绿跌红）；
//   2. 数据为确定性模拟（种子化 RNG + 固定 epoch 锚点，无 Date() 依赖），
//      不接任何网络接口，保证每次截图完全可复现。
//
// 引擎侧的关键契约（踩过的坑，改动前先读）：
//   · 行容器默认 justify=grow 包裹内容宽；行内嵌列时，列必须带 grow 子节点
//     （1fr），否则列的宽度回读行主轴缓存 → 重复进入死循环；
//   · 包裹行（justify=grow 且无 grow 子节点）内的文本必须 autoWidth；
//   · 行去定高（alignFix=false）时子节点必须自带高度，否则 StackLayout 抛
//     「子节点应该有它自己的尺寸」；
//   · 页面级信号（交易对 / 视口 / 分拽尺寸）必须建在页面节点对象体上：
//     子树随窗口尺寸整体重建，建在 argChildren 里的信号会丢失；
//   · 盘口每侧行数由「窗口高度预算」推导（读自身高度会递归，见
//     dDashboardPage 的 SHELL_* 常量），行多时由窗格内容区裁剪兜底。
// ════════════════════════════════════════════════════

// ════════════════════════════════════════════════════
// 十六、看板页面
//
// 结构（对齐 finance-dashboard.ooc）：
//   header（52）：logo / 交易对+最新价+涨跌 / 交易对 tabs / 周期 tabs /
//                 DEMO / 状态点 / （窄窗口逐级收起）
//   main（grow）：左列（自选 grow + 分拽 + 概览定高）
//                 ｜ 中列（K 线 grow + 分拽 + 行情活动定高：Depth|Trades）
//                 ｜ 右列（盘口 grow + 分拽 + 模拟下单定高）
//   footer（24）：免责声明 + 提交提示 + 重置布局
//
// 高度预算：页面高度 = 窗口高 - 外壳（顶栏 56 + 状态栏 30 + 内容边距 32）
//           - segTabs 36 - 卡片间距 12；盘口每侧行数由「列高 - 底 pane 高」
//           推导（读自身高度会递归进布局缓存，故用窗口高度预算近似）。
// ════════════════════════════════════════════════════
internal const val SHELL_TOP_BAR = 56f
internal const val SHELL_STATUS_BAR = 30f
internal const val CONTENT_PAD = 16f
internal const val SEG_TABS_H = 36f
internal const val CONTENT_GAP = 12f
internal const val DASH_HEADER_H = 52f
internal const val DASH_FOOTER_H = 24f
/** 盘口固定 chrome：工具行 + 列头 + 中间行 + 比例条。 */
internal const val BOOK_OVERHEAD = 22f + 18f + 28f + 16f

internal fun StateHolder<Node, List<Node>>.dDashboardPage(
    active: StoreRef<DemoTab>,
    windowWidth: () -> Float,
    windowHeight: () -> Float
): RectNode = object : RectNode(this), FlexParam, GrowChild {
    override val hide: Boolean get() = active.value != DemoTab.DASHBOARD
    override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.y
    override val directionJustify: DirectionJustify get() = DirectionJustify.start
    override val alignItem: AlignItem get() = AlignItem.stretch
    override val alignFix: Boolean get() = true
    override val gap: Float get() = 0f

    // ── 页面级状态（建在节点对象体上：子树随窗口尺寸重建时存活）──
    private val symbol = createSignal("BTCUSDT")
    private val interval = createSignal("15m")
    private val activity = createSignal("Depth")
    private val bookMode = createSignal("both")
    private val bookAvgSum = createSignal(false)
    private val bookRatio = createSignal(true)
    private val bookRounding = createSignal(true)
    private val bookCumulative = createSignal(false)
    private val bookStep = createSignal(0.0)
    private val limitPrice = createSignal<Double?>(null)
    private val chartCount = createSignal(ChartGeom.DEFAULT_VISIBLE)
    private val chartOffset = createSignal(0)
    private val depthZoom = createSignal(1.0)
    private val note = createSignal("")

    // ── 行情心跳：盘口/深度的版本号，300ms 节流 +1，消费方按 rev 取数 ──
    private val marketRev = createSignal(0)

    /** 心跳订阅句柄：null = 未订阅（holder 重建后由 argChildren 重新拉起）。 */
    private var tickSub: FrameSubscription? = null
    // ── 布局尺寸（持久化，口径对齐 ooc layout.ts：加载 clamp / 拖拽落盘 / 重置清除）──
    private fun loadF(key: String, def: Float, min: Float, max: Float): Float {
        val raw = prefs.get(key, "").toFloatOrNull() ?: return def
        return if (raw.isFinite()) raw.coerceIn(min, max) else def
    }

    private val leftWidth = createSignal(loadF("dash.leftWidth", 248f, 176f, 420f))
    private val rightWidth = createSignal(loadF("dash.rightWidth", 316f, 224f, 520f))
    private val leftBottom = createSignal(loadF("dash.leftBottom", 224f, 120f, 420f))
    private val centerBottom = createSignal(loadF("dash.centerBottom", 218f, 120f, 420f))
    private val rightBottom = createSignal(loadF("dash.rightBottom", 200f, 140f, 420f))

    /** 拖拽结束后落盘（与 ooc saveLayout 同时机：不以每帧拖动写盘）。 */
    private fun saveLayout() {
        prefs.putFloat("dash.leftWidth", leftWidth.value)
        prefs.putFloat("dash.rightWidth", rightWidth.value)
        prefs.putFloat("dash.leftBottom", leftBottom.value)
        prefs.putFloat("dash.centerBottom", centerBottom.value)
        prefs.putFloat("dash.rightBottom", rightBottom.value)
    }

    private val g = engineGlobal

    /** 首访免责声明只自动弹一次（argChildren 会随信号重建反复执行，需本地护栏）。 */
    private var noticeAutoShown = false

    private fun columnHeight(): Float =
        windowHeight() - SHELL_TOP_BAR - SHELL_STATUS_BAR - CONTENT_PAD * 2 -
            SEG_TABS_H - CONTENT_GAP - DASH_HEADER_H - DASH_FOOTER_H

    /** 盘口每侧行数：单侧模式下吃满整块，两侧模式对半分。 */
    private fun bookRows(): Int {
        val col = columnHeight()
        val paneH = col - rightBottom.value - SPLIT_W
        val contentH = paneH - PANE_HEAD_H
        val sideH = if (bookMode.value == "both") {
            (contentH - BOOK_OVERHEAD) / 2f
        } else {
            contentH - BOOK_OVERHEAD
        }
        return BookMath.rowCount(sideH)
    }

    private fun resetLayout() {
        leftWidth.value = 248f
        rightWidth.value = 316f
        leftBottom.value = 224f
        centerBottom.value = 218f
        rightBottom.value = 200f
        chartCount.value = ChartGeom.DEFAULT_VISIBLE
        chartOffset.value = 0
        depthZoom.value = 1.0
        // 清除持久值：下次启动回到默认布局（对齐 ooc resetLayout 删 key 语义）
        for (k in listOf(
            "dash.leftWidth", "dash.rightWidth", "dash.leftBottom",
            "dash.centerBottom", "dash.rightBottom"
        )) prefs.remove(k)
        note.value = "布局已重置"
    }

    override fun draw(canvas: PlatformCanvas) {
        fillOuterRoundRect(canvas, 12f, D_BG)
        super.draw(canvas)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        val narrow = windowWidth() < 1000f
        val veryNarrow = windowWidth() < 880f

        // 行情心跳：帧源 UI 线程回调、300ms 节流写 marketRev；
        // 挂 addDestroy 与本 holder 同生命周期——子树重建时旧订阅取消、新订阅拉起。
        if (tickSub == null) {
            var lastTickMs = 0f
            val sub = DefaultFrameSource.subscribe({ now ->
                if (now - lastTickMs >= 300f) {
                    lastTickMs = now
                    marketRev.value = marketRev.value + 1
                }
                false
            }) { }
            tickSub = sub
            addDestroy {
                sub.cancel()
                tickSub = null
            }
        }

        // 首访自动弹出免责声明（对齐 ooc whenFirstVisit + noticeSeen 语义，
        // 标记持久化在 java.util.prefs，桌面端 localStorage 对应物）
        if (!noticeAutoShown && prefs.get("dash.noticeSeen", "") != "1") {
            noticeAutoShown = true
            g.appendPop { pop ->
                dNoticeDialog(onClose = {
                    g.removePop(pop)
                    prefs.put("dash.noticeSeen", "1")
                })
            }
        }

        // ── header ──
        dRow(gap = 10f, height = DASH_HEADER_H, padX = 12f) {
            // logo 瓦片
            object : RectNode(this) {
                override val argWidth: LayoutSize get() = LayoutSize(26f, false)
                override val argHeight: LayoutSize get() = LayoutSize(26f, false)

                override fun draw(canvas: PlatformCanvas) {
                    fillOuterRoundRect(canvas, 6f, D_ACCENT)
                    drawTextCentered(canvas, "W", 13f, 13f, 11f, rgba(255, 255, 255), 700)
                    super.draw(canvas)
                }
            }
            if (!veryNarrow) {
                dText({ "WY Markets" }, 11f, { D_TEXT2 }, 600)
                // 分隔竖线
                object : RectNode(this) {
                    override val argWidth: LayoutSize get() = LayoutSize(1f, false)
                    override val argHeight: LayoutSize get() = LayoutSize(22f, false)

                    override fun draw(canvas: PlatformCanvas) {
                        fillOuterRect(canvas, D_LINE)
                    }
                }
            }
            // 交易对 + 最新价 + 涨跌
            dRow(gap = 8f, height = 30f) {
                dText({ symbol.value }, 13f, { D_TEXT }, 600)
                val t = MarketData.tickerOf(symbol.value)
                dText({ fmtPrice(t.last) }, 13f, { D_TEXT })
                dText(
                    { fmtPercent(t.changePercent) }, 11f,
                    { if (t.changePercent >= 0) D_UP else D_DOWN }, 600
                )
            }
            dSpacer()
            // 交易对 tabs（窄窗口只留前两个，完整列表在自选面板）
            val symbolTabs = if (veryNarrow) 2 else MarketData.symbols.size
            MarketData.symbols.take(symbolTabs).forEach { s ->
                dTabButton({ s }, { symbol.value == s }) {
                    if (symbol.value != s) {
                        symbol.value = s
                        limitPrice.value = null
                        note.value = ""
                    }
                }
            }
            // 周期 tabs（窄窗口隐藏）
            if (!narrow) {
                MarketData.intervals.forEach { itv ->
                    dTabButton({ itv }, { interval.value == itv }, height = 22f, padX = 7f) {
                        if (interval.value != itv) {
                            interval.value = itv
                            chartCount.value = ChartGeom.DEFAULT_VISIBLE
                            chartOffset.value = 0
                        }
                    }
                }
            }
            // DEMO 徽章 → 免责声明（手动打开同样回写已读标记）
            dTabButton({ "DEMO" }, { false }, height = 20f, padX = 7f, chip = D_AMBER) {
                g.appendPop { pop ->
                    dNoticeDialog(onClose = {
                        g.removePop(pop)
                        prefs.put("dash.noticeSeen", "1")
                    })
                }
            }
            // 状态点
            object : RectNode(this) {
                override val argWidth: LayoutSize get() = LayoutSize(6f, false)
                override val argHeight: LayoutSize get() = LayoutSize(6f, false)

                override fun draw(canvas: PlatformCanvas) {
                    fillOuterOval(canvas, D_AMBER)
                }
            }
            if (!narrow) {
                dText({ "MOCK" }, 9f, { D_TEXT3 }, 600)
            }
        }

        // ── main：三列 ──
        dRow(gap = 0f, growY = true, align = AlignItem.stretch, justify = DirectionJustify.start) {
            // 左列：自选 + 概览
            dCol(gap = 0f, widthOf = { leftWidth.value }) {
                dPane("Watchlist", "24h", grow = true) {
                    dWatchlist(
                        tickers = { MarketData.watchTickers() },
                        activeSymbol = { symbol.value }
                    ) { s ->
                        symbol.value = s
                        limitPrice.value = null
                        note.value = ""
                    }
                }
                dSplitH({ leftBottom.value }, 120f, 420f, -1f) { leftBottom.value = it; saveLayout() }
                dPane("Market stats", "Binance", heightOf = { leftBottom.value }) {
                    dStats { MarketData.tickerOf(symbol.value) }
                }
            }

            dSplitV({ leftWidth.value }, 176f, 420f, 1f) { leftWidth.value = it; saveLayout() }

            // 中列：K 线 + 行情活动
            dCol(gap = 0f, grow = true) {
                dPane("Chart", interval.value, grow = true) {
                    dChartPane(
                        klines = { MarketData.klinesOf(symbol.value, interval.value) },
                        count = chartCount,
                        offset = chartOffset
                    )
                }
                dSplitH({ centerBottom.value }, 120f, 420f, -1f) { centerBottom.value = it; saveLayout() }
                dPane(
                    "Market activity",
                    "Trades / Depth",
                    heightOf = { centerBottom.value },
                    tools = {
                        dTabButton({ "Depth" }, { activity.value == "Depth" }, height = 18f, padX = 7f) {
                            activity.value = "Depth"
                        }
                        dTabButton({ "Trades" }, { activity.value == "Trades" }, height = 18f, padX = 7f) {
                            activity.value = "Trades"
                        }
                    }
                ) {
                    if (activity.value == "Depth") {
                        dCol(gap = 0f, growY = true) {
                            // 图例 + 缩放控件行
                            dRow(gap = 8f, height = 20f, padX = 8f, justify = DirectionJustify.between) {
                                val geo = DepthGeom.geo(MarketData.depthOf(symbol.value, marketRev.value), depthZoom.value)
                                dRow(gap = 5f, height = 14f) {
                                    object : RectNode(this) {
                                        override val argWidth: LayoutSize get() = LayoutSize(10f, false)
                                        override val argHeight: LayoutSize get() = LayoutSize(6f, false)

                                        override fun draw(canvas: PlatformCanvas) {
                                            fillOuterRoundRect(canvas, 1.5f, D_UP.withAlpha(0.7f))
                                        }
                                    }
                                    dText({ "BID" }, 8.5f, { D_UP }, 600)
                                    dText({ fmtQty(geo.bidMax) }, 8.5f, { D_TEXT3 })
                                    object : RectNode(this) {
                                        override val argWidth: LayoutSize get() = LayoutSize(10f, false)
                                        override val argHeight: LayoutSize get() = LayoutSize(6f, false)

                                        override fun draw(canvas: PlatformCanvas) {
                                            fillOuterRoundRect(canvas, 1.5f, D_DOWN.withAlpha(0.7f))
                                        }
                                    }
                                    dText({ "ASK" }, 8.5f, { D_DOWN }, 600)
                                    dText({ fmtQty(geo.askMax) }, 8.5f, { D_TEXT3 })
                                    dText({ "价差幅度" }, 8.5f, { D_TEXT3 })
                                    dText(
                                        {
                                            String.format(
                                                java.util.Locale.US, "%.2f",
                                                DepthGeom.RANGE_PCT / DepthGeom.zoomClamp(depthZoom.value)
                                            ) + "%"
                                        },
                                        8.5f, { D_TEXT2 }
                                    )
                                }
                                dRow(gap = 4f, height = 16f) {
                                    dIconButton(size = 16f, onClick = {
                                        depthZoom.value = DepthGeom.zoomClamp(depthZoom.value * 0.8)
                                    }) { canvas, w, h ->
                                        drawTextCentered(canvas, "−", w / 2f, h / 2f, 10f, D_TEXT2, 600)
                                    }
                                    dText(
                                        { "${String.format(java.util.Locale.US, "%.1f", DepthGeom.zoomClamp(depthZoom.value))}×" },
                                        8.5f, { D_TEXT3 }, width = 26f, align = TextAlign.CENTER
                                    )
                                    dIconButton(size = 16f, onClick = {
                                        depthZoom.value = DepthGeom.zoomClamp(depthZoom.value * 1.25)
                                    }) { canvas, w, h ->
                                        drawTextCentered(canvas, "+", w / 2f, h / 2f, 10f, D_TEXT2, 600)
                                    }
                                    dTabButton({ "RESET" }, { false }, height = 16f, padX = 5f) {
                                        depthZoom.value = 1.0
                                    }
                                    dTabButton({ "FULL" }, { false }, height = 16f, padX = 5f) {
                                        g.appendPop { pop ->
                                            dDepthFull(
                                                depth = { MarketData.depthOf(symbol.value, marketRev.value) },
                                                zoom = { depthZoom.value },
                                                onWheelZoom = { delta ->
                                                    depthZoom.value = DepthGeom.zoomClamp(
                                                        depthZoom.value * exp(-delta * 0.0015)
                                                    )
                                                },
                                                onClose = { g.removePop(pop) }
                                            )
                                        }
                                    }
                                }
                            }
                            depthCanvas(
                                depth = { MarketData.depthOf(symbol.value, marketRev.value) },
                                zoom = { depthZoom.value },
                                onWheelZoom = { delta ->
                                    depthZoom.value = DepthGeom.zoomClamp(
                                        depthZoom.value * exp(-delta * 0.0015)
                                    )
                                }
                            )
                        }
                    } else {
                        dTrades { MarketData.tradesOf(symbol.value) }
                    }
                }
            }

            dSplitV({ rightWidth.value }, 224f, 520f, -1f) { rightWidth.value = it; saveLayout() }

            // 右列：盘口 + 模拟下单
            dCol(gap = 0f, widthOf = { rightWidth.value }) {
                dPane(
                    "Order book",
                    "L2 aggregated",
                    grow = true,
                    tools = {
                        val menuBtn = object : ButtonBase(this), GrowChild {
                            override val directionJustify: DirectionJustify get() = DirectionJustify.center
                            override val argWidth: LayoutSize get() = LayoutSize(22f, false)
                            override val argHeight: LayoutSize get() = LayoutSize(22f, false)

                            override fun onClick() {
                                val anchor: LayoutNode = this
                                g.appendPop { pop ->
                                    dMenu(anchor = anchor, width = 200f, onClose = { g.removePop(pop) }) {
                                        dMenuLabel("Order Book Display")
                                        dMenuCheckItem({ "Display Avg. & Sum" }, { bookAvgSum.value }) {
                                            bookAvgSum.value = !bookAvgSum.value
                                        }
                                        dMenuCheckItem({ "Show Buy/Sell Ratio" }, { bookRatio.value }) {
                                            bookRatio.value = !bookRatio.value
                                        }
                                        dMenuCheckItem({ "Rounding" }, { bookRounding.value }) {
                                            bookRounding.value = !bookRounding.value
                                        }
                                        dMenuSeparator()
                                        dMenuLabel("Book Depth Visualization")
                                        dMenuItem(
                                            { "Amount" },
                                            { if (!bookCumulative.value) "✓" else "" }
                                        ) {
                                            bookCumulative.value = false
                                        }
                                        dMenuItem(
                                            { "Cumulative" },
                                            { if (bookCumulative.value) "✓" else "" }
                                        ) {
                                            bookCumulative.value = true
                                        }
                                    }
                                }
                            }

                            override fun drawContent(canvas: PlatformCanvas) {
                                if (hovered || pressed) fillOuterRoundRect(canvas, 3f, D_HOVER)
                                // ⋯ 三点
                                val cx = outerWidth / 2f
                                val cy = outerHeight / 2f
                                canvas.fillOval(cx - 7f, cy - 1.5f, 3f, 3f, D_TEXT2)
                                canvas.fillOval(cx - 1.5f, cy - 1.5f, 3f, 3f, D_TEXT2)
                                canvas.fillOval(cx + 4f, cy - 1.5f, 3f, 3f, D_TEXT2)
                            }

                            override fun drawFocusRing(canvas: PlatformCanvas) {
                                strokeOuterRing(canvas, 1f, 4f, D_ACCENT, 1.5f)
                            }
                        }
                        menuBtn
                    }
                ) {
                    dOrderbook(
                        depth = { MarketData.depthOf(symbol.value, marketRev.value) },
                        ticker = { MarketData.tickerOf(symbol.value) },
                        mode = bookMode,
                        avgSum = bookAvgSum,
                        ratio = bookRatio,
                        rounding = bookRounding,
                        cumulative = bookCumulative,
                        step = bookStep,
                        rowsPerSide = { bookRows() },
                        onPickPrice = { limitPrice.value = it }
                    )
                }
                dSplitH({ rightBottom.value }, 140f, 420f, -1f) { rightBottom.value = it; saveLayout() }
                dPane("Paper order", "Demo only", heightOf = { rightBottom.value }) {
                    dTicket(
                        ticker = { MarketData.tickerOf(symbol.value) },
                        limitPrice = limitPrice,
                        note = { note.value },
                        onNote = { note.value = it }
                    )
                }
            }
        }

        // ── footer ──
        dRow(gap = 8f, height = DASH_FOOTER_H, padX = 12f, justify = DirectionJustify.between) {
            dText(
                { "UNOFFICIAL DEMO · 模拟行情 · 非投资建议" },
                9f, { D_TEXT3 }
            )
            dRow(gap = 8f, height = 16f) {
                val n = note.value
                if (n.isNotEmpty()) {
                    dText({ n }, 9f, { D_TEXT2 })
                }
                dTabButton({ "重置布局" }, { false }, height = 16f, padX = 7f) { resetLayout() }
            }
        }
    }
}
