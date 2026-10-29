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
// 金融看板（深色行情终端）
//
// 复刻自 ooc example 的 finance-dashboard（pages/finance-dashboard.ooc）：
//   三列可拖拽终端 = header（交易对 / 周期 / DEMO / 状态）
//   + 左列（自选 Watchlist + Market stats）
//   + 中列（K 线 Chart + Depth + Trades，三窗格独立拖高度）
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
//                 ｜ 中列（K 线 grow + 分拽 + Depth 定高 + 分拽 + Trades 定高）
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
/** 盘口 chrome 基础高度：工具行 + 列头 + 中间行（Avg.&Sum / 比例条随开关另计）。 */
private const val BOOK_CORE_OVERHEAD = 22f + 18f + 28f
/** Avg.&Sum 两行高度（开启时计入预算）。 */
private const val BOOK_AVG_ROWS_H = 15f * 2
/** B/S 比例条高度（开启时计入预算）。 */
private const val BOOK_RATIO_H = 16f

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

    // 看历史时冻结的行情快照：offset>0 表示用户已滚离最新端。
    // 实时段每 3 秒 append 一根，会让 takeLast(500) 窗口持续左滑、历史视图跟着右漂，
    // 拖拽因此不跟手；冻结后平移基于固定总长，与真实交易终端「看历史不自动滚」一致。
    private val frozenSym = createSignal("")
    private val frozenIv = createSignal("")
    private val frozenView = createSignal<List<Candle>?>(null)

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
    private val centerMiddle = createSignal(loadF("dash.centerMiddle", 200f, 120f, 420f))
    private val centerBottom = createSignal(loadF("dash.centerBottom", 218f, 120f, 420f))
    private val rightBottom = createSignal(loadF("dash.rightBottom", 200f, 140f, 420f))

    /** 拖拽结束后落盘（与 ooc saveLayout 同时机：不以每帧拖动写盘）。 */
    private fun saveLayout() {
        prefs.putFloat("dash.leftWidth", leftWidth.value)
        prefs.putFloat("dash.rightWidth", rightWidth.value)
        prefs.putFloat("dash.leftBottom", leftBottom.value)
        prefs.putFloat("dash.centerMiddle", centerMiddle.value)
        prefs.putFloat("dash.centerBottom", centerBottom.value)
        prefs.putFloat("dash.rightBottom", rightBottom.value)
    }

    private val g = engineGlobal

    /** 首访免责声明只自动弹一次（argChildren 会随信号重建反复执行，需本地护栏）。 */
    private var noticeAutoShown = false

    private fun columnHeight(): Float =
        windowHeight() - SHELL_TOP_BAR - SHELL_STATUS_BAR - CONTENT_PAD * 2 -
            SEG_TABS_H - CONTENT_GAP - DASH_HEADER_H - DASH_FOOTER_H

    /** 盘口 chrome 总高：基础三项 + 开启的 Avg.&Sum 两行 / 比例条（与 dOrderbook 实际结构对齐）。 */
    private fun bookOverhead(): Float =
        BOOK_CORE_OVERHEAD +
            (if (bookAvgSum.value) BOOK_AVG_ROWS_H else 0f) +
            (if (bookRatio.value) BOOK_RATIO_H else 0f)

    /**
     * 盘口每侧行数：单侧模式下吃满整块，两侧模式对半分。
     * 所有读取（窗口高 / 分拽尺寸 / 模式 / 两个开关）都发生在
     * dOrderbook 的 renderForEach memo 内，任一变化即重算行数。
     */
    private fun bookRows(): Int {
        val col = columnHeight()
        val paneH = col - rightBottom.value - SPLIT_W
        val contentH = paneH - PANE_HEAD_H
        val sideH = if (bookMode.value == "both") {
            (contentH - bookOverhead()) / 2f
        } else {
            contentH - bookOverhead()
        }
        return BookMath.rowCount(sideH)
    }

    private fun resetLayout() {
        leftWidth.value = 248f
        rightWidth.value = 316f
        leftBottom.value = 224f
        centerMiddle.value = 200f
        centerBottom.value = 218f
        rightBottom.value = 200f
        chartCount.value = ChartGeom.DEFAULT_VISIBLE
        chartOffset.value = 0
        depthZoom.value = 1.0
        // 清除持久值：下次启动回到默认布局（对齐 ooc resetLayout 删 key 语义）
        for (k in listOf(
            "dash.leftWidth", "dash.rightWidth", "dash.leftBottom",
            "dash.centerMiddle", "dash.centerBottom", "dash.rightBottom"
        )) prefs.remove(k)
        note.value = "布局已重置"
    }

    override fun draw(canvas: PlatformCanvas) {
        fillOuterRoundRect(canvas, 12f, D_BG)
        super.draw(canvas)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
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

        // ── header ──（拆至 DashHeader.kt：窄化断点由 renderForEach 现读）
        dDashHeader(
            windowWidth, symbol, interval, limitPrice,
            note, chartCount, chartOffset
        )

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
                dPane("Chart", hintOf = { interval.value }, grow = true) {
                dChartPane(
                    // 实时段每 10 个心跳滚一根新 K 线（末根随心跳走，rev=0 为静态快照）。
                    // 钉在最新(offset==0)时实时跟随；滚离最新(offset>0)时冻结当前快照，
                    // 实时增长不再顶入窗口，左右拖拽才跟手。
                    klines = {
                        val sym = symbol.value
                        val iv = interval.value
                        if (sym != frozenSym.value || iv != frozenIv.value) {
                            frozenSym.value = sym
                            frozenIv.value = iv
                            frozenView.value = null
                        }
                        if (chartOffset.value == 0) {
                            frozenView.value = null
                            MarketData.liveKlines(sym, iv, marketRev.value)
                        } else {
                            frozenView.value
                                ?: MarketData.liveKlines(sym, iv, marketRev.value)
                                    .also { frozenView.value = it }
                        }
                    },
                    count = chartCount,
                    offset = chartOffset
                )
                }
                dSplitH({ centerMiddle.value }, 120f, 420f, -1f) { centerMiddle.value = it; saveLayout() }
                // 深度图窗格（拆至 DashActivity.kt；对齐 ooc：Depth/Trades 各自独立不做 tab）
                dDashDepth(centerMiddle, symbol, marketRev, depthZoom)
                dSplitH({ centerBottom.value }, 120f, 420f, -1f) { centerBottom.value = it; saveLayout() }
                // 逐笔窗格：与深度是两条不同的流，各自一份高度条才不用互相将就
                dPane("Trades", "Tape", heightOf = { centerBottom.value }) {
                    dTrades { MarketData.tradesOf(symbol.value) }
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
                                    dMenu(
                                        anchor = anchor, width = 200f,
                                        // 锚点在卡片右端：菜单右缘与锚点右缘对齐（回退翻转也不改对齐轴）
                                        placement = Placement.BottomEnd,
                                        onClose = { g.removePop(pop) }
                                    ) {
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
                // 提示语走 renderForEach：结构 if 读 note 是启动快照，重置后不出现
                renderForEach({ callback ->
                    val n = note.value
                    if (n.isNotEmpty()) callback(0, n)
                }) { _, each -> dText({ each.value }, 9f, { D_TEXT2 }) }
                dTabButton({ "重置布局" }, { false }, height = 16f, padX = 7f) { resetLayout() }
            }
        }
    }
}
