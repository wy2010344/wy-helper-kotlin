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
// 四、K 线几何（移植 chart-view.ts）
//
// 视口 = 「可见根数 + 距最新一根的偏移」；坐标全部用百分比，
// 绘制时乘以绘图区像素尺寸。像素只出现在两处：滚轮缩放的光标锚点、
// 拖拽平移的位移换算。
// ════════════════════════════════════════════════════
internal object ChartGeom {
    const val MIN_VISIBLE = 12
    const val MAX_VISIBLE = 360
    const val DEFAULT_VISIBLE = 90

    /** 价格区占绘图高度的比例（%），下方留给成交量柱。 */
    const val PRICE_AREA = 78f
    /** 成交量柱区的顶边（%）。 */
    const val VOLUME_TOP = 82f
    private const val MAX_PRICE_TICKS = 8
    private const val MIN_PRICE_TICKS = 2

    data class Frame(val total: Int, val count: Int, val offset: Int, val start: Int, val end: Int)

    data class Box(
        val left: Float,
        val width: Float,
        val up: Boolean,
        val bodyTop: Float,
        val bodyHeight: Float,
        val wickTop: Float,
        val wickHeight: Float,
        val volumeTop: Float,
        val volumeHeight: Float
    )

    data class PriceTick(val value: Double, val top: Float)

    /** align: 0 = start，1 = mid，2 = end。 */
    data class TimeTick(val left: Float, val text: String, val align: Int)

    data class Layout(
        val hasData: Boolean,
        val frame: Frame,
        val candles: List<Candle>,
        val boxes: List<Box>,
        val priceTicks: List<PriceTick>,
        val timeTicks: List<TimeTick>,
        val high: Double,
        val low: Double,
        val last: Double,
        val lastTop: Float,
        val showLast: Boolean,
        val spanMs: Long,
        val pinned: Boolean
    )

    /** 悬停快照：光标纵坐标只决定价格，横向吸附到 K 线中心。 */
    data class Hover(
        val candle: Candle,
        val price: Double,
        val x: Float,
        val y: Float,
        val index: Int,
        val changePct: Double?,
        val timeText: String
    )

    private fun clamp(v: Float, min: Float, max: Float) = min(max, max(min, v))
    private fun clampI(v: Int, min: Int, max: Int) = min(max, max(min, v))

    fun visibleCount(total: Int, count: Int): Int {
        val max = max(1, min(MAX_VISIBLE, if (total <= 0) MAX_VISIBLE else total))
        val min = min(MIN_VISIBLE, max)
        return clampI(count, min, max)
    }

    private fun clampOffset(total: Int, count: Int, offset: Int) =
        clampI(offset, 0, max(0, total - count))

    fun chartFrame(total: Int, count: Int, offset: Int): Frame {
        val c = visibleCount(total, count)
        val off = clampOffset(total, c, offset)
        val end = max(0, total - off)
        val start = max(0, end - c)
        return Frame(total, c, off, start, end)
    }

    /** 以锚点（0~1）为中心缩放：锚点下那根 K 线保持不动。 */
    fun zoomFrame(total: Int, count: Int, offset: Int, factor: Double, anchor: Double): Pair<Int, Int> {
        val frame = chartFrame(total, count, offset)
        val span = max(1, frame.end - frame.start)
        val ratio = if (anchor.isFinite()) clamp(anchor.toFloat(), 0f, 1f) else 0.5f
        val at = frame.start + ratio * span
        val f = if (factor.isFinite()) factor.coerceIn(0.5, 2.0) else 1.0
        val next = visibleCount(total, (frame.count * f).roundToInt())
        val nextStart = (at - ratio * next).roundToInt()
        return next to clampOffset(total, next, total - next - nextStart)
    }

    /** 平移：delta > 0 往更早的方向翻（内容向右移动）。 */
    fun panFrame(total: Int, count: Int, offset: Int, delta: Double): Pair<Int, Int> {
        val frame = chartFrame(total, count, offset)
        return frame.count to clampOffset(total, frame.count, (frame.offset + delta).roundToInt())
    }

    /** 1 / 2 / 2.5 / 5 / 10 × 10ⁿ 的「好看」步长。 */
    private fun niceStep(raw: Double): Double {
        if (!raw.isFinite() || raw <= 0) return 1.0
        val power = 10.0.pow(floor(log10(raw)))
        val fraction = raw / power
        val candidates = doubleArrayOf(1.0, 2.0, 2.5, 5.0, 10.0)
        var best = candidates[0]
        var bestDistance = Double.MAX_VALUE
        for (c in candidates) {
            val d = abs(ln(c / fraction))
            if (d < bestDistance) {
                bestDistance = d
                best = c
            }
        }
        return best * power
    }

    private fun priceTicksOf(high: Double, low: Double): List<PriceTick> {
        val span = high - low
        if (!span.isFinite() || span <= 0) return emptyList()
        val step = niceStep(span / 6)
        val first = ceil(low / step) * step
        val ticks = ArrayList<PriceTick>()
        var i = 0
        while (i < MAX_PRICE_TICKS + 2) {
            val value = first + i * step
            if (value > high) break
            // 去掉浮点尾差，刻度值才能和格式化结果一致
            val rounded = value.toPrecision12()
            ticks.add(PriceTick(rounded, ((high - rounded) / span * PRICE_AREA).toFloat()))
            i++
        }
        return if (ticks.size < MIN_PRICE_TICKS) emptyList() else ticks
    }

    private fun Double.toPrecision12(): Double {
        val text = String.format(java.util.Locale.US, "%.12g", this)
        return text.toDouble()
    }

    private fun timeTicksOf(candles: List<Candle>, spanMs: Long, widthPx: Float): List<TimeTick> {
        val n = candles.size
        if (n == 0) return emptyList()
        val want = clampI(floor(((if (widthPx > 0) widthPx else 520f) / 96f).toDouble()).toInt(), 2, 7)
        val count = min(want, n)
        val ticks = ArrayList<TimeTick>()
        var previous = ""
        for (i in 0 until count) {
            val index = if (count == 1) 0 else (i * (n - 1)) / (count - 1)
            val text = fmtAxisTime(candles[index].time, spanMs)
            if (text == previous) continue
            previous = text
            val align = if (i == 0) 0 else if (i == count - 1) 2 else 1
            ticks.add(TimeTick(((index + 0.5) / n * 100).toFloat(), text, align))
        }
        return ticks
    }

    private val emptyLayout = { frame: Frame ->
        Layout(false, frame, emptyList(), emptyList(), emptyList(), emptyList(),
            0.0, 0.0, 0.0, 0f, false, 0L, true)
    }

    fun chartLayout(all: List<Candle>?, count: Int, offset: Int, widthPx: Float): Layout {
        val frame = chartFrame(all?.size ?: 0, count, offset)
        val candles = if (all.isNullOrEmpty()) emptyList() else all.subList(frame.start, frame.end)
        if (candles.isEmpty()) return emptyLayout(frame)

        var rawHigh = -Double.MAX_VALUE
        var rawLow = Double.MAX_VALUE
        var volumeMax = 0.0
        for (c in candles) {
            if (c.high > rawHigh) rawHigh = c.high
            if (c.low < rawLow) rawLow = c.low
            if (c.volume > volumeMax) volumeMax = c.volume
        }
        // 上下各 8% 呼吸位，影线不贴边、最新价标签不压刻度
        var padding = (rawHigh - rawLow) * 0.08
        if (padding == 0.0) padding = abs(rawHigh) * 0.0006
        if (padding == 0.0) padding = 1.0
        val high = rawHigh + padding
        val low = rawLow - padding
        val span = if (high - low != 0.0) high - low else 1.0
        fun yOf(v: Double) = ((high - v) / span * PRICE_AREA).toFloat()

        val step = 100f / candles.size
        val width = max(step * 0.62f, 0.14f)
        val volumeRange = max(volumeMax, 1e-12)
        val boxes = candles.mapIndexed { index, c ->
            val openY = yOf(c.open)
            val closeY = yOf(c.close)
            val volumeHeight = (c.volume / volumeRange * (100f - VOLUME_TOP)).toFloat()
            Box(
                left = index * step + step / 2 - width / 2,
                width = width,
                up = c.close >= c.open,
                bodyTop = min(openY, closeY),
                bodyHeight = max(abs(closeY - openY), 0.55f),
                wickTop = yOf(c.high),
                wickHeight = max(yOf(c.low) - yOf(c.high), 0.55f),
                volumeTop = 100f - volumeHeight,
                volumeHeight = volumeHeight
            )
        }
        val lastCandle = candles.last()
        val newest = all!!.last()
        val last = newest.close
        val lastInView = frame.offset == 0
        val spanMs = if (candles.size > 1) lastCandle.time - candles[0].time else 0L
        val lastTop = if (lastInView) yOf(last) else yOf(lastCandle.close)
        val rawTicks = priceTicksOf(high, low)
        // 最新价标签是实心的：附近 3.5% 内的刻度让位给标签
        val ticks = rawTicks.filter { abs(it.top - lastTop) > 3.5f }

        return Layout(
            hasData = boxes.isNotEmpty(),
            frame = frame,
            candles = candles,
            boxes = boxes,
            priceTicks = ticks,
            timeTicks = timeTicksOf(candles, spanMs, widthPx),
            high = high,
            low = low,
            last = last,
            lastTop = lastTop,
            showLast = lastInView,
            spanMs = spanMs,
            pinned = frame.offset == 0
        )
    }

    fun chartHover(layout: Layout, ratioX: Float?, ratioY: Float?): Hover? {
        if (ratioX == null || ratioY == null) return null
        if (!ratioX.isFinite() || !ratioY.isFinite()) return null
        val n = layout.candles.size
        if (n == 0) return null
        val index = clampI(floor(ratioX * n).toInt(), 0, n - 1)
        val candle = layout.candles[index]
        val x = ((index + 0.5f) / n) * 100f
        val y = min(clamp(ratioY, 0f, 1f) * 100f, PRICE_AREA)
        val span = if (layout.high - layout.low != 0.0) layout.high - layout.low else 1.0
        val price = layout.high - (y / PRICE_AREA) * span
        val prev = if (index > 0) layout.candles[index - 1].close else null
        val changePct = if (prev != null && prev > 0) (candle.close - prev) / prev * 100.0 else null
        return Hover(
            candle = candle,
            price = price,
            x = x,
            y = y,
            index = index,
            changePct = changePct,
            timeText = fmtAxisTime(candle.time, layout.spanMs)
        )
    }
}
