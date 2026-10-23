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

// 三列：Symbol（含 24h 成交额）/ Last / 24h 涨跌；点击切换交易对。
// ════════════════════════════════════════════════════
internal fun StateHolder<Node, List<Node>>.dWatchlist(
    tickers: () -> List<Ticker>,
    activeSymbol: () -> String,
    onPick: (String) -> Unit
) {
    object : SimpleScrollNode(this) {
        override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f
        override val scrollBarStyle: ScrollBarStyle get() = DARK_SCROLL_BAR
        override val contentGap: Float get() = 0f

        override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
            dRow(gap = 8f, height = 26f, padX = 10f) {
                dText({ "Symbol" }, 9f, { D_TEXT3 }, 600)
                dSpacer()
                dText({ "Last" }, 9f, { D_TEXT3 }, 600, width = 66f, align = TextAlign.END)
                dText({ "24h" }, 9f, { D_TEXT3 }, 600, width = 54f, align = TextAlign.END)
            }
            tickers().forEach { t ->
                dClickRow(
                    height = 38f,
                    active = t.symbol == activeSymbol(),
                    onClick = { onPick(t.symbol) }
                ) {
                    dCol(gap = 1f, align = AlignItem.start, grow = true) {
                        dText({ t.symbol }, 11f, { D_TEXT }, 500)
                        dText({ "Vol ${fmtCompact(t.quoteVolume)}" }, 9f, { D_TEXT3 })
                    }
                    dText({ fmtPrice(t.last) }, 11f, { D_TEXT }, width = 66f, align = TextAlign.END)
                    dText(
                        { fmtPercent(t.changePercent) }, 10f,
                        { if (t.changePercent >= 0) D_UP else D_DOWN },
                        width = 54f, align = TextAlign.END
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════
// 九、交易对概览（复刻 stats.ooc）
//
// 24h Change 大字 + 区间位置渐变条（低 → 高），下接七行明细。
// ════════════════════════════════════════════════════
internal fun StateHolder<Node, List<Node>>.dStats(ticker: () -> Ticker?) {
    object : SimpleScrollNode(this) {
        override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f
        override val scrollBarStyle: ScrollBarStyle get() = DARK_SCROLL_BAR
        override val contentGap: Float get() = 0f

        override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
            // 24h Change 块
            dCol(gap = 7f, padX = 10f, height = 62f) {
                dRow(gap = 8f, height = 18f) {
                    dText({ "24h Change" }, 9.5f, { D_TEXT3 }, 600)
                    dSpacer()
                    val t = ticker()
                    dText(
                        { fmtPercent(t?.changePercent) }, 13f,
                        { if ((t?.changePercent ?: 0.0) >= 0) D_UP else D_DOWN }, 600
                    )
                }
                // 区间位置条：绿 → 琥珀 → 红 渐变轨 + 当前位置圆点
                object : RectNode(this), FlexParam {
                    override val layout: LayoutDirection = FlexObject(this)
                    override val direction: Direction get() = Direction.x
                    override val alignItem: AlignItem get() = AlignItem.center
                    override val alignFix: Boolean get() = true
                    override val argHeight: LayoutSize get() = LayoutSize(4f, false)

                    override fun draw(canvas: PlatformCanvas) {
                        val t = ticker()
                        val pct = if (t != null && t.high > t.low) {
                            ((t.last - t.low) / (t.high - t.low)).toFloat().coerceIn(0f, 1f)
                        } else 0.5f
                        canvas.fillRoundRect(
                            0f, 0f, outerWidth, outerHeight, 2f, rgba(0, 0, 0),
                            LinearGradient(
                                0f, 0f, outerWidth, 0f,
                                listOf(D_DOWN, D_AMBER, D_UP)
                            )
                        )
                        canvas.fillOval(outerWidth * pct - 3.5f, -2f, 7f, 8f, D_TEXT)
                    }
                }
                dRow(height = 12f) {
                    val t = ticker()
                    dText({ "Low" }, 9f, { D_TEXT3 })
                    dSpacer()
                    val pct = if (t != null && t.high > t.low) {
                        (t.last - t.low) / (t.high - t.low) * 100.0
                    } else 50.0
                    dText({ "${String.format(java.util.Locale.US, "%.1f", pct)}%" }, 9f, { D_TEXT2 })
                    dSpacer()
                    dText({ "High" }, 9f, { D_TEXT3 })
                }
            }
            // 七行明细
            fun statRow(label: String, value: () -> String, tone: () -> ColorInt) {
                dRow(gap = 8f, height = 25f, padX = 10f) {
                    dText({ label }, 9.5f, { D_TEXT3 }, 600)
                    dSpacer()
                    dText(value, 11f, tone)
                }
            }
            statRow("Open", { fmtPrice(ticker()?.open) }, { D_TEXT2 })
            statRow("High", { fmtPrice(ticker()?.high) }, { D_UP })
            statRow("Low", { fmtPrice(ticker()?.low) }, { D_DOWN })
            statRow("Bid", { fmtPrice(ticker()?.bid) }, { D_UP })
            statRow("Ask", { fmtPrice(ticker()?.ask) }, { D_DOWN })
            statRow("Volume", { fmtCompact(ticker()?.volume) }, { D_TEXT2 })
            statRow("Quote Vol", { fmtCompact(ticker()?.quoteVolume) }, { D_TEXT2 })
        }
    }
}

// ════════════════════════════════════════════════════
// 十、K 线窗格（复刻 chart.ooc + chart-view.ts）
//
// 单节点 canvas 直绘：蜡烛 / 影线 / 成交量 / 网格 / 价格轴 / 时间轴 /
// 最新价标签 / 十字线。交互：滚轮以光标为锚缩放、按住拖拽平移、按钮缩放。
// ════════════════════════════════════════════════════
