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

    /** 单档基准挂单量（对齐 mock-api PROFILES 的 lot）。 */
    private val lotSize = mapOf(
        "BTCUSDT" to 0.012, "ETHUSDT" to 0.35, "SOLUSDT" to 6.0,
        "BNBUSDT" to 1.6, "XRPUSDT" to 900.0, "DOGEUSDT" to 4200.0
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

    // 实时 K 线单条缓存：klines() 每帧调用，同一 rev 内复用整条游走结果
    private var liveCacheKey: Triple<String, String, Int>? = null
    private var liveCacheValue: List<Candle> = emptyList()

    // 实时盘口缓存：depthOf() 每帧调用，同一 (symbol, rev) 内复用
    private var liveDepthKey: Pair<String, Int>? = null
    private var liveDepthValue: Depth? = null

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

    /** 行情心跳间隔（与 DashPage 里 marketRev 的 300ms 节流一致）。 */
    private const val HEARTBEAT_MS = 300L

    /**
     * 带行情心跳的 K 线：[rev] = 0 时直接返回静态快照（截图逐像素可复现）。
     *
     * 收盘节奏对齐交易所：1m / 5m / 15m / 1h 各按自己的周期边界滚出新根
     * （rev 即 300ms 心跳 → 1m = 200 个心跳、5m = 1000、15m = 3000、1h = 12000）。
     * 最后一根「正在走」的未收盘 K 线每个心跳都更新 OHLC（用步内相位做插值）。
     *
     * 实时段用**确定性随机游走**生成（种子由 symbol/interval 固定；第 i 根的取数
     * 次数与可见窗口无关），因此同一 rev 每次求值完全一致。波动率、影线与成交量
     * 都与静态序列同量级，渲染出来是正常蜡烛；同时每根做 2% 的弱均值回归，
     * 长时间运行也不会漂移到与页头报价脱节。
     *
     * 旧实现用两条低频正弦叠加当价格（振幅仅 0.07%），实时段连起来是一条平滑
     * 小波浪、看不出蜡烛实体——这是「新进入数据像波浪」的根因。
     */
    fun liveKlines(symbol: String, interval: String, rev: Int): List<Candle> {
        val base = klinesOf(symbol, interval)
        if (rev <= 0 || base.isEmpty()) return base
        val key = Triple(symbol, interval, rev)
        // klines() 每帧都会调用；同一 rev 内复用，避免每帧重算整条游走
        if (liveCacheKey == key) return liveCacheValue

        val stepMs = minutesOf(interval) * 60_000L
        // 一根 K 线要走多少个心跳才收盘：1m=200、5m=1000、15m=3000、1h=12000
        val ticksPerCandle = maxOf(1, (stepMs / HEARTBEAT_MS).toInt())
        val anchor = base.last().close
        // 与静态序列同量级：单根约 0.11% 的价格波动
        val vol = anchor * 0.0011
        val avgVol = base.takeLast(60).sumOf { it.volume } / 60.0
        // 固定种子：同一根 K 线在任意 rev 下都取到同一组随机数
        val rng = Rng(
            symbol.hashCode().toLong() * -0x61c8864680b583ebL xor
                (interval.hashCode().toLong() * 0x2545F4914F6CDD1DL)
        )

        val rolled = rev / ticksPerCandle
        val phase = (rev % ticksPerCandle).toDouble() / ticksPerCandle
        // 已收盘：第 1..rolled 根；正在走的是第 rolled+1 根（phase 走完才收盘）
        val liveIndex = rolled + 1
        val from = maxOf(1, liveIndex - 499)

        val live = ArrayList<Candle>(liveIndex - from + 1)
        var price = anchor
        for (i in 1..liveIndex) {
            val open = price
            // 弱均值回归 + 高斯噪声。**先取完本根全部随机数再按窗口过滤**：
            // from 随 rev 变化，若过滤后才取数会改变后续序列，跨 rev 就不一致了。
            val delta = (anchor - open) * 0.02 + rng.gaussian() * vol
            val wickHi = rng.range(0.05, 0.7) * vol
            val wickLo = rng.range(0.05, 0.7) * vol
            val volFactor = rng.range(0.55, 1.45)
            val full = open + delta
            price = full
            if (i < from) continue

            val closed = i <= rolled
            val close = if (closed) full else open + delta * phase
            val hiW = wickHi * (if (closed) 1.0 else phase)
            val loW = wickLo * (if (closed) 1.0 else phase)
            val volume = avgVol * volFactor * (1.0 + abs(delta) / vol * 0.25) *
                (if (closed) 1.0 else phase)
            live.add(
                Candle(
                    base.last().time + i * stepMs,
                    open,
                    maxOf(open, close) + hiW,
                    minOf(open, close) - loW,
                    close,
                    volume,
                    closed
                )
            )
        }
        // 与静态序列同量级（500 根）：静态历史在前，实时段接在后面
        return (base + live).takeLast(500).also {
            liveCacheKey = key
            liveCacheValue = it
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

    /** 模拟盘口：算法逐行对齐 mock-api 的 getDepth（ooc 的 MOCK 数据源）。rev = 0 用缓存快照。 */
    fun depthOf(symbol: String, rev: Int = 0): Depth {
        if (rev == 0) return depthCache.getOrPut(symbol) { genDepth(symbol, 0) }
        // 每帧都会求值，同一 rev 内复用（5000 档 × 2 侧，不缓存会有明显分配压力）
        val key = symbol to rev
        if (liveDepthKey == key) return liveDepthValue!!
        return genDepth(symbol, rev).also {
            liveDepthKey = key
            liveDepthValue = it
        }
    }

    /**
     * 生成第 [rev] 版盘口：种子掺 rev（固定序列，不读时钟，逐像素仍可复现）。
     *
     * 形态对齐 mock-api（`lib/binance-mock/market.ts` 的 getDepth）：
     * - 每侧 5000 档，档距 = mid × 1% / 5000（整本铺满约 ±0.5%，与深度图 halfSpan 一致）；
     * - 单档量 = lot × (0.6 + rand × 1.6) × (1 + index × 0.02) × 侧向偏置 × 墙，
     *   即随离中间价的距离线性增厚 → 累计量近似二次曲线（这是 ooc 那边看到的下圆弧形）；
     * - 3% 概率出现 4~8 倍的「墙」，保留盘口的层次感。
     */
    private fun genDepth(symbol: String, rev: Int): Depth {
        // 0x9E3779B97F4A7C15（黄金比例常数）超出十六进制字面量上限，取其有符号等值
        val rng = Rng(symbol.hashCode().toLong() * -0x61c8864680b583ebL xor 0x5DEECE66DL xor (rev.toLong() * -7046029254386353131L))
        val t = tickerOf(symbol)
        val tick = tickOf(symbol)
        val lot = lotSize[symbol] ?: 1.0
        val mid = t.last
        val size = 5000
        val stepSize = max(tick, alignTo((mid * 0.01) / size, tick))
        val half = max(tick, mid * 0.00004)
        val bidBias = 0.85 + rng.nextDouble() * 0.45
        val askBias = 0.85 + rng.nextDouble() * 0.45
        fun side(sign: Double, bias: Double): List<DepthLevel> {
            val list = ArrayList<DepthLevel>(size)
            for (i in 0 until size) {
                val wall = if (rng.nextDouble() < 0.03) 4.0 + rng.nextDouble() * 4.0 else 1.0
                val depthFactor = 1.0 + i * 0.02
                val qty = max(lot, lot * (0.6 + rng.nextDouble() * 1.6) * depthFactor * bias * wall)
                list.add(DepthLevel(alignTo(mid + sign * (half + i * stepSize), tick), qty))
            }
            return list
        }
        // 时间随 rev 前推（每版 300ms），消费方显示的时间戳随之走动
        return Depth(side(-1.0, bidBias), side(1.0, askBias), t.time + rev * 300L)
    }

    /** 按最小变动价位对齐（对齐 mock-api 的 align：round 到 tick 的整数倍并截到 tick 的小数位）。 */
    private fun alignTo(value: Double, step: Double): Double {
        if (!value.isFinite() || step <= 0) return value
        // tick 都是 10 的整数次幂，直接按小数位放大取整，避开 String.format 的开销
        //（每版盘口 1 万档，格式化字符串会成为每 300ms 一次的卡顿）
        val digits = if (step >= 1) 0 else min(8, ceil(-log10(step)).toInt())
        val scale = 10.0.pow(digits)
        return (value * scale).roundToLong().toDouble() / scale
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
