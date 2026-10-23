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

internal const val PRICE_AXIS_W = 64f
internal const val TIME_AXIS_H = 16f

internal fun StateHolder<Node, List<Node>>.dChartPane(
    klines: () -> List<Candle>,
    count: StoreRef<Int>,
    offset: StoreRef<Int>
): RectNode {
    // 悬停快照：header（OHLC）与 canvas（十字线）共享，建在 pane 对象体上
    val hover = createSignal<ChartGeom.Hover?>(null)
    return object : RectNode(this), FlexParam, GrowChild {
        override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.y
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val alignFix: Boolean get() = true
        override val gap: Float get() = 0f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            val holder = this

            // ── OHLC 工具行 ──
            dRow(gap = 10f, height = 26f, padX = 8f) {
                // 悬停时显示光标那根，否则显示最新一根（全部在 lambda 里求值，
                // 避免每次悬停移动都触发 header 子树重建）
                fun currentCandle(): Candle? = hover.value?.candle ?: klines().lastOrNull()
                fun ohlc(label: String, pick: () -> Double?) {
                    dRow(gap = 4f, height = 14f) {
                        dText({ label }, 9f, { D_TEXT3 }, 600)
                        dText({ fmtPrice(pick()) }, 10f, { D_TEXT2 })
                    }
                }
                ohlc("O") { currentCandle()?.open }
                ohlc("H") { currentCandle()?.high }
                ohlc("L") { currentCandle()?.low }
                ohlc("C") { currentCandle()?.close }
                dText(
                    {
                        val c = currentCandle()
                        val pct = if (c != null && c.open > 0) (c.close - c.open) / c.open * 100.0 else 0.0
                        fmtPercent(pct)
                    }, 10f,
                    {
                        val c = currentCandle()
                        val pct = if (c != null && c.open > 0) (c.close - c.open) / c.open * 100.0 else 0.0
                        if (pct >= 0) D_UP else D_DOWN
                    }, 600
                )
                dSpacer()
                // 缩放控件：以中线为锚
                fun zoomBy(factor: Double) {
                    val (c2, o2) = ChartGeom.zoomFrame(
                        klines().size, count.value, offset.value, factor, 0.5
                    )
                    count.value = c2
                    offset.value = o2
                }
                dIconButton(size = 18f, onClick = { zoomBy(0.8) }) { canvas, w, h ->
                    drawTextCentered(canvas, "−", w / 2f, h / 2f, 12f, D_TEXT2, 600)
                }
                dText({ "${count.value}" }, 9f, { D_TEXT3 }, width = 26f, align = TextAlign.CENTER)
                dIconButton(size = 18f, onClick = { zoomBy(1.25) }) { canvas, w, h ->
                    drawTextCentered(canvas, "+", w / 2f, h / 2f, 12f, D_TEXT2, 600)
                }
                dIconButton(size = 18f, onClick = {
                    count.value = ChartGeom.DEFAULT_VISIBLE
                    offset.value = 0
                }) { canvas, w, h ->
                    drawTextCentered(canvas, "↺", w / 2f, h / 2f, 11f, D_TEXT2, 600)
                }
            }

            // ── 绘图 canvas ──
            object : RectNode(this), FlexParam, GrowChild {
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.y
                override val directionJustify: DirectionJustify get() = DirectionJustify.start
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val alignFix: Boolean get() = true
                override fun argGrow(direction: Direction): Float =
                    if (direction == Direction.y) 1f else 0f

                private fun plotW() = outerWidth - PRICE_AXIS_W
                private fun plotH() = outerHeight - TIME_AXIS_H

                private fun layoutNow(): ChartGeom.Layout =
                    ChartGeom.chartLayout(klines(), count.value, offset.value, plotW())

                override fun cursorAt(x: Float, y: Float) = CursorType.CROSSHAIR

                override fun onPointerMove(e: PointerEvent) {
                    val w = plotW()
                    val h = plotH()
                    if (w <= 0f || h <= 0f) return
                    if (e.x < 0f || e.x > w || e.y < 0f || e.y > h) {
                        hover.value = null
                        return
                    }
                    hover.value = ChartGeom.chartHover(layoutNow(), e.x / w, e.y / h)
                }

                override fun onPointerWheel(e: PointerEvent) {
                    val w = plotW()
                    val anchor = if (w > 0f) (e.x / w).coerceIn(0f, 1f).toDouble() else 0.5
                    val factor = exp(e.wheelDelta * 0.0015).coerceIn(0.5, 2.0)
                    val (c, o) = ChartGeom.zoomFrame(
                        klines().size, count.value, offset.value, factor, anchor
                    )
                    count.value = c
                    offset.value = o
                    e.stopPropagation()
                }

                override fun onPointerDown(e: PointerEvent) {
                    var lastX = e.rootX
                    val w = plotW()
                    holder.drag(e) { me ->
                        val dx = me.rootX - lastX
                        lastX = me.rootX
                        if (dx == 0f) return@drag
                        val total = klines().size
                        val frame = ChartGeom.chartFrame(total, count.value, offset.value)
                        val span = max(1, frame.end - frame.start)
                        val perCandle = if (w > 0f) w / span else 12f
                        val (_, o) = ChartGeom.panFrame(
                            total, count.value, offset.value, (dx / perCandle).toDouble()
                        )
                        offset.value = o
                    }
                    e.stopPropagation()
                }

                override fun draw(canvas: PlatformCanvas) {
                    val w = outerWidth
                    val h = outerHeight
                    val pw = w - PRICE_AXIS_W
                    val ph = h - TIME_AXIS_H
                    if (pw <= 0f || ph <= 0f) return
                    canvas.fillRect(0f, 0f, pw, ph, D_PLOT)
                    val lay = layoutNow()
                    if (!lay.hasData) {
                        drawTextCentered(canvas, "暂无行情数据", pw / 2f, ph / 2f, 11f, D_TEXT3)
                        return
                    }

                    // 网格 + 价格轴（右）
                    for (tick in lay.priceTicks) {
                        val y = tick.top / 100f * ph
                        canvas.drawLine(0f, y, pw, y, D_GRID, 1f)
                        drawText(canvas, fmtAxisPrice(tick.value), pw + 6f, y - 6f, 9f, D_AXIS)
                    }
                    // 时间轴（底）：竖线 + 标签，两端贴边、中间居中
                    for (tick in lay.timeTicks) {
                        val x = tick.left / 100f * pw
                        canvas.drawLine(x, 0f, x, ph, D_GRID, 1f)
                        val ty = ph + 2f
                        when (tick.align) {
                            0 -> drawText(canvas, tick.text, 2f, ty, 9f, D_AXIS)
                            1 -> drawTextCentered(canvas, tick.text, x, ty + 6f, 9f, D_AXIS)
                            else -> drawTextEnd(canvas, tick.text, pw - 2f, ty + 6f, 9f, D_AXIS)
                        }
                    }

                    // 蜡烛 + 成交量
                    for (box in lay.boxes) {
                        val cx = box.left / 100f * pw
                        val cw = box.width / 100f * pw
                        val body = if (box.up) D_UP else D_DOWN
                        val wick = body.withAlpha(0.55f)
                        val mid = cx + cw / 2f
                        val wickTop = box.wickTop / 100f * ph
                        canvas.drawLine(
                            mid, wickTop, mid,
                            (box.wickTop + box.wickHeight) / 100f * ph, wick, 1f
                        )
                        canvas.fillRect(
                            cx, box.bodyTop / 100f * ph, cw,
                            box.bodyHeight / 100f * ph, body
                        )
                        canvas.fillRect(
                            cx, box.volumeTop / 100f * ph, cw,
                            box.volumeHeight / 100f * ph, body.withAlpha(0.22f)
                        )
                    }

                    // 最新价：虚线 + 右轴实心标签
                    if (lay.showLast) {
                        val y = lay.lastTop / 100f * ph
                        canvas.drawLine(0f, y, pw, y, D_ACCENT.withAlpha(0.55f), 1f)
                        val upTrend = lay.candles.lastOrNull()?.let { it.close >= it.open } ?: true
                        val tagColor = if (upTrend) D_UP else D_DOWN
                        canvas.fillRect(pw + 2f, y - 8f, 58f, 16f, tagColor)
                        drawTextCentered(
                            canvas, fmtPrice(lay.last), pw + 31f, y, 9f,
                            rgba(255, 255, 255), 700
                        )
                    }

                    // 十字线（悬停）：竖线 + 横线 + 价格 / 时间浮标
                    val hov = hover.value
                    if (hov != null) {
                        val hx = hov.x / 100f * pw
                        val hy = hov.y / 100f * ph
                        val lineColor = D_TEXT2.withAlpha(0.55f)
                        canvas.drawLine(hx, 0f, hx, ph, lineColor, 1f)
                        canvas.drawLine(0f, hy, pw, hy, lineColor, 1f)
                        // 价格浮标（右轴，贴光标）
                        val priceText = fmtPrice(hov.price)
                        val pw2 = textParagraph(priceText, 9f, D_TEXT).width + 8f
                        canvas.fillRect(pw + 2f, hy - 8f, pw2, 16f, D_PANE_HEAD)
                        canvas.strokeRect(pw + 2f, hy - 8f, pw2, 16f, D_LINE, 1f)
                        drawTextCentered(canvas, priceText, pw + 2f + pw2 / 2f, hy, 9f, D_TEXT, 600)
                        // 时间浮标（底部，贴光标）
                        val tw = textParagraph(hov.timeText, 9f, D_TEXT).width + 10f
                        val tx = (hx - tw / 2f).coerceIn(0f, pw - tw)
                        canvas.fillRoundRect(tx, ph + 1f, tw, 14f, 3f, D_PANE_HEAD)
                        canvas.strokeRoundRect(tx, ph + 1f, tw, 14f, 3f, D_LINE, 1f)
                        drawTextCentered(
                            canvas, hov.timeText, tx + tw / 2f, ph + 8f, 9f, D_TEXT, 600
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════
// 十一、累计深度图几何（移植 depth-group.ts / depth.ts 口径）
//
// 价格轴始终以中间价居中，买卖两侧共用同一套刻度；缩放只改变价格跨度
// （固定展示中间价 ±0.5% ÷ 缩放倍率，1..24 倍）。
// ════════════════════════════════════════════════════
