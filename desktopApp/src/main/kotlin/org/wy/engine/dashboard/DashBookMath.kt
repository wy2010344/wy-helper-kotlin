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
// 五、盘口数学（移植 orderbook-rows.ts）
//
// 行为对齐币安现货盘口：Price / Amount / Total 三列、步长聚合
// （卖价上取整 / 买价下取整）、深度条两侧贴右生长、B/S 比例。
// ════════════════════════════════════════════════════
internal object BookMath {
    data class Row(
        val price: Double,
        val qty: Double,
        val total: Double,
        var percent: Float,
        val ask: Boolean
    )

    data class View(
        /** 卖盘按显示顺序（远价在前）。 */
        val asks: List<Row>,
        /** 买盘最优价在前。 */
        val bids: List<Row>,
        val buyRatio: Double,
        val step: Double
    )

    data class Summary(val avg: Double, val sum: Double)

    fun summary(rows: List<Row>): Summary {
        var qty = 0.0
        var sum = 0.0
        for (r in rows) {
            qty += r.qty
            sum += r.total
        }
        return Summary(if (qty > 0) sum / qty else 0.0, sum)
    }

    private fun baseStep(price: Double): Double = when {
        !price.isFinite() || price <= 0 -> 0.01
        price >= 1 -> 0.01
        price >= 0.01 -> 0.0001
        else -> 0.000001
    }

    /** 精度档位：最小步长 ×10 递进（币安选择器同一套）。 */
    fun steps(price: Double): List<Double> {
        val base = baseStep(price)
        return listOf(1.0, 10.0, 100.0, 1000.0).map { round8(base * it) }
    }

    private fun round8(v: Double): Double {
        val text = String.format(java.util.Locale.US, "%.8f", v)
        return text.toDouble()
    }

    private fun decimalsOf(step: Double): Int {
        val text = step.toString()
        val dot = text.indexOf('.')
        return if (dot < 0) 0 else text.length - dot - 1
    }

    fun bestPrice(depth: Depth?): Double {
        if (depth == null) return 0.0
        val bid = if (depth.bids.isNotEmpty()) depth.bids[0].price else 0.0
        val ask = if (depth.asks.isNotEmpty()) depth.asks[0].price else 0.0
        return if (bid != 0.0) bid else ask
    }

    private fun aggregate(levels: List<DepthLevel>, ask: Boolean, step: Double, count: Int): List<Row> {
        val rows = ArrayList<Row>()
        if (levels.isEmpty() || count <= 0) return rows
        val decimals = decimalsOf(step)
        fun bucketOf(price: Double) = if (ask) ceil(price / step) else floor(price / step)
        var key = bucketOf(levels[0].price)
        var qty = 0.0
        var total = 0.0
        for (level in levels) {
            if (level.qty <= 0) continue
            val next = bucketOf(level.price)
            if (next != key) {
                rows.add(Row(roundTo(key * step, decimals), qty, total, 0f, ask))
                if (rows.size >= count) return rows
                key = next
                qty = 0.0
                total = 0.0
            }
            qty += level.qty
            total += level.price * level.qty
        }
        if (rows.size < count && qty > 0) {
            rows.add(Row(roundTo(key * step, decimals), qty, total, 0f, ask))
        }
        return rows
    }

    private fun roundTo(v: Double, decimals: Int): Double {
        if (decimals <= 0) return v.roundToLong().toDouble()
        val text = String.format(java.util.Locale.US, "%.${decimals}f", v)
        return text.toDouble()
    }

    fun view(depth: Depth?, count: Int, step: Double, cumulative: Boolean): View {
        if (depth == null || (depth.bids.isEmpty() && depth.asks.isEmpty())) {
            return View(emptyList(), emptyList(), 0.5, 0.01)
        }
        val options = steps(bestPrice(depth))
        val effective = if (options.contains(step)) step else options[0]
        var asks = aggregate(depth.asks, true, effective, count)
        var bids = aggregate(depth.bids, false, effective, count)

        var maxTotal = 0.0
        var bidSum = 0.0
        var askSum = 0.0
        for (r in asks) {
            if (r.total > maxTotal) maxTotal = r.total
            askSum += r.total
        }
        for (r in bids) {
            if (r.total > maxTotal) maxTotal = r.total
            bidSum += r.total
        }
        val scale = if (cumulative) max(askSum, bidSum) else maxTotal
        val denom = if (scale > 0) scale else 1.0
        applyBars(asks, denom, cumulative)
        applyBars(bids, denom, cumulative)
        val both = bidSum + askSum
        asks = asks.asReversed()
        return View(asks, bids, if (both > 0) bidSum / both else 0.5, effective)
    }

    private fun applyBars(rows: List<Row>, denom: Double, cumulative: Boolean) {
        if (!cumulative) {
            for (r in rows) {
                r.percent = min(100f, (r.total / denom * 100).toFloat())
            }
            return
        }
        // cumulative 按「最优在前」逐档累加（view 里 asks 已反转为远价在前，
        // 这里从尾部往前累，保证最优价那档最小、最深处满格）
        var running = 0.0
        for (i in rows.indices.reversed()) {
            running += rows[i].total
            rows[i].percent = min(100f, (running / denom * 100).toFloat())
        }
    }

    /** 每侧能放多少档：行高 18px，夹在 3..60。 */
    fun rowCount(sideHeight: Float): Int {
        if (!sideHeight.isFinite() || sideHeight <= 0) return 3
        return min(60, max(3, floor(sideHeight / 18f).toInt()))
    }
}
