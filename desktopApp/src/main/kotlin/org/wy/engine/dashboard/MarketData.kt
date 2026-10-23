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
internal class Rng(seed: Long) {
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
