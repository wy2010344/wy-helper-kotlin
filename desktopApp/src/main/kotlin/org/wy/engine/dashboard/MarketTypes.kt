package org.wy.engine.dashboard

// ════════════════════════════════════════════════════
// 行情数据结构（对齐 ooc dashboard/market.ts 与 mock-api 的报文口径）
//
// 数据只来自两条外部源：币安公共行情 / 本机 mock-api（见 DashFeed.kt）。
// 这里不再自带模拟生成器，避免出现「顶栏价、K 线、深度中线各自一套价」。
// ════════════════════════════════════════════════════

/** 自选交易对（对齐 ooc finance-dashboard 的 watchSymbols）。 */
internal val watchSymbols = listOf(
    "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT", "DOGEUSDT"
)

/** K 线周期（对齐 ooc 顶部周期 tabs）。 */
internal val chartIntervals = listOf("1m", "5m", "15m", "1h")

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
