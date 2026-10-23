package org.wy.engine

import com.wy.layout.AlignItem
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

// ── 终端配色（深色 + 中式涨红跌绿）──────────────────
private val D_BG = rgba(10, 14, 20)
private val D_PANE = rgba(17, 22, 31)
private val D_PANE_HEAD = rgba(22, 28, 39)
private val D_PLOT = rgba(13, 17, 24)
private val D_LINE = rgba(255, 255, 255, 15)
private val D_LINE_SOFT = rgba(255, 255, 255, 8)
private val D_TEXT = rgba(230, 234, 242)
private val D_TEXT2 = rgba(138, 148, 166)
private val D_TEXT3 = rgba(96, 106, 122)
private val D_UP = rgba(240, 76, 92) // 涨红（买盘同色）
private val D_DOWN = rgba(14, 203, 129) // 跌绿（卖盘同色）
private val D_ACCENT = rgba(76, 141, 255)
private val D_AMBER = rgba(245, 166, 35)
private val D_UP_BG = rgba(240, 76, 92, 30)
private val D_DOWN_BG = rgba(14, 203, 129, 30)
private val D_HOVER = rgba(255, 255, 255, 12)
private val D_ACTIVE_BG = rgba(76, 141, 255, 26)
private val D_GRID = rgba(255, 255, 255, 9)
private val D_AXIS = rgba(120, 130, 148)

/** 侧栏「看板」导航项的图标色（= 终端强调色，跨文件取用故单独导出）。 */
internal val DASH_ACCENT = D_ACCENT

// ════════════════════════════════════════════════════
// 一、数据模型（对齐 market.ts 的字段口径）
// ════════════════════════════════════════════════════
data class Candle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val closed: Boolean
)

data class Trade(
    val id: Long,
    val price: Double,
    val qty: Double,
    val time: Long,
    /** true = 买方为 maker（主动卖出成交），着色走跌色。 */
    val buyerMaker: Boolean
)

data class DepthLevel(val price: Double, val qty: Double)

data class Depth(val bids: List<DepthLevel>, val asks: List<DepthLevel>, val time: Long)

data class Ticker(
    val symbol: String,
    val last: Double,
    val change: Double,
    val changePercent: Double,
    val high: Double,
    val low: Double,
    val open: Double,
    val volume: Double,
    val quoteVolume: Double,
    val bid: Double,
    val ask: Double,
    val time: Long
)

// ════════════════════════════════════════════════════
// 二、确定性行情数据
//
// 每个符号一条 1 分钟基准序列（7200 根 = 5 天），其余周期按倍数聚合；
// 全部由「符号哈希 + 固定算法」生成，不读时钟，截图逐像素可复现。
// ════════════════════════════════════════════════════
private class Rng(seed: Long) {
    private var s: Long = if (seed == 0L) -0x61c8864680b583ebL else seed

    fun nextLong(): Long {
        var x = s
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        s = x
        return x
    }

    fun nextDouble(): Double = (nextLong() ushr 11).toDouble() / (1L shl 53).toDouble()

    /** [min, max) 均匀分布。 */
    fun range(min: Double, max: Double): Double = min + nextDouble() * (max - min)

    /** 近似正态（三次均匀分布相加），均值 0、标准差约 0.29。 */
    fun gaussian(): Double = (nextDouble() + nextDouble() + nextDouble() - 1.5) / 0.8165
}

object MarketData {
    val symbols = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT", "DOGEUSDT")

    /** 币安现货的最小变动价位（决定盘口聚合步长与刻度小数位）。 */
    private val tickSize = mapOf(
        "BTCUSDT" to 0.01, "ETHUSDT" to 0.01, "SOLUSDT" to 0.01,
        "BNBUSDT" to 0.10, "XRPUSDT" to 0.0001, "DOGEUSDT" to 0.00001
    )

    /** 锚定基准价：让最后一根收盘落在熟悉的量级。 */
    private val basePrice = mapOf(
        "BTCUSDT" to 96432.5, "ETHUSDT" to 3412.8, "SOLUSDT" to 198.42,
        "BNBUSDT" to 592.3, "XRPUSDT" to 2.1847, "DOGEUSDT" to 0.3241
    )

    val intervals = listOf("1m", "5m", "15m", "1h")

    /** 固定 epoch 锚点：2025-01-01T00:00:00Z，与 1 分钟对齐。 */
    val epoch: Long = 1_735_689_600_000L

    private const val BASE_MINUTES = 7200

    private val baseCache = HashMap<String, List<Candle>>()
    private val klinesCache = HashMap<String, List<Candle>>()
    private val tickerCache = HashMap<String, Ticker>()
    private val depthCache = HashMap<String, Depth>()
    private val tradesCache = HashMap<String, List<Trade>>()

    fun tickOf(symbol: String): Double = tickSize[symbol] ?: 0.01

    /** 1 分钟基准序列（各周期由它聚合而来）。 */
    fun baseKlines(symbol: String): List<Candle> = baseCache.getOrPut(symbol) {
        val rng = Rng(symbol.hashCode().toLong() * -0x61c8864680b583ebL)
        val base = basePrice[symbol]!!
        val vol = base * 0.0011
        // 让随机漫步的终点回到基准价附近：按剩余距离做弱回归
        var price = base * rng.range(0.93, 1.0)
        val list = ArrayList<Candle>(BASE_MINUTES)
        var t = epoch
        val trend = (base - price) / BASE_MINUTES * 0.6
        for (i in 0 until BASE_MINUTES) {
            val open = price
            var close = open + trend + rng.gaussian() * vol
            if (rng.nextDouble() < 0.035) {
                // 偶发长阳 / 长阴，图形更有看头
                close = open + (if (rng.nextDouble() < 0.5) -1.0 else 1.0) * rng.range(2.4, 5.0) * vol
            }
            val high = max(open, close) + rng.range(0.05, 0.7) * vol
            val low = min(open, close) - rng.range(0.05, 0.7) * vol
            val volume = rng.range(30.0, 220.0) * (1.0 + abs(close - open) / vol * 0.30)
            list.add(Candle(t, open, high, low, close, volume, true))
            price = close
            t += 60_000L
        }
        list
    }

    private fun minutesOf(interval: String): Int = when (interval) {
        "5m" -> 5
        "15m" -> 15
        "1h" -> 60
        else -> 1
    }

    /** 指定周期的 K 线（末尾保留 500 根，够 90 根默认视口 + 翻历史）。 */
    fun klinesOf(symbol: String, interval: String): List<Candle> =
        klinesCache.getOrPut("$symbol@$interval") {
            val base = baseKlines(symbol)
            val step = minutesOf(interval)
            if (step == 1) {
                base.takeLast(500)
            } else {
                val out = ArrayList<Candle>(base.size / step)
                var i = 0
                while (i + step <= base.size) {
                    val group = base.subList(i, i + step)
                    out.add(
                        Candle(
                            group[0].time,
                            group[0].open,
                            group.maxOf { it.high },
                            group.minOf { it.low },
                            group[step - 1].close,
                            group.sumOf { it.volume },
                            true
                        )
                    )
                    i += step
                }
                out.takeLast(500)
            }
        }

    /** 24h 概览（以最后 1440 根 1m 为窗口）。 */
    fun tickerOf(symbol: String): Ticker = tickerCache.getOrPut(symbol) {
        val win = baseKlines(symbol).takeLast(1440)
        val last = win.last().close
        val open = win.first().open
        val tick = tickOf(symbol) * 2
        Ticker(
            symbol = symbol,
            last = last,
            change = last - open,
            changePercent = (last - open) / open * 100.0,
            high = win.maxOf { it.high },
            low = win.minOf { it.low },
            open = open,
            volume = win.sumOf { it.volume },
            quoteVolume = win.sumOf { it.close * it.volume },
            bid = last - tick,
            ask = last + tick,
            time = win.last().time
        )
    }

    /** 40 档盘口：最优价附近密、偶尔来一面墙。rev = 0 用缓存快照（截图可复现）。 */
    fun depthOf(symbol: String, rev: Int = 0): Depth =
        if (rev == 0) depthCache.getOrPut(symbol) { genDepth(symbol, 0) } else genDepth(symbol, rev)

    /** 生成第 [rev] 版盘口：种子掺 rev（固定序列，不读时钟，逐像素仍可复现）。 */
    private fun genDepth(symbol: String, rev: Int): Depth {
        // 0x9E3779B97F4A7C15（黄金比例常数）超出十六进制字面量上限，取其有符号等值
        val rng = Rng(symbol.hashCode().toLong() * -0x61c8864680b583ebL xor 0x5DEECE66DL xor (rev.toLong() * -7046029254386353131L))
        val t = tickerOf(symbol)
        val step = tickOf(symbol)
        val scale = when {
            t.last >= 1000 -> 3.2
            t.last >= 100 -> 12.0
            t.last >= 1 -> 260.0
            else -> 9000.0
        }
        fun side(from: Double, sign: Double): List<DepthLevel> {
            val list = ArrayList<DepthLevel>(60)
            // 档距按中间价百分比几何递增（贴近真实交易所越远越疏的挂单分布）：
            // 60 档覆盖约 ±1.2%，保证深度图 zoom=1（±1%）内呈现完整阶梯累计形态；
            // 旧实现 40 档 tick 级档距只覆盖 ±0.05%，深度图里全量挤在贴中线的头两档。
            for (i in 0 until 60) {
                val pct = 0.00002 * 1.115.pow(i)
                val price = t.last + sign * t.last * pct * rng.range(0.85, 1.2)
                var qty = rng.range(0.25, 1.0) * scale
                if (rng.nextDouble() < 0.10) qty *= rng.range(5.0, 11.0) // 随机墙
                list.add(DepthLevel(price, qty))
            }
            return list
        }
        // 时间随 rev 前推（每版 300ms），消费方显示的时间戳随之走动
        return Depth(side(t.bid, -1.0), side(t.ask, 1.0), t.time + rev * 300L)
    }

    /** 最近 30 笔成交（时间倒序，最新在前）。 */
    fun tradesOf(symbol: String): List<Trade> = tradesCache.getOrPut(symbol) {
        val rng = Rng(symbol.hashCode().toLong() * 0x2545F4914F6CDD1DL.toLong() xor 0x1234ABCDL)
        val t = tickerOf(symbol)
        val step = tickOf(symbol)
        val list = ArrayList<Trade>(30)
        var id = 9_000_000L
        var time = t.time
        for (i in 0 until 30) {
            val drift = rng.gaussian() * step * 1.5
            val price = t.last + drift
            val qty = rng.range(0.001, 2.4) * when {
                t.last >= 1000 -> 0.8
                t.last >= 100 -> 2.0
                else -> 60.0
            }
            list.add(Trade(id--, price, qty, time, rng.nextDouble() < 0.5))
            time -= rng.range(1100.0, 9400.0).toLong()
        }
        list
    }

    fun watchTickers(): List<Ticker> = symbols.map { tickerOf(it) }
}

// ════════════════════════════════════════════════════
// 三、格式化（对齐 market.ts / orderbook-rows.ts 的口径）
// ════════════════════════════════════════════════════
private fun fmtPrice(v: Double?): String {
    if (v == null || !v.isFinite()) return "--"
    val abs = abs(v)
    val digits = when {
        abs >= 1000 -> 2
        abs >= 1 -> 2
        abs >= 0.01 -> 4
        else -> 6
    }
    return trimZeros(String.format(java.util.Locale.US, "%.${digits}f", v))
}

private fun fmtQty(v: Double?): String {
    if (v == null || !v.isFinite()) return "--"
    val abs = abs(v)
    val digits = when {
        abs >= 1000 -> 0
        abs >= 1 -> 3
        else -> 5
    }
    return trimZeros(String.format(java.util.Locale.US, "%.${digits}f", v))
}

private fun fmtCompact(v: Double?): String {
    if (v == null || !v.isFinite()) return "--"
    val abs = abs(v)
    return when {
        abs >= 1e9 -> String.format(java.util.Locale.US, "%.2fB", v / 1e9)
        abs >= 1e6 -> String.format(java.util.Locale.US, "%.2fM", v / 1e6)
        abs >= 1e3 -> String.format(java.util.Locale.US, "%.2fK", v / 1e3)
        else -> String.format(java.util.Locale.US, "%.2f", v)
    }
}

private fun fmtPercent(v: Double?): String {
    if (v == null || !v.isFinite()) return "--"
    return "${if (v >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.2f", v)}%"
}

/** 只去掉小数部分尾零，整数部分不能被吃掉。 */
private fun trimZeros(text: String): String {
    if (!text.contains('.')) return text
    return text.replace(Regex("0+$"), "").replace(Regex("\\.$"), "")
}

/** 千分位分隔。 */
private fun withThousands(text: String): String {
    val dot = text.indexOf('.')
    val head = if (dot < 0) text else text.substring(0, dot)
    val tail = if (dot < 0) "" else text.substring(dot)
    val sb = StringBuilder()
    head.forEachIndexed { i, c ->
        if (i > 0 && (head.length - i) % 3 == 0) sb.append(',')
        sb.append(c)
    }
    return sb.toString() + tail
}

/** UTC 时钟 HH:mm（不用 Date，避免时区 / 区域设置污染截图）。 */
private fun fmtClockUtc(ms: Long): String {
    val secs = (ms / 1000) % 86_400L
    val h = secs / 3600
    val m = (secs % 3600) / 60
    return "${pad2(h)}:${pad2(m)}"
}

private fun pad2(v: Long): String = if (v < 10) "0$v" else "$v"

/** 公历日期（Howard Hinnant days→civil），输入为 epoch 天数。 */
private fun civilFromDays(z0: Long): Triple<Long, Long, Long> {
    var z = z0 + 719_468
    val era = (if (z >= 0) z else z - 146_096) / 146_097
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146_096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    return Triple(if (m <= 2) y + 1 else y, m, d)
}

/** 时间轴标签：跨度越长越粗（对齐 formatAxisTime）。 */
private fun fmtAxisTime(time: Long, spanMs: Long): String {
    val clock = fmtClockUtc(time)
    if (spanMs >= 20 * 3600_000L) {
        val (_, m, d) = civilFromDays(time / 86_400_000L)
        return "${pad2(m)}-${pad2(d)} $clock"
    }
    return clock
}

/** 价格轴刻度：少一位视觉噪声，尽量塞进 64px 轴列。 */
private fun fmtAxisPrice(value: Double): String {
    if (!value.isFinite()) return "--"
    val abs = abs(value)
    val digits = when {
        abs >= 1000 -> 2
        abs >= 1 -> 2
        abs >= 0.01 -> 4
        else -> 6
    }
    return trimZeros(String.format(java.util.Locale.US, "%,.${digits}f", value))
}

/** 盘口 Total 列：大数 K/M/B 收，小值 6 位。 */
private fun fmtTotal(v: Double, exact: Boolean = false): String {
    if (!v.isFinite()) return "--"
    if (exact) return withThousands(trimZeros(String.format(java.util.Locale.US, "%.6f", v)))
    return when {
        v >= 1e9 -> String.format(java.util.Locale.US, "%.2fB", v / 1e9)
        v >= 1e6 -> String.format(java.util.Locale.US, "%.2fM", v / 1e6)
        v >= 1e3 -> String.format(java.util.Locale.US, "%.2fK", v / 1e3)
        v >= 1 -> String.format(java.util.Locale.US, "%.2f", v)
        else -> String.format(java.util.Locale.US, "%.6f", v)
    }
}

private fun fmtStep(step: Double): String =
    if (!step.isFinite() || step <= 0) "--" else step.toString()

private fun fmtShare(ratio: Double): String {
    val v = if (ratio.isFinite()) min(1.0, max(0.0, ratio)) else 0.5
    return String.format(java.util.Locale.US, "%.1f", v * 100)
}

private fun fmtClockHms(ms: Long): String {
    val secs = (ms / 1000) % 86_400L
    return "${pad2(secs / 3600)}:${pad2((secs % 3600) / 60)}:${pad2(secs % 60)}"
}

// ════════════════════════════════════════════════════
// 四、K 线几何（移植 chart-view.ts）
//
// 视口 = 「可见根数 + 距最新一根的偏移」；坐标全部用百分比，
// 绘制时乘以绘图区像素尺寸。像素只出现在两处：滚轮缩放的光标锚点、
// 拖拽平移的位移换算。
// ════════════════════════════════════════════════════
private object ChartGeom {
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

// ════════════════════════════════════════════════════
// 五、盘口数学（移植 orderbook-rows.ts）
//
// 行为对齐币安现货盘口：Price / Amount / Total 三列、步长聚合
// （卖价上取整 / 买价下取整）、深度条两侧贴右生长、B/S 比例。
// ════════════════════════════════════════════════════
private object BookMath {
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

// ════════════════════════════════════════════════════
// 六、画布文本助手
//
// K 线 / 深度图是单节点 canvas 直绘（蜡烛、影线、面积、十字线），
// 轴标签与浮标同样走 canvas：buildParagraph 按需建段、drawParagraph 落字。
// ════════════════════════════════════════════════════
private fun textParagraph(text: String, size: Float, color: ColorInt, weight: Int = 400): PlatformParagraph {
    if (text.isEmpty()) {
        return buildParagraph(
            listOf(RichTextSpan(" ", RichTextStyle(null, size, weight, false, color))),
            Float.MAX_VALUE
        )
    }
    return buildParagraph(
        listOf(RichTextSpan(text, RichTextStyle(null, size, weight, false, color))),
        Float.MAX_VALUE
    )
}

/** 在 (x, y)（左上角）绘制一行文本。 */
private fun drawText(
    canvas: PlatformCanvas,
    text: String,
    x: Float,
    y: Float,
    size: Float,
    color: ColorInt,
    weight: Int = 400
) {
    if (text.isEmpty()) return
    canvas.drawParagraph(textParagraph(text, size, color, weight), x, y)
}

/** 以 (cx, cy) 为视觉中心绘制一行文本。 */
private fun drawTextCentered(
    canvas: PlatformCanvas,
    text: String,
    cx: Float,
    cy: Float,
    size: Float,
    color: ColorInt,
    weight: Int = 400
) {
    if (text.isEmpty()) return
    val p = textParagraph(text, size, color, weight)
    canvas.drawParagraph(p, cx - p.width / 2f, cy - p.height / 2f)
}

/** 右对齐到 rightX、垂直中心 cy。 */
private fun drawTextEnd(
    canvas: PlatformCanvas,
    text: String,
    rightX: Float,
    cy: Float,
    size: Float,
    color: ColorInt,
    weight: Int = 400
) {
    if (text.isEmpty()) return
    val p = textParagraph(text, size, color, weight)
    canvas.drawParagraph(p, rightX - p.width, cy - p.height / 2f)
}

// ════════════════════════════════════════════════════
// 七、终端基础组件
// ════════════════════════════════════════════════════

/**
 * 终端文本。[wrap] = true 时宽度从父级拉伸并按宽折行（卡片 / 弹层文案）；
 * 给 [width] 时定宽单行不折行、溢出裁剪（右对齐数字列）；
 * 默认 autoWidth（宽度 = 文本宽，用于行内标签 / 徽标）。
 */
private fun StateHolder<Node, List<Node>>.dText(
    text: () -> String,
    size: Float = 11f,
    color: () -> ColorInt = { D_TEXT },
    weight: Int = 400,
    wrap: Boolean = false,
    width: Float? = null,
    align: TextAlign = TextAlign.START,
    grow: Boolean = false
): WrappedTextNode = object : WrappedTextNode(this), GrowChild {
    override val autoWidth: Boolean get() = !wrap && width == null && !grow
    override val text: String get() = text()
    override val fontSize: Float get() = size
    override val color: ColorInt get() = color()
    override val fontWeight: Int get() = weight
    override val textAlign: TextAlign get() = align
    // 定宽列 / grow 列都按单行裁剪，避免长数字折行撑高行
    override val noWrap: Boolean get() = (width != null && !wrap) || grow
    override val argWidth: LayoutSize get() = width?.let { LayoutSize(it, false) } ?: super.argWidth
    override fun argGrow(direction: Direction): Float =
        if (grow && direction == Direction.x) 1f else 0f
}

/**
 * 行容器（主轴 x）。契约与组件库页 gRow 一致：
 * 宽度默认包裹内容（justify=grow）；[grow] = true 走 grow 通道填满剩余；
 * [height] 有定值时子节点纵向拉伸（alignFix=true），无定值时包裹最高子节点。
 */
private fun StateHolder<Node, List<Node>>.dRow(
    gap: Float = 8f,
    align: AlignItem = AlignItem.center,
    justify: DirectionJustify = DirectionJustify.grow,
    height: Float? = null,
    padX: Float = 0f,
    grow: Boolean = false,
    growY: Boolean = false,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit
) {
    object : RectNode(this), FlexParam, GrowChild {
        override val debugName: String get() = "看板行"
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.x
        override val directionJustify: DirectionJustify get() = justify
        override val alignItem: AlignItem get() = align
        // growY = true 时行的高度由父级拉伸而来，交叉轴尺寸必须取父级给定值，
        // 否则 size memo 会回读正在计算的自身 size（子列 stretch 时 childSize = 行 size）构成死循环。
        override val alignFix: Boolean get() = height != null || growY
        override val gap: Float get() = gap
        override val argHeight: LayoutSize
            get() = height?.let { LayoutSize(it, false) } ?: super.argHeight

        override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
            if (direction == Direction.x) padX else 0f

        override fun argGrow(direction: Direction): Float = when (direction) {
            Direction.x -> if (grow) 1f else 0f
            Direction.y -> if (growY) 1f else 0f
        }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            content()
        }
    }
}

/**
 * 列容器（主轴 y）。宽度始终横向拉伸（alignFix=true）。
 * 高度：[height] 有定值时子节点顺序堆放（justify=start），否则包裹内容。
 *
 * ⚠️ 放进行容器（dRow）时必须传 grow = true（1fr）：否则列的宽度回读行的
 * 主轴缓存，构成重复进入死循环。
 */
private fun StateHolder<Node, List<Node>>.dCol(
    gap: Float = 0f,
    align: AlignItem = AlignItem.stretch,
    height: Float? = null,
    grow: Boolean = false,
    growY: Boolean = false,
    padX: Float = 0f,
    width: Float? = null,
    // 动态宽度通道：argChildren 只执行一次（StateHolderI.create 契约），
    // width = signal.value 是启动时刻快照；走 lambda 让 argWidth getter
    // 每次布局读取信号，被 size memo 追踪后拖拽才会重算布局。优先于 width。
    widthOf: (() -> Float)? = null,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit
) {
    object : RectNode(this), FlexParam, GrowChild {
        override val debugName: String get() = "看板列"
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.y
        override val directionJustify: DirectionJustify
            get() = if (height != null) DirectionJustify.start else DirectionJustify.grow
        override val alignItem: AlignItem get() = align
        override val alignFix: Boolean get() = true
        override val gap: Float get() = gap
        override val argHeight: LayoutSize
            get() = height?.let { LayoutSize(it, false) } ?: super.argHeight
        override val argWidth: LayoutSize
            get() = (widthOf?.invoke() ?: width)?.let { LayoutSize(it, false) } ?: super.argWidth

        override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
            if (direction == Direction.x) padX else 0f

        override fun argGrow(direction: Direction): Float = when (direction) {
            Direction.x -> if (grow) 1f else 0f
            Direction.y -> if (growY) 1f else 0f
        }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            content()
        }
    }
}

/** 弹性占位（把后续内容推到右侧）。 */
private fun StateHolder<Node, List<Node>>.dSpacer(direction: Direction = Direction.x) {
    object : RectNode(this), GrowChild {
        override fun argGrow(d: Direction): Float = if (d == direction) 1f else 0f
        override val argHeight: LayoutSize get() = LayoutSize(0f, false)
    }
}

/**
 * 看板窗格：36px 标题栏（标题 + 提示 + tools 槽）+ 裁剪内容区。
 * [height] 有定值时堆叠（justify=start）；[grow] = true 时在列里吃剩余高度。
 */
private fun StateHolder<Node, List<Node>>.dPane(
    title: String,
    hint: String = "",
    height: Float? = null,
    // 动态高度通道：argChildren 一次性执行，height = signal.value 是快照；
    // lambda 让 argHeight getter 每次布局读信号（拖拽分隔条时重算）。优先于 height。
    heightOf: (() -> Float)? = null,
    grow: Boolean = false,
    tools: (StateHolderWithNode<Node, List<Node>>.() -> Unit)? = null,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit
): RectNode = object : RectNode(this), FlexParam, GrowChild {
    override val debugName: String get() = "窗格[$title]"
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.y
    override val directionJustify: DirectionJustify get() = DirectionJustify.start
    override val alignItem: AlignItem get() = AlignItem.stretch
    override val alignFix: Boolean get() = true
    override val gap: Float get() = 0f

    override fun argGrow(direction: Direction): Float = if (grow && direction == Direction.y) 1f else 0f
    override val argHeight: LayoutSize
        get() = (heightOf?.invoke() ?: height)?.let { LayoutSize(it, false) } ?: super.argHeight

    override fun draw(canvas: PlatformCanvas) {
        fillOuterRoundRect(canvas, 6f, D_PANE)
        // 标题栏：裁到圆角内再铺浅一档底色 + 底线
        canvas.save()
        canvas.clipRRect(0f, 0f, outerWidth, outerHeight, 6f)
        canvas.fillRect(0f, 0f, outerWidth, PANE_HEAD_H, D_PANE_HEAD)
        canvas.drawLine(0f, PANE_HEAD_H, outerWidth, PANE_HEAD_H, D_LINE, 1f)
        canvas.restore()
        strokeOuterRoundRect(canvas, 6f, D_LINE, 1f)
        super.draw(canvas)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        dRow(gap = 8f, height = PANE_HEAD_H, padX = 10f) {
            dText({ title }, 10f, { D_TEXT2 }, 600)
            if (hint.isNotEmpty()) {
                dText({ hint }, 9.5f, { D_TEXT3 })
            }
            dSpacer()
            tools?.invoke(this)
        }
        // 内容区：标准 SimpleScrollNode（grow 在 y 方向）填满剩余高度。
        // 用引擎内置的 grow-grow 范式（SimpleScrollNode 内部 ScrollContent 也是 grow 子节点），
        // 父级 flex 走 grow 分支、不回读子节点尺寸，避免布局重入。
        object : SimpleScrollNode(this) {
            override fun argGrow(direction: Direction): Float =
                if (direction == Direction.y) 1f else 0f

            override val scrollBarStyle: ScrollBarStyle get() = DARK_SCROLL_BAR

            override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
                content()
            }
        }
    }
}

private const val PANE_HEAD_H = 36f

/** 竖向分隔条：左右拖拽改列宽。sign = -1 表示鼠标右移使列变窄。 */
private fun StateHolder<Node, List<Node>>.dSplitV(
    current: () -> Float,
    min: Float,
    max: Float,
    sign: Float,
    onChange: (Float) -> Unit
): RectNode {
    val holder = this
    return object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.x
        override val directionJustify: DirectionJustify get() = DirectionJustify.center
        override val alignItem: AlignItem get() = AlignItem.center
        override val alignFix: Boolean get() = true
        override val argWidth: LayoutSize get() = LayoutSize(SPLIT_W, false)

        private val g = engineGlobal
        private val hovered by memo { g.moveHitTest?.include(this) == true }

        override fun cursorAt(x: Float, y: Float) = CursorType.RESIZE_EW

        override fun onPointerDown(e: PointerEvent) {
            val startRoot = e.rootX
            val startValue = current()
            holder.drag(e) { me ->
                onChange((startValue + (me.rootX - startRoot) * sign).coerceIn(min, max))
            }
            e.stopPropagation()
        }

        override fun draw(canvas: PlatformCanvas) {
            canvas.fillRect(SPLIT_W / 2f - 0.5f, 0f, 1f, outerHeight, if (hovered) D_ACCENT else D_LINE)
            super.draw(canvas)
        }
    }
}

/** 横向分隔条：上下拖拽改窗格高。sign = -1 表示鼠标下移使窗格变矮。 */
private fun StateHolder<Node, List<Node>>.dSplitH(
    current: () -> Float,
    min: Float,
    max: Float,
    sign: Float,
    onChange: (Float) -> Unit
): RectNode {
    val holder = this
    return object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.x
        override val directionJustify: DirectionJustify get() = DirectionJustify.center
        override val alignItem: AlignItem get() = AlignItem.center
        override val alignFix: Boolean get() = true
        override val argHeight: LayoutSize get() = LayoutSize(SPLIT_W, false)

        private val g = engineGlobal
        private val hovered by memo { g.moveHitTest?.include(this) == true }

        override fun cursorAt(x: Float, y: Float) = CursorType.RESIZE_NS

        override fun onPointerDown(e: PointerEvent) {
            val startRoot = e.rootY
            val startValue = current()
            holder.drag(e) { me ->
                onChange((startValue + (me.rootY - startRoot) * sign).coerceIn(min, max))
            }
            e.stopPropagation()
        }

        override fun draw(canvas: PlatformCanvas) {
            canvas.fillRect(0f, SPLIT_W / 2f - 0.5f, outerWidth, 1f, if (hovered) D_ACCENT else D_LINE)
            super.draw(canvas)
        }
    }
}

private const val SPLIT_W = 6f

/**
 * header 小 tab（交易对 / 周期 / 活动页签）：选中态主色淡底 + 主色描边。
 * 宽度包裹内容（directionJustify=grow）。
 */
private fun StateHolder<Node, List<Node>>.dTabButton(
    label: () -> String,
    active: () -> Boolean,
    height: Float = 22f,
    padX: Float = 8f,
    chip: ColorInt? = null,
    grow: Boolean = false,
    onClick: () -> Unit
): ButtonBase = object : ButtonBase(this), GrowChild {
    override val debugName: String get() = "标签按钮"
    override val directionJustify: DirectionJustify get() = DirectionJustify.grow
    override val argHeight: LayoutSize get() = LayoutSize(height, false)

    // 文本标签按钮必须有确定（非父派生）宽度：否则被 between / grow(无 grow 子) 行回读
    // outerSize 时父级 childSize 正在计算 → 重入崩溃。按标签内容测量（与 dText autoWidth 同机制）；
    // grow 子节点则走 sizeFromParent，接收父级分配的拉伸宽度（与 dText grow 同机制）。
    private val tabTextWidth by memo {
        buildParagraph(
            listOf(RichTextSpan(label(), RichTextStyle(fontSize = 10f, fontWeight = 500))),
            Float.MAX_VALUE
        ).width
    }
    override val argWidth: LayoutSize
        get() = if (grow) sizeFromParent(Direction.x) else LayoutSize(tabTextWidth, true)

    override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
        if (direction == Direction.x) padX else 0f

    override fun argGrow(direction: Direction): Float =
        if (grow && direction == Direction.x) 1f else 0f

    override fun onClick() = onClick()

    override fun drawContent(canvas: PlatformCanvas) {
        val on = chip != null || active()
        val tone = chip ?: D_ACCENT
        if (on) {
            fillOuterRoundRect(canvas, 3f, tone.withAlpha(0.14f))
            strokeOuterRoundRect(canvas, 3f, tone.withAlpha(0.45f), 1f)
        } else if (hovered) {
            fillOuterRoundRect(canvas, 3f, D_HOVER)
            strokeOuterRoundRect(canvas, 3f, D_LINE, 1f)
        } else {
            strokeOuterRoundRect(canvas, 3f, D_LINE_SOFT, 1f)
        }
    }

    override fun drawFocusRing(canvas: PlatformCanvas) {
        strokeOuterRing(canvas, 2f, 4f, D_ACCENT, 1.5f)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        val on = chip != null || active()
        dText(label, 10f, { if (on) (chip ?: D_TEXT) else D_TEXT2 }, 500)
    }
}

/** 定尺寸小按钮（图标 / 文字皆可），悬停浅底。 */
private fun StateHolder<Node, List<Node>>.dIconButton(
    size: Float = 20f,
    radius: Float = 3f,
    onClick: () -> Unit,
    drawIcon: (PlatformCanvas, Float, Float) -> Unit
): ButtonBase = object : ButtonBase(this) {
    override val directionJustify: DirectionJustify get() = DirectionJustify.center
    override val argWidth: LayoutSize get() = LayoutSize(size, false)
    override val argHeight: LayoutSize get() = LayoutSize(size, false)

    override fun onClick() = onClick()

    override fun drawContent(canvas: PlatformCanvas) {
        if (hovered || pressed) fillOuterRoundRect(canvas, radius, D_HOVER)
        drawIcon(canvas, outerWidth, outerHeight)
    }

    override fun drawFocusRing(canvas: PlatformCanvas) {
        strokeOuterRing(canvas, 2f, radius + 1f, D_ACCENT, 1.5f)
    }
}

/**
 * 可点击行（自选 / 盘口点价）：hover 浅底、选中主色淡底。
 * 内容是行内子节点（1fr 列 + 定宽数字列）。
 */
private fun StateHolder<Node, List<Node>>.dClickRow(
    height: Float,
    active: Boolean = false,
    padX: Float = 10f,
    onClick: () -> Unit,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit
): ButtonBase = object : ButtonBase(this) {
    override val directionJustify: DirectionJustify get() = DirectionJustify.start
    override val alignItem: AlignItem get() = AlignItem.center
    override val alignFix: Boolean get() = true
    override val argHeight: LayoutSize get() = LayoutSize(height, false)

    override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
        if (direction == Direction.x) padX else 0f

    override fun onClick() = onClick()

    override fun drawContent(canvas: PlatformCanvas) {
        when {
            active -> fillOuterRect(canvas, D_ACTIVE_BG)
            pressed -> fillOuterRect(canvas, D_HOVER)
            hovered -> fillOuterRect(canvas, D_HOVER)
        }
    }

    override fun drawFocusRing(canvas: PlatformCanvas) {
        strokeOuterRing(canvas, 1f, 3f, D_ACCENT, 1.5f)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        content()
    }
}

/** 深色下拉浮层（盘口 ⋯ 菜单 / 精度选择）。 */
private fun StateHolder<Node, List<Node>>.dMenu(
    anchor: LayoutNode,
    width: Float = 190f,
    onClose: () -> Unit,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit
): DropdownBase = object : DropdownBase(this, anchor) {
    override fun onDismiss() = onClose()
    override val panelWidth: Float get() = width

    override fun StateHolderWithNode<Node, List<Node>>.panel() {
        // panelWidth 在外层（DropdownBase 对象体）先读出来：内层面板对象里
        // 没有指向外层匿名对象的标签可用，直接传标量最稳
        val pw = panelWidth
        val ox = absoluteX
        val oy = absoluteY
        object : RectNode(this), FlexParam {
            override val layout: LayoutDirection = FlexObject(this)
            override val direction: Direction get() = Direction.y
            override val directionJustify: DirectionJustify get() = DirectionJustify.grow
            override val alignItem: AlignItem get() = AlignItem.stretch
            override val alignFix: Boolean get() = true
            override val gap: Float get() = 2f
            override val argWidth: LayoutSize get() = LayoutSize(pw, false)

            override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                if (direction == Direction.x) 6f else 5f

            override fun argPosition(direction: Direction): Float {
                val a = anchor
                return when (direction) {
                    Direction.x -> a.absoluteX - ox
                    Direction.y -> a.absoluteY + a.outerHeight + 4f - oy
                }
            }

            override fun onPointerClick(e: PointerEvent) {
                e.stopPropagation()
            }

            override fun draw(canvas: PlatformCanvas) {
                // 阴影先画（被不透明底板盖住内圈），再铺底板 + 描边
                canvas.drawShadow(2f, 2f, outerWidth - 4f, outerHeight - 4f, 6f, 12f, rgba(0, 0, 0, 120))
                fillOuterRoundRect(canvas, 6f, D_PANE)
                strokeOuterRoundRect(canvas, 6f, D_LINE, 1f)
                super.draw(canvas)
            }

            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                content()
            }
        }
    }

    override fun StateHolderWithNode<Node, List<Node>>.contentChildren() = content()
}

/** 菜单普通项。 */
private fun StateHolder<Node, List<Node>>.dMenuItem(
    label: () -> String,
    trailing: () -> String = { "" },
    onClick: () -> Unit
): ButtonBase = object : ButtonBase(this) {
    override val debugName: String get() = "菜单项"
    override val directionJustify: DirectionJustify get() = DirectionJustify.start
    override val alignItem: AlignItem get() = AlignItem.center
    override val alignFix: Boolean get() = true
    override val argHeight: LayoutSize get() = LayoutSize(24f, false)

    override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
        if (direction == Direction.x) 8f else 0f

    override fun onClick() = onClick()

    override fun drawContent(canvas: PlatformCanvas) {
        if (hovered || pressed) fillOuterRoundRect(canvas, 3f, D_HOVER)
    }

    override fun drawFocusRing(canvas: PlatformCanvas) {
        strokeOuterRing(canvas, 1f, 4f, D_ACCENT, 1.5f)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        dText(label, 10.5f, { D_TEXT })
        dSpacer()
        val t = trailing()
        if (t.isNotEmpty()) dText({ t }, 10f, { D_TEXT2 })
    }
}

/** 菜单复选项（✓ 前缀）。 */
private fun StateHolder<Node, List<Node>>.dMenuCheckItem(
    label: () -> String,
    checked: () -> Boolean,
    onToggle: () -> Unit
): ButtonBase = object : ButtonBase(this) {
    override val directionJustify: DirectionJustify get() = DirectionJustify.start
    override val alignItem: AlignItem get() = AlignItem.center
    override val alignFix: Boolean get() = true
    override val argHeight: LayoutSize get() = LayoutSize(24f, false)

    override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
        if (direction == Direction.x) 8f else 0f

    override fun onClick() = onToggle()

    override fun drawContent(canvas: PlatformCanvas) {
        if (hovered || pressed) fillOuterRoundRect(canvas, 3f, D_HOVER)
    }

    override fun drawFocusRing(canvas: PlatformCanvas) {
        strokeOuterRing(canvas, 1f, 4f, D_ACCENT, 1.5f)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        dText({ if (checked()) "✓" else "" }, 10.5f, { D_ACCENT }, 700, width = 14f, align = TextAlign.CENTER)
        dText(label, 10.5f, { D_TEXT })
    }
}

/** 菜单分组标签。 */
private fun StateHolder<Node, List<Node>>.dMenuLabel(text: String) {
    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.x
        override val alignItem: AlignItem get() = AlignItem.center
        override val alignFix: Boolean get() = true
        override val argHeight: LayoutSize get() = LayoutSize(20f, false)

        override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
            if (direction == Direction.x) 8f else 0f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            dText({ text }, 9f, { D_TEXT3 }, 600)
        }
    }
}

/** 菜单分隔线。 */
private fun StateHolder<Node, List<Node>>.dMenuSeparator() {
    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.x
        override val alignItem: AlignItem get() = AlignItem.center
        override val alignFix: Boolean get() = true
        override val argHeight: LayoutSize get() = LayoutSize(7f, false)

        override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
            if (direction == Direction.x) 8f else 3f

        override fun draw(canvas: PlatformCanvas) {
            canvas.drawLine(0f, outerHeight / 2f, outerWidth, outerHeight / 2f, D_LINE, 1f)
        }
    }
}

/** 深色滚动条（自选 / 成交流 / 概览共用）：白滑块低 alpha。 */
private val DARK_SCROLL_BAR = ScrollBarStyle(
    thumb = rgba(255, 255, 255),
    idleAlpha = 0.22f,
    hoverAlpha = 0.38f,
    dragAlpha = 0.52f
)

// ════════════════════════════════════════════════════
// 八、自选列表（复刻 watchlist.ooc）
//
// 三列：Symbol（含 24h 成交额）/ Last / 24h 涨跌；点击切换交易对。
// ════════════════════════════════════════════════════
private fun StateHolder<Node, List<Node>>.dWatchlist(
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
private fun StateHolder<Node, List<Node>>.dStats(ticker: () -> Ticker?) {
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
private const val PRICE_AXIS_W = 64f
private const val TIME_AXIS_H = 16f

private fun StateHolder<Node, List<Node>>.dChartPane(
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
private object DepthGeom {
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
private fun StateHolder<Node, List<Node>>.depthCanvas(
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
private fun niceStepOf(raw: Double): Double {
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
// ════════════════════════════════════════════════════
private fun StateHolder<Node, List<Node>>.dTrades(trades: () -> List<Trade>) {
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
private const val BOOK_ROW_H = 18f

private fun StateHolder<Node, List<Node>>.dBookRow(
    row: BookMath.Row,
    rounding: Boolean,
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
        val barW = (outerWidth - 20f) * row.percent / 100f
        if (barW > 0.5f) {
            canvas.fillRect(
                outerWidth - 10f - barW, 0f, barW, outerHeight,
                if (row.ask) D_DOWN_BG else D_UP_BG
            )
        }
        if (hovered || pressed) canvas.fillRect(0f, 0f, outerWidth, outerHeight, D_HOVER)
    }

    override fun drawFocusRing(canvas: PlatformCanvas) {
        strokeOuterRing(canvas, 1f, 2f, D_ACCENT, 1.5f)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        dText({ fmtPrice(row.price) }, 10f, { if (row.ask) D_DOWN else D_UP })
        dSpacer()
        dText({ fmtQty(row.qty) }, 10f, { D_TEXT2 }, align = TextAlign.END, grow = true)
        dText(
            { fmtTotal(row.total, !rounding) }, 10f, { D_TEXT3 },
            width = 62f, align = TextAlign.END
        )
    }
}

/** 模式图标（Both / Bids only / Asks only），16×16 viewBox 缩放到按钮内。 */
private fun StateHolder<Node, List<Node>>.dModeIconButton(
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

private fun StateHolder<Node, List<Node>>.dOrderbook(
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
                            dMenu(anchor = anchor, width = 130f, onClose = { g.removePop(pop) }) {
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

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    viewOf().asks.forEach { row ->
                        dBookRow(row, rounding.value) { onPickPrice(row.price) }
                    }
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

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    viewOf().bids.forEach { row ->
                        dBookRow(row, rounding.value) { onPickPrice(row.price) }
                    }
                }
            }

            // asksBlock / bidsBlock 已在上面按文档序注册（构造即入树）

            // ── Avg.&Sum（开关关闭时整行隐藏）──
            if (avgSum.value) {
                dRow(gap = 8f, height = 15f, padX = 10f) {
                    val v = viewOf()
                    val a = BookMath.summary(v.asks)
                    dText({ "AVG ${fmtPrice(a.avg)}" }, 9f, { D_DOWN })
                    dSpacer()
                    dText({ "Σ ${fmtTotal(a.sum, !rounding.value)}" }, 9f, { D_TEXT3 })
                }
                dRow(gap = 8f, height = 15f, padX = 10f) {
                    val v = viewOf()
                    val b = BookMath.summary(v.bids)
                    dText({ "AVG ${fmtPrice(b.avg)}" }, 9f, { D_UP })
                    dSpacer()
                    dText({ "Σ ${fmtTotal(b.sum, !rounding.value)}" }, 9f, { D_TEXT3 })
                }
            }

            // ── 中间行：最新价 + 涨跌箭头 + 价差 ──
            dRow(gap = 8f, height = 28f, padX = 10f) {
                val t = ticker()
                val up = (t?.change ?: 0.0) >= 0
                dText({ fmtPrice(t?.last) }, 15f, { if (up) D_UP else D_DOWN }, 600)
                dText({ if (up) "↑" else "↓" }, 10f, { if (up) D_UP else D_DOWN })
                dSpacer()
                val d = depth()
                val ask = d?.asks?.firstOrNull()?.price ?: 0.0
                val bid = d?.bids?.firstOrNull()?.price ?: 0.0
                dText(
                    { if (ask > 0 && bid > 0) "Spread ${fmtPrice(ask - bid)}" else "Spread --" },
                    9.5f, { D_TEXT3 }
                )
            }

            // ── B/S 比例条 ──
            if (ratio.value) {
                dRow(gap = 6f, height = 16f, padX = 10f) {
                    val v = viewOf()
                    val bidShare = v.buyRatio
                    dText({ "B ${fmtShare(bidShare)}%" }, 9f, { D_UP }, 600, width = 42f)
                    // 轨道：卖色淡底；填充：买色，宽度 = 买盘占比
                    object : RectNode(this), FlexParam, GrowChild {
                        override fun argGrow(direction: Direction): Float =
                            if (direction == Direction.x) 1f else 0f
                        override val layout: LayoutDirection = FlexObject(this)
                        override val direction: Direction get() = Direction.x
                        override val alignItem: AlignItem get() = AlignItem.center
                        override val alignFix: Boolean get() = true
                        override val argHeight: LayoutSize get() = LayoutSize(3f, false)

                        override fun draw(canvas: PlatformCanvas) {
                            fillOuterRoundRect(canvas, 1.5f, D_DOWN.withAlpha(0.28f))
                            val w = (outerWidth * bidShare).toFloat().coerceIn(0f, outerWidth)
                            if (w > 0.5f) {
                                canvas.save()
                                canvas.clipRRect(0f, 0f, outerWidth, outerHeight, 1.5f)
                                canvas.fillRect(0f, 0f, w, outerHeight, D_UP.withAlpha(0.75f))
                                canvas.restore()
                            }
                        }
                    }
                    dText({ "${fmtShare(1.0 - bidShare)}% S" }, 9f, { D_DOWN }, 600, width = 44f, align = TextAlign.END)
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
private const val TICKET_BALANCE = 10000.0

private fun StateHolder<Node, List<Node>>.dTicket(
    ticker: () -> Ticker?,
    limitPrice: StoreRef<Double?>,
    note: () -> String,
    onNote: (String) -> Unit
) {
    val side = createSignal("buy")
    val amount = createSignal("0.05")

    fun priceOf(): Double = limitPrice.value ?: ticker()?.last ?: 0.0

    object : SimpleScrollNode(this) {
        override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f
        override val scrollBarStyle: ScrollBarStyle get() = DARK_SCROLL_BAR
        override val contentGap: Float get() = 8f

        override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
            // ── 买卖 tab ──
            dRow(gap = 6f, height = 28f, padX = 10f) {
                dTabButton(
                    { "买入 / Buy" },
                    { side.value == "buy" },
                    height = 26f,
                    chip = if (side.value == "buy") D_UP else null,
                    grow = true
                ) {
                    side.value = "buy"
                    onNote("")
                }
                dTabButton(
                    { "卖出 / Sell" },
                    { side.value == "sell" },
                    height = 26f,
                    chip = if (side.value == "sell") D_DOWN else null,
                    grow = true
                ) {
                    side.value = "sell"
                    onNote("")
                }
            }

            // ── 限价行 ──
            dRow(gap = 8f, height = 16f, padX = 10f, justify = DirectionJustify.between) {
                dText({ "Limit price" }, 9.5f, { D_TEXT3 }, 600)
                dRow(gap = 6f, height = 14f) {
                    dText({ fmtPrice(priceOf()) }, 11f, { D_TEXT })
                    if (limitPrice.value != null) {
                        dTabButton({ "×" }, { false }, height = 14f, padX = 4f) {
                            limitPrice.value = null
                        }
                    }
                }
            }

            // ── Amount(USDT) 行：右侧为名义金额 ──
            dRow(gap = 8f, height = 16f, padX = 10f, justify = DirectionJustify.between) {
                dText({ "Amount (USDT)" }, 9.5f, { D_TEXT3 }, 600)
                val total = priceOf() * (amount.value.toDoubleOrNull() ?: 0.0)
                dText({ if (total > 0) fmtPrice(total) else "0.00" }, 11f, { D_TEXT })
            }

            // ── 数量输入框 ──
            object : EditableTextNode(this) {
                override var text: String
                    get() = amount.value
                    set(v) {
                        amount.value = v
                        onNote("")
                    }

                override val singleLine: Boolean = true
                override val fontSize: Float = 13f
                override val textAlign: TextAlign = TextAlign.END
                override val cursorColor: ColorInt = D_ACCENT
                override val selectionColor: ColorInt = rgba(76, 141, 255, 70)
                override val placeholderColor: ColorInt = D_TEXT3
                override val argHeight: LayoutSize = LayoutSize(28f, false)

                init {
                    placeholder = "0.05"
                }

                override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                    if (direction == Direction.x) 8f else 4f

                override fun draw(canvas: PlatformCanvas) {
                    fillOuterRoundRect(canvas, 4f, rgba(0, 0, 0, 60))
                    strokeOuterRoundRect(
                        canvas, 4f,
                        if (isFocused) D_ACCENT else D_LINE_SOFT,
                        if (isFocused) 1.5f else 1f
                    )
                    super.draw(canvas)
                }
            }

            // ── 25 / 50 / 75 / MAX ──
            dRow(gap = 6f, height = 20f, padX = 10f) {
                listOf(0.25 to "25%", 0.5 to "50%", 0.75 to "75%", 1.0 to "MAX").forEach { (r, label) ->
                    dTabButton({ label }, { false }, height = 18f, padX = 0f, grow = true) {
                        val p = priceOf()
                        if (p > 0) {
                            amount.value = String.format(
                                java.util.Locale.US, "%.6f", TICKET_BALANCE * r / p
                            )
                            onNote("")
                        } else {
                            onNote("等待行情…")
                        }
                    }
                }
            }

            // ── Est. qty ──
            dRow(gap = 8f, height = 16f, padX = 10f, justify = DirectionJustify.between) {
                dText({ "Est. qty" }, 9.5f, { D_TEXT3 }, 600)
                val p = priceOf()
                val qty = if (p > 0) (amount.value.toDoubleOrNull() ?: 0.0) / p else 0.0
                dText({ fmtQty(qty) }, 11f, { D_TEXT2 })
            }

            // ── 提交 ──
            dTabButton(
                { if (side.value == "buy") "买入" else "卖出" },
                { true },
                height = 28f,
                chip = if (side.value == "buy") D_UP else D_DOWN
            ) {
                val p = priceOf()
                val qty = if (p > 0) (amount.value.toDoubleOrNull() ?: 0.0) / p else 0.0
                val verb = if (side.value == "buy") "买入" else "卖出"
                onNote("模拟$verb ${fmtQty(qty)} — 未提交交易所")
            }

            // ── 提示行 ──
            // 换行文本必须放在纵向容器里：交叉轴拉伸取宽（父级只读它的高度）。
            // 若放进横向 dRow，父级会回读它的宽度 → sizeFromParent → 布局重入崩溃。
            dCol(gap = 0f, height = 26f, padX = 10f) {
                dText(note, 9f, { D_TEXT3 }, wrap = true)
            }
        }
    }
}

// ════════════════════════════════════════════════════
// 十五、免责声明弹窗 + 深度图全屏浮层
// ════════════════════════════════════════════════════

/** 深色模态对话框（DEMO 免责声明）。 */
private fun StateHolder<Node, List<Node>>.dNoticeDialog(onClose: () -> Unit) {
    object : DialogBase(this) {
        override fun onDismiss() = onClose()
        override val panelWidth: Float get() = 440f

        override fun StateHolderWithNode<Node, List<Node>>.panel() {
            val pw = panelWidth
            object : RectNode(this), FlexParam {
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.y
                override val directionJustify: DirectionJustify get() = DirectionJustify.grow
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val alignFix: Boolean get() = true
                override val gap: Float get() = 10f
                override val argWidth: LayoutSize
                    get() = LayoutSize(pw, false)

                override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                    if (direction == Direction.x) 20f else 18f

                override fun onPointerClick(e: PointerEvent) {
                    e.stopPropagation()
                }

                override fun draw(canvas: PlatformCanvas) {
                    fillOuterRoundRect(canvas, 10f, D_PANE)
                    strokeOuterRoundRect(canvas, 10f, D_LINE, 1f)
                    canvas.drawShadow(4f, 4f, outerWidth - 8f, outerHeight - 8f, 10f, 24f, rgba(0, 0, 0, 140))
                    super.draw(canvas)
                }

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    contentChildren()
                }
            }
        }

        override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
            dRow(gap = 8f, height = 20f) {
                dText({ "演示环境 · 免责声明" }, 14f, { D_TEXT }, 700)
                dSpacer()
                dTabButton({ "DEMO" }, { false }, height = 18f, padX = 6f, chip = D_AMBER) { }
            }
            dText(
                {
                    "本页为行情终端界面复刻演示：全部行情与盘口均为本地确定性模拟数据，" +
                        "不连接任何交易所接口，不构成任何投资建议。\n" +
                        "下单窗格（Paper order）只做界面演示，任何「提交」都不会产生真实委托。"
                },
                11.5f, { D_TEXT2 }, wrap = true
            )
            dRow(gap = 8f, height = 30f, justify = DirectionJustify.end) {
                dTabButton({ "我知道了" }, { false }, height = 28f, padX = 14f, chip = D_ACCENT) {
                    onClose()
                }
            }
        }
    }
}

/** 深度图全屏浮层（FULL）：遮罩 + 居中大图画布 + CLOSE。 */
private fun StateHolder<Node, List<Node>>.dDepthFull(
    depth: () -> Depth?,
    zoom: () -> Double,
    onWheelZoom: (Float) -> Unit,
    onClose: () -> Unit
) {
    object : OverlayBase(this) {
        override val trapsFocus: Boolean get() = true
        override fun onDismiss() = onClose()

        override fun drawScrim(canvas: PlatformCanvas) {
            fillOuterRect(canvas, rgba(0, 0, 0, 150))
        }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            object : RectNode(this), FlexParam {
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.y
                override val directionJustify: DirectionJustify get() = DirectionJustify.start
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val alignFix: Boolean get() = true
                override val gap: Float get() = 0f

                // 尺寸随窗口收放（OverlayBase 已铺满窗口，读它的内尺寸安全：
                // 根节点尺寸是定值，不参与本面板的测量）
                override val argWidth: LayoutSize
                    get() = LayoutSize(min(780f, (layoutParent?.innerWidth ?: 780f) - 48f), false)
                override val argHeight: LayoutSize
                    get() = LayoutSize(min(480f, (layoutParent?.innerHeight ?: 480f) - 48f), false)

                override fun onPointerClick(e: PointerEvent) {
                    e.stopPropagation()
                }

                override fun draw(canvas: PlatformCanvas) {
                    fillOuterRoundRect(canvas, 8f, D_BG)
                    strokeOuterRoundRect(canvas, 8f, D_LINE, 1f)
                    super.draw(canvas)
                }

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    dRow(gap = 8f, height = 30f, padX = 12f, justify = DirectionJustify.between) {
                        dText({ "Depth · Cumulative" }, 11f, { D_TEXT2 }, 600)
                        dTabButton({ "CLOSE" }, { false }, height = 20f, padX = 8f) { onClose() }
                    }
                    depthCanvas(depth, zoom, onWheelZoom)
                }
            }
        }
    }
}

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
private const val SHELL_TOP_BAR = 56f
private const val SHELL_STATUS_BAR = 30f
private const val CONTENT_PAD = 16f
private const val SEG_TABS_H = 36f
private const val CONTENT_GAP = 12f
private const val DASH_HEADER_H = 52f
private const val DASH_FOOTER_H = 24f
/** 盘口固定 chrome：工具行 + 列头 + 中间行 + 比例条。 */
private const val BOOK_OVERHEAD = 22f + 18f + 28f + 16f

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
