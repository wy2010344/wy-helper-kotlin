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

internal object DepthGeom {
    const val BUCKETS = 80
    const val RANGE_PCT = 1.0
    const val MIN_ZOOM = 1.0
    const val MAX_ZOOM = 24.0

    /**
     * 一侧的累计曲线（价格升序）。
     * totals[i] = 到 prices[i] 为止的累计量（Σqty）；
     * notionalTotals[i] = 到该价位为止 Σ(price×qty)，口径对齐 ooc depth-group.ts 的
     * notionals 前缀和（逐档 price*qty 累加后再求前缀，而非 累计量×单点价格 的近似）。
     */
    data class Side(
        val prices: DoubleArray,
        val totals: DoubleArray,
        val notionalTotals: DoubleArray,
        val max: Double
    )

    data class Geo(
        val hasData: Boolean,
        val mid: Double,
        val halfSpan: Double,
        val bidMax: Double,
        val askMax: Double,
        val axisMax: Double,
        val bid: Side,
        val ask: Side
    )

    private fun niceStep(raw: Double): Double {
        if (!raw.isFinite() || raw <= 0) return 1.0
        val power = 10.0.pow(floor(log10(raw)))
        val fraction = raw / power
        val candidates = doubleArrayOf(1.0, 1.25, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 10.0)
        val nice = candidates.firstOrNull { fraction <= it } ?: 10.0
        return nice * power
    }

    fun zoomClamp(z: Double) = if (z.isFinite()) z.coerceIn(MIN_ZOOM, MAX_ZOOM) else MIN_ZOOM

    private fun emptySide() = Side(doubleArrayOf(), doubleArrayOf(), doubleArrayOf(), 0.0)

    fun group(levels: List<DepthLevel>, ask: Boolean, mid: Double, halfSpan: Double): Side {
        if (levels.isEmpty() || !halfSpan.isFinite() || halfSpan <= 0) return emptySide()
        val quantities = DoubleArray(BUCKETS)
        val notionals = DoubleArray(BUCKETS)
        val step = halfSpan / BUCKETS
        for (level in levels) {
            val offset = if (ask) level.price - mid else mid - level.price
            if (!offset.isFinite() || offset < 0 || offset > halfSpan) continue
            val index = floor(offset / step).toInt().coerceIn(0, BUCKETS - 1)
            quantities[index] += level.qty
            notionals[index] += level.price * level.qty
        }
        val n = BUCKETS + 1
        val totals = DoubleArray(n)
        val notionalTotals = DoubleArray(n)
        for (i in 0 until BUCKETS) {
            totals[i + 1] = totals[i] + quantities[i]
            notionalTotals[i + 1] = notionalTotals[i] + notionals[i]
        }
        // 输出统一为价格升序：totals[i] 原按 offset（离中间价的距离）前缀累计，
        // bid 远端 = 大累计，翻转索引使其对齐升序价格（ask 天然同向无需翻）
        val prices = DoubleArray(n)
        val totalsOut = DoubleArray(n)
        val notionalsOut = DoubleArray(n)
        for (i in 0..BUCKETS) {
            val j = if (ask) i else BUCKETS - i
            prices[i] = if (ask) mid + i * step else mid - (BUCKETS - i) * step
            totalsOut[i] = totals[j]
            notionalsOut[i] = notionalTotals[j]
        }
        return Side(prices, totalsOut, notionalsOut, totals[BUCKETS])
    }

    fun geo(depth: Depth?, zoom: Double): Geo {
        if (depth == null || (depth.bids.isEmpty() && depth.asks.isEmpty())) {
            return Geo(false, 0.0, 0.0, 0.0, 0.0, 0.0, emptySide(), emptySide())
        }
        val bestBid = depth.bids.firstOrNull()?.price
        val bestAsk = depth.asks.firstOrNull()?.price
        val mid = when {
            bestBid != null && bestAsk != null -> (bestBid + bestAsk) / 2
            bestAsk != null -> bestAsk
            else -> bestBid ?: 0.0
        }
        val halfSpan = mid * (RANGE_PCT / 100.0) / zoomClamp(zoom)
        val bid = group(depth.bids, false, mid, halfSpan)
        val ask = group(depth.asks, true, mid, halfSpan)
        val rawMax = max(bid.max, ask.max)
        val step = niceStep(max(rawMax, 1e-8) / 4)
        val axisMax = max(step, ceil(max(rawMax, 1e-8) / step) * step)
        return Geo(true, mid, halfSpan, bid.max, ask.max, axisMax, bid, ask)
    }

    data class Hover(
        val ask: Boolean,
        val price: Double,
        val total: Double,
        val notional: Double,
        val rangePct: Double,
        val x: Float,
        val y: Float
    )

    /** 光标比例 → 悬停点（只在有数据时）。 */
    fun hover(geo: Geo, ratioX: Float?, ratioY: Float?): Hover? {
        if (ratioX == null || ratioY == null) return null
        if (!geo.hasData || geo.halfSpan <= 0) return null
        if (!ratioX.isFinite() || !ratioY.isFinite()) return null
        val price = geo.mid - geo.halfSpan + ratioX.coerceIn(0f, 1f) * 2 * geo.halfSpan
        val ask = price >= geo.mid
        val side = if (ask) geo.ask else geo.bid
        var bestIndex = -1
        var bestDistance = Double.MAX_VALUE
        for (i in side.prices.indices) {
            val d = abs(side.prices[i] - price)
            if (d < bestDistance) {
                bestDistance = d
                bestIndex = i
            }
        }
        if (bestIndex < 0) return null
        val rangePct = (price - geo.mid) / geo.mid * 100.0
        val y = if (geo.axisMax > 0) (side.totals[bestIndex] / geo.axisMax).coerceIn(0.0, 1.0).toFloat() else 0f
        return Hover(
            ask, price, side.totals[bestIndex], side.notionalTotals[bestIndex],
            rangePct, ratioX, y
        )
    }

    /** 带符号的偏移百分比（绝对值自适应小数位），口径对齐 ooc formatSignedDepthRange。 */
    fun fmtSignedRangePct(pct: Double): String {
        val sign = if (pct >= 0) "+" else "-"
        val a = abs(pct)
        val dec = when {
            a >= 1 -> 2
            a >= 0.01 -> 3
            else -> 4
        }
        return sign + String.format(java.util.Locale.US, "%.${dec}f", a) + "%"
    }
}

/** 深度图画布：累计面积（买红左 / 卖绿右）+ 双轴刻度 + 悬停十字线。 */
internal fun StateHolder<Node, List<Node>>.depthCanvas(
    depth: () -> Depth?,
    zoom: () -> Double,
    onWheelZoom: ((Float) -> Unit)? = null
): RectNode {
    val hover = createSignal<DepthGeom.Hover?>(null)
    return object : RectNode(this), FlexParam, GrowChild {
        override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.y
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val alignFix: Boolean get() = true

        private val Y_AXIS_W = 46f
        private val X_AXIS_H = 14f

        private fun plotW() = outerWidth - Y_AXIS_W
        private fun plotH() = outerHeight - X_AXIS_H

        override fun cursorAt(x: Float, y: Float) = CursorType.CROSSHAIR

        override fun onPointerMove(e: PointerEvent) {
            val w = plotW()
            val h = plotH()
            if (w <= 0f || h <= 0f) return
            if (e.x < 0f || e.x > w || e.y < 0f || e.y > h) {
                hover.value = null
                return
            }
            hover.value = DepthGeom.hover(geoNow(), e.x / w, e.y / h)
        }

        override fun onPointerWheel(e: PointerEvent) {
            onWheelZoom?.invoke(e.wheelDelta)
            e.stopPropagation()
        }

        private fun geoNow() = DepthGeom.geo(depth(), zoom())

        override fun draw(canvas: PlatformCanvas) {
            val w = outerWidth
            val h = outerHeight
            val pw = w - Y_AXIS_W
            val ph = h - X_AXIS_H
            if (pw <= 0f || ph <= 0f) return
            val geo = geoNow()
            canvas.fillRect(0f, 0f, pw, ph, D_PLOT)
            if (!geo.hasData) {
                drawTextCentered(canvas, "暂无深度数据", pw / 2f, ph / 2f, 10f, D_TEXT3)
                return
            }
            val span = geo.halfSpan * 2
            fun xOf(price: Double) = ((price - (geo.mid - geo.halfSpan)) / span * pw).toFloat()
            fun yOf(total: Double) = (ph - (total / geo.axisMax) * ph).toFloat()

            // 网格 + 累计量轴（右）
            val segments = 4
            for (i in 0..segments) {
                val v = geo.axisMax / segments * i
                val y = yOf(v)
                canvas.drawLine(0f, y, pw, y, D_GRID, 1f)
                drawText(canvas, fmtQty(v), pw + 5f, y - 6f, 8.5f, D_AXIS)
            }
            // 价格轴（底）：等距对称刻度（档数随宽度自适应，72px/档 夹 3..9，
            // 口径对齐 ooc depthXTickCount + depth.ts:239-244）
            val tickCount = (pw / 72f).toInt().coerceIn(3, 9)
            for (i in 0 until tickCount) {
                val t = i.toFloat() / (tickCount - 1).coerceAtLeast(1)
                val price = geo.mid - geo.halfSpan + t.toDouble() * span
                // 首尾刻度内缩，避免居中绘制被画布边缘裁掉半截
                val x = (t * pw).coerceIn(26f, pw - 26f)
                canvas.drawLine(t * pw, 0f, t * pw, ph, D_GRID, 1f)
                drawTextCentered(canvas, fmtAxisPrice(price), x, ph + 1f, 8.5f, D_AXIS)
            }

            // 买卖累计面积（买红左 / 卖绿右），阶梯线连接：
            // bid 用 'before'（先竖后横）、ask 用 'after'（先横后竖），
            // 对齐 ooc depth.ts:154-176 的 StepMode 语义
            fun area(side: DepthGeom.Side, color: ColorInt, stepAfter: Boolean) {
                val n = side.prices.size
                if (n < 2) return
                val path = Path()
                path.moveTo(xOf(side.prices[0]), ph)
                var prevY = yOf(side.totals[0])
                path.lineTo(xOf(side.prices[0]), prevY)
                for (i in 1 until n) {
                    val x = xOf(side.prices[i])
                    val y = yOf(side.totals[i])
                    if (stepAfter) {
                        // 先横后竖：前一点的累计量保持到本价位再跳变
                        path.lineTo(x, prevY)
                        path.lineTo(x, y)
                    } else {
                        // 先竖后横：在本价位先跳变再水平延伸
                        path.lineTo(xOf(side.prices[i - 1]), y)
                        path.lineTo(x, y)
                    }
                    prevY = y
                }
                path.lineTo(xOf(side.prices[n - 1]), ph)
                path.close()
                canvas.fillPath(path, color.withAlpha(0.16f))
                canvas.strokePath(path, color.withAlpha(0.75f), 1.2f)
            }
            area(geo.bid, D_UP, stepAfter = false)
            area(geo.ask, D_DOWN, stepAfter = true)

            // 中间价竖线
            val midX = xOf(geo.mid)
            canvas.drawLine(midX, 0f, midX, ph, D_TEXT3.withAlpha(0.6f), 1f)

            // 悬停：双 tooltip 镜像（左 BID 右 ASK）+ 对称双虚线 + 带符号偏移%
            // 口径对齐 ooc depthHoverPair（depth.ts:321-339）：光标取 near 镜像点，
            // 左侧恒为 bid（near 处）、右侧恒为 ask（1-near 处）。
            val hov = hover.value
            if (hov != null) {
                val near = min(hov.x, 1f - hov.x)
                val hovBid = DepthGeom.hover(geo, near, hov.x)
                val hovAsk = DepthGeom.hover(geo, 1f - near, hov.x)

                fun dashedV(x: Float) {
                    var y = 0f
                    while (y < ph) {
                        val seg = min(4f, ph - y)
                        if (seg > 0f) canvas.drawLine(x, y, x, y + seg, D_TEXT2.withAlpha(0.55f), 1f)
                        y += 7f
                    }
                }
                if (hovBid != null && hovAsk != null) {
                    val bx = hovBid.x * pw
                    val ax = hovAsk.x * pw
                    dashedV(bx)
                    dashedV(ax)
                    // 对称遮罩：双线外侧压暗（对齐 ooc 对称遮罩语义）
                    canvas.fillRect(0f, 0f, min(bx, ax), ph, rgba(0, 0, 0, 70))
                    canvas.fillRect(max(bx, ax), 0f, pw - max(bx, ax), ph, rgba(0, 0, 0, 70))
                    // 着色按偏移方向走本地涨红跌绿（正=高于中间价=红）
                    fun drawTip(h: DepthGeom.Hover, left: Boolean) {
                        val tone = if (h.rangePct >= 0) D_UP else D_DOWN
                        val lines = listOf(
                            (if (h.ask) "ASK" else "BID") to tone,
                            fmtPrice(h.price) to D_TEXT,
                            DepthGeom.fmtSignedRangePct(h.rangePct) to tone,
                            "累计 ${fmtQty(h.total)}" to D_TEXT2,
                            "Σ ${fmtCompact(h.notional)}" to D_TEXT2
                        )
                        val lineH = 12f
                        val boxW = 104f
                        val boxH = lines.size * lineH + 8f
                        val hx = h.x * pw
                        val bx2 = if (left) {
                            (hx - boxW - 6f).takeIf { it >= 2f } ?: (hx + 6f)
                        } else {
                            (hx + 6f).takeIf { it + boxW <= pw - 2f } ?: (hx - boxW - 6f)
                        }.coerceIn(2f, (pw - boxW - 2f).coerceAtLeast(2f))
                        val by = (ph - boxH - 4f).coerceIn(2f, ph - boxH - 2f)
                        canvas.fillRoundRect(bx2, by, boxW, boxH, 4f, D_PANE_HEAD)
                        canvas.strokeRoundRect(bx2, by, boxW, boxH, 4f, D_LINE, 1f)
                        lines.forEachIndexed { i, (text, color) ->
                            drawText(
                                canvas, text, bx2 + 6f, by + 4f + i * lineH, 8.5f, color,
                                if (i == 0) 700 else 400
                            )
                        }
                    }
                    drawTip(hovBid, left = true)
                    drawTip(hovAsk, left = false)
                }
            }
        }
    }
}

/** 1 / 2 / 2.5 / 5 / 10 × 10ⁿ 步长（深度图价格轴复用）。 */
internal fun niceStepOf(raw: Double): Double {
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

// ════════════════════════════════════════════════════
// 十二、成交流（复刻 trades.ooc）
//
// 逐笔 Price / Amount / Time，按主动方向着色（买方 maker = 主动卖出 → 跌色）。
