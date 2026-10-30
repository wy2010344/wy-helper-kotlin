package org.wy.engine.dashboard

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.wy.engine.ColorInt
import org.wy.engine.prefs
import org.wy.engine.rgba
import org.wy.lib.StoreRef
import org.wy.signal.createSignal
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities

// ════════════════════════════════════════════════════
// 数据源（对齐 ooc dashboard/feed.ts + market.ts）
//
//   SIM  —— 本地确定性模拟（MarketData，零网络，截图逐像素可复现）
//   LIVE —— 币安公共行情：REST 快照 + WS 组合流
//           wss://.../stream?streams=<全部自选>@ticker/<sym>@kline_<iv>/<sym>@aggTrade/<sym>@depth@100ms
//           深度 = REST 5000 档快照 + 增量帧维护本地全簿（对齐 ooc order-book.ts）。
//           WS 连续失败降级为 REST 轮询（4s），窗口/周期切换重建会话。
//
// 所有数据读取都走本对象，消费方（header/book/chart/activity）不再直接碰 MarketData。
//
// 线程约定：网络回调在后台线程；信号（source/status/gen）只在 EDT 上写，
// 快照通过 SwingUtilities.invokeLater 投递，与渲染同线程。
// ════════════════════════════════════════════════════

internal enum class FeedSource { SIM, LIVE }

internal enum class FeedStatus { MOCK, CONNECTING, LIVE, POLLING, RECONNECTING, OFFLINE }

/** 一次实时快照（不可变）：WS/轮询线程构造，EDT 读取。 */
internal class FeedSnap(
    val symbol: String,
    val interval: String,
    val klines: List<Candle>,
    val ticker: Ticker?,
    val tickers: Map<String, Ticker>,
    val depth: Depth?,
    val trades: List<Trade>
)

/** 状态点颜色：LIVE 绿 / MOCK 蓝 / 轮询·重连 琥珀 / 连接中·离线 灰（对齐 ooc statusDot）。 */
internal fun feedStatusColor(s: FeedStatus): ColorInt = when (s) {
    FeedStatus.LIVE -> rgba(52, 211, 153)
    FeedStatus.MOCK -> rgba(56, 189, 248)
    FeedStatus.POLLING, FeedStatus.RECONNECTING -> D_AMBER
    FeedStatus.CONNECTING, FeedStatus.OFFLINE -> D_TEXT3
}

/** 状态文案（与 ooc statusLabel 同口径）。 */
internal fun feedLabel(s: FeedStatus): String = when (s) {
    FeedStatus.LIVE -> "LIVE"
    FeedStatus.MOCK -> "MOCK"
    FeedStatus.POLLING -> "POLLING"
    FeedStatus.RECONNECTING -> "RECONNECTING"
    FeedStatus.CONNECTING -> "CONNECTING"
    FeedStatus.OFFLINE -> "OFFLINE"
}

internal object DashMarket {
    /** 默认接币安：只有用户显式切到 MOCK 才持久化为 sim（旧键 dash.source 不再读取）。 */
    private const val PREF_SOURCE = "dash.source.v2"

    val source: StoreRef<FeedSource> = createSignal(
        if (prefs.get(PREF_SOURCE, "") == "sim") FeedSource.SIM else FeedSource.LIVE
    )
    val status: StoreRef<FeedStatus> = createSignal(
        if (source.value == FeedSource.LIVE) FeedStatus.CONNECTING else FeedStatus.MOCK
    )
    /** 最近一次行情错误文案（空 = 无错误）；footer 红字展示（对齐 ooc 的 error 信号）。 */
    val error: StoreRef<String> = createSignal("")

    /** 数据版本：每次实时快照落地 +1；所有取数函数都读它，从而登记依赖。 */
    private val gen: StoreRef<Int> = createSignal(0)

    private var snap: FeedSnap? = null

    /** 截图/离屏渲染会关掉网络：画面始终是确定性模拟，rev=0 逐像素可复现。 */
    private var networkAllowed = true
    private var watchSym = ""
    private var watchIv = ""
    private var session: LiveSession? = null

    /** 顶栏开关：SIM ⇄ LIVE。切回模拟时不保留实时快照，避免两套数据混用。 */
    fun toggleSource() {
        if (!networkAllowed) return
        val next = if (source.value == FeedSource.SIM) FeedSource.LIVE else FeedSource.SIM
        source.value = next
        error.value = ""
        prefs.put(PREF_SOURCE, if (next == FeedSource.LIVE) "live" else "sim")
        session?.close()
        session = null
        snap = null
        if (next == FeedSource.LIVE) {
            status.value = FeedStatus.CONNECTING
            val sym = watchSym.ifEmpty { MarketData.symbols.first() }
            val iv = watchIv.ifEmpty { MarketData.intervals.first() }
            watchSym = sym
            watchIv = iv
            startSession(sym, iv)
        } else {
            status.value = FeedStatus.MOCK
        }
        gen.value = gen.value + 1
    }

    /** 截图入口调用：禁网 + 归位到模拟，保证离屏渲染与运行时开关无关。 */
    fun disableNetwork() {
        networkAllowed = false
        session?.close()
        session = null
        snap = null
        source.value = FeedSource.SIM
        status.value = FeedStatus.MOCK
        error.value = ""
    }

    /** 页面心跳里调（幂等）：交易对/周期或数据源变化时重建实时会话。 */
    fun ensureWatch(symbol: String, interval: String) {
        watchSym = symbol
        watchIv = interval
        if (!networkAllowed || source.value != FeedSource.LIVE) return
        val s = session
        if (s != null && !s.closed && s.symbol == symbol && s.interval == interval) return
        startSession(symbol, interval)
    }

    private fun startSession(symbol: String, interval: String) {
        session?.close()
        status.value = FeedStatus.CONNECTING
        session = LiveSession(symbol, interval, ::onSnapshot, ::onStatus, ::onError).also { it.start() }
    }

    private fun onSnapshot(s: FeedSnap) {
        SwingUtilities.invokeLater {
            if (source.value == FeedSource.LIVE) {
                snap = s
                if (error.value.isNotEmpty()) error.value = ""
                gen.value = gen.value + 1
            }
        }
    }

    private fun onError(message: String) {
        SwingUtilities.invokeLater {
            if (source.value == FeedSource.LIVE && error.value != message) error.value = message
        }
    }

    private fun onStatus(s: FeedStatus) {
        SwingUtilities.invokeLater {
            if (source.value == FeedSource.LIVE) status.value = s
        }
    }

    /** 实时快照（仅 LIVE 且已有数据时非空）；同时登记数据依赖。 */
    private fun active(): FeedSnap? {
        gen.value
        return if (source.value == FeedSource.LIVE) snap else null
    }

    fun tickerOf(symbol: String): Ticker {
        val s = active()
        if (s != null) {
            s.tickers[symbol]?.let { return it }
            if (symbol == s.symbol && s.ticker != null) return s.ticker
        }
        return MarketData.tickerOf(symbol)
    }

    fun watchTickers(): List<Ticker> = MarketData.symbols.map { tickerOf(it) }

    fun klinesOf(symbol: String, interval: String, rev: Int): List<Candle> {
        val s = active()
        if (s != null && symbol == s.symbol && interval == s.interval && s.klines.isNotEmpty()) {
            return s.klines
        }
        return MarketData.liveKlines(symbol, interval, rev)
    }

    fun depthOf(symbol: String, rev: Int): Depth {
        val s = active()
        if (s != null && symbol == s.symbol && s.depth != null) return s.depth
        return MarketData.depthOf(symbol, rev)
    }

    fun tradesOf(symbol: String): List<Trade> {
        val s = active()
        if (s != null && symbol == s.symbol && s.trades.isNotEmpty()) return s.trades
        return MarketData.tradesOf(symbol)
    }
}

// ════════════════════════════════════════════════════
// 单个交易对 + 周期的一次会话
// ════════════════════════════════════════════════════

// 用币安「公开行情专用」域名（data-api / data-stream）：只读行情、无需签名，
// 且 api.binance.com / stream.binance.com:9443 在部分网络（含国内）不可达。
private const val BINANCE_REST = "https://data-api.binance.vision"
private const val BINANCE_WS = "wss://data-stream.binance.vision/stream?streams="
private const val HISTORY = 500
private const val TRADE_KEEP = 30
/** 本地全簿每侧最大档数（对齐 ooc order-book.ts 的 6000）。 */
private const val BOOK_LEVELS = 6000
/** 本地簿快照的 REST limit：币安 /depth 上限 5000/侧，深度图要 ±0.5% 的完整簿。 */
private const val BOOK_LIMIT = 5000
/** 连接稳定存活 20s 后才算健康：清零失败计数并退出轮询降级（对齐 ooc HEALTHY_LIFETIME）。 */
private const val HEALTHY_MS = 20_000L
/** 快照合并后推给界面的节奏（10Hz 帧逐条推会拖垮整页重绘；对齐 ooc DEPTH_EMIT_MS = 220）。 */
private const val PUBLISH_MS = 220L
/** WS 不可用时的 REST 轮询周期（对齐 ooc pollSnapshot 4000ms）。 */
private const val POLL_MS = 4000L

private val http: HttpClient by lazy {
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
}

/** 网络线程：轮询 / 合并推送 / 重连调度，守护线程不阻止进程退出。 */
private val feedTimer: ScheduledExecutorService by lazy {
    Executors.newScheduledThreadPool(2) { r ->
        Thread(r, "dash-feed").apply { isDaemon = true }
    }
}

private fun enc(s: String): String =
    URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20")

/**
 * 本地全簿：REST /depth 快照铺底 + @depth@100ms 增量帧修正（对齐 ooc order-book.ts）。
 *
 * 为什么不用 depth20 部分深度流：深度图要中间价 ±0.5%（BTC 约上万档）的完整簿，
 * 而部分深度流最多 20 档；改成「快照一次（5000 档/侧）+ 增量流」后，
 * REST 只在建立连接 / 重连 / 缺帧时用一次。
 * 同步算法照币安「How to manage a local order book」：快照落地前先缓冲增量，
 * 丢弃 u <= lastUpdateId 的旧帧，首帧必须满足 U <= lastUpdateId+1 <= u，否则重拉快照。
 */
private class LocalBook(private val lock: Any) {
    private val bids = ArrayList<DepthLevel>(64) // 价格降序
    private val asks = ArrayList<DepthLevel>(64) // 价格升序
    private var lastId = 0L
    private val pending = ArrayList<Diff>()

    private class Diff(val firstId: Long, val lastId: Long, val b: JsonArray?, val a: JsonArray?)

    fun ready(): Boolean = lastId > 0 && (bids.isNotEmpty() || asks.isNotEmpty())

    /** 返回 false = 中间缺帧，本地簿不可信，调用方应重新拉快照。 */
    fun apply(firstId: Long, lastId: Long, b: JsonArray?, a: JsonArray?): Boolean = synchronized(lock) {
        if (firstId <= 0L || lastId < firstId) return true
        if (this.lastId == 0L) {
            // 快照还在路上：按序缓冲，reset 时回放
            pending.add(Diff(firstId, lastId, b, a))
            if (pending.size > 4096) pending.removeAt(0)
            return true
        }
        if (lastId <= this.lastId) return true
        if (firstId > this.lastId + 1) return false
        applyOne(Diff(firstId, lastId, b, a))
        true
    }

    /** 返回 false = 缓冲帧与本快照仍有缺口，调用方应稍后重拉。 */
    fun reset(lastUpdateId: Long, bidRows: JsonArray?, askRows: JsonArray?): Boolean = synchronized(lock) {
        bids.clear()
        asks.clear()
        bids.addAll(rows(bidRows, asc = false))
        asks.addAll(rows(askRows, asc = true))
        this.lastId = lastUpdateId
        val buffered = pending.toList()
        pending.clear()
        for (d in buffered) {
            if (d.lastId <= this.lastId) continue
            if (d.firstId > this.lastId + 1) return false
            applyOne(d)
        }
        true
    }

    /** 当前全簿的只读拷贝（发布时一次性拷贝，避免界面线程读到半更新状态）。 */
    fun depth(): Depth? = synchronized(lock) {
        if (!ready()) return null
        Depth(bids.toList(), asks.toList(), System.currentTimeMillis())
    }

    private fun applyOne(d: Diff) {
        d.b?.forEach { e ->
            val a = e.asJsonArray
            upsert(bids, a[0].asDouble, a[1].asDouble, asc = false)
        }
        d.a?.forEach { e ->
            val a = e.asJsonArray
            upsert(asks, a[0].asDouble, a[1].asDouble, asc = true)
        }
        lastId = d.lastId
    }

    private fun upsert(list: MutableList<DepthLevel>, price: Double, qty: Double, asc: Boolean) {
        var lo = 0
        var hi = list.size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            val v = list[mid].price
            if (if (asc) v < price else v > price) lo = mid + 1 else hi = mid
        }
        val hit = list.getOrNull(lo)
        if (qty <= 0) {
            if (hit != null && hit.price == price) list.removeAt(lo)
            return
        }
        if (hit != null && hit.price == price) {
            list[lo] = DepthLevel(price, qty)
            return
        }
        list.add(lo, DepthLevel(price, qty))
        if (list.size > BOOK_LEVELS) list.removeAt(list.size - 1)
    }

    private fun rows(arr: JsonArray?, asc: Boolean): List<DepthLevel> {
        if (arr == null) return emptyList()
        val out = ArrayList<DepthLevel>(arr.size())
        arr.forEach { e ->
            val a = e.asJsonArray
            val p = a[0].asDouble
            val q = a[1].asDouble
            if (p.isFinite() && q > 0) out.add(DepthLevel(p, q))
        }
        out.sortWith(if (asc) compareBy { it.price } else compareByDescending { it.price })
        return out.take(BOOK_LEVELS)
    }
}

private class LiveSession(
    val symbol: String,
    val interval: String,
    private val onSnap: (FeedSnap) -> Unit,
    private val onStatus: (FeedStatus) -> Unit,
    private val onErr: (String) -> Unit
) {
    private val lock = Any()
    private val klines = ArrayList<Candle>(HISTORY + 8)
    private var ticker: Ticker? = null
    private val tickers = HashMap<String, Ticker>()
    /** 轮询兜底用的浅盘口；WS 正常时由 [book] 的全簿覆盖。 */
    private var depth: Depth? = null
    private val book = LocalBook(lock)
    /** 最新在前（与 MarketData.tradesOf 同口径）。 */
    private val trades = ArrayList<Trade>(TRADE_KEEP)
    private var dirty = false
    /** 是否已推出过首份快照：决定 REST 失败要不要透出（首屏失败要报，稳态抖动静默）。 */
    @Volatile private var published = false

    @Volatile var closed = false
        private set
    private var ws: WebSocket? = null
    @Volatile private var wsUp = false
    /** 本次连接建立时间（ms）；用于区分「稳定连接」与「连上就断」。 */
    @Volatile private var openedAt = 0L
    private var failures = 0
    /** 重连退避：连上就断时 ×1.6 累加，稳定连接过才回到 800ms（对齐 ooc openSocket）。 */
    private var retryDelay = 800L
    private var healthyTask: ScheduledFuture<*>? = null
    /** 防止 onError + onClose 对同一次断线重复计数。 */
    private var handlingDown = false
    /** 全簿快照请求节流：缺失帧时避免 10Hz 增量流把 REST 打成热循环。 */
    @Volatile private var lastBookFetchMs = 0L
    /** REST 连续失败计数：WS 同时不通时用于判定 OFFLINE（HttpClient 线程写，近似计数即可）。 */
    private val restFails = AtomicInteger(0)
    private var publishTask: ScheduledFuture<*>? = null
    private var pollTask: ScheduledFuture<*>? = null

    fun start() {
        requestBook() // 全簿快照（5000 档/侧）：深度图与盘口都吃它
        pollRest() // 首屏：历史 K 线 / 24h 行情 / 浅盘口 / 成交
        pollTask = feedTimer.scheduleAtFixedRate(
            { if (!wsUp) pollRest() }, POLL_MS, POLL_MS, TimeUnit.MILLISECONDS
        )
        publishTask = feedTimer.scheduleAtFixedRate(
            { publish() }, PUBLISH_MS, PUBLISH_MS, TimeUnit.MILLISECONDS
        )
        connect()
    }

    fun close() {
        closed = true
        publishTask?.cancel(false)
        pollTask?.cancel(false)
        healthyTask?.cancel(false)
        healthyTask = null
        ws?.abort()
        ws = null
    }

    // ── WebSocket ──

    private fun connect() {
        if (closed) return
        handlingDown = false
        val s = symbol.lowercase()
        // 组合流：全部自选币种的 ticker（自选列表实时刷新，对齐 ooc createWatchlistSession）
        // + 当前交易对的 kline / aggTrade / 全簿增量（@depth@100ms，配合 LocalBook 重建完整盘口）
        val streams = buildString {
            MarketData.symbols.forEach { append(it.lowercase()).append("@ticker/") }
            append("$s@kline_$interval/$s@aggTrade/$s@depth@100ms")
        }
        http.newWebSocketBuilder()
            .buildAsync(URI(BINANCE_WS + streams), Listener())
            .whenComplete { w, err ->
                if (closed) {
                    w?.abort()
                } else if (err != null || w == null) {
                    onDown()
                } else {
                    ws = w
                }
            }
    }

    /** WS 断开：稳定过才清零失败/退避，连上就断则持续累计并降级轮询（对齐 ooc onClose）。 */
    private fun onDown() {
        if (closed || handlingDown) return
        handlingDown = true
        wsUp = false
        healthyTask?.cancel(false)
        healthyTask = null
        val lifetime = if (openedAt > 0L) System.currentTimeMillis() - openedAt else 0L
        openedAt = 0L
        failures = if (lifetime > HEALTHY_MS) 0 else failures + 1
        retryDelay = if (lifetime > HEALTHY_MS) 800L else minOf(20_000L, (retryDelay * 8L) / 5L)
        onStatus(if (failures >= 3) FeedStatus.POLLING else FeedStatus.RECONNECTING)
        feedTimer.schedule({ connect() }, retryDelay, TimeUnit.MILLISECONDS)
    }

    private inner class Listener : WebSocket.Listener {
        private val sb = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            if (closed) {
                webSocket.abort()
                return
            }
            wsUp = true
            openedAt = System.currentTimeMillis()
            onErr("") // 连接恢复：清掉 footer 的错误提示（对齐 ooc onOpen）
            webSocket.request(1)
            // 断线期间增量流是断的，本地簿已落后，重连后必须重新对齐
            requestBook()
            // 稳定存活 20s 后才清零失败计数并退出轮询降级：避免「连上就断」时来回抖动
            healthyTask?.cancel(false)
            healthyTask = feedTimer.schedule({
                if (!closed && wsUp) {
                    failures = 0
                    retryDelay = 800L
                    onStatus(FeedStatus.LIVE)
                }
            }, HEALTHY_MS, TimeUnit.MILLISECONDS)
            // 非降级状态下立即报 LIVE；降级（polling）期间保持轮询态直到连接证明稳定
            if (failures < 3) onStatus(FeedStatus.LIVE)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            sb.append(data)
            if (last) {
                val text = sb.toString()
                sb.setLength(0)
                handle(text)
            }
            webSocket.request(1)
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            // 主动关闭（切交易对/切回模拟）时的 abort 也会触发 onError，不要误报
            if (closed) return
            onErr("行情连接异常，正在重连")
            onDown()
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            onDown()
            return null
        }
    }

    private fun handle(text: String) {
        if (closed) return
        val root = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return
        // 组合流外层 {"stream":...,"data":{...}}；单流时没有这层
        val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: root
        when (data.str("e")) {
            "24hrTicker" -> putTicker(tickerFromWs(data))
            "kline" -> data.get("k")?.takeIf { it.isJsonObject }?.asJsonObject?.let { upsert(candleFromKline(it)) }
            "aggTrade" -> addTrade(tradeFrom(data))
            "depthUpdate" -> {
                val b = data.get("b")?.takeIf { it.isJsonArray }?.asJsonArray
                val a = data.get("a")?.takeIf { it.isJsonArray }?.asJsonArray
                val applied = synchronized(lock) {
                    val ok = book.apply(data.lng("U"), data.lng("u"), b, a)
                    if (ok) dirty = true
                    ok
                }
                if (!applied) requestBook()
            }
            else -> {
                // 兜底：部分深度流（depth20 之类）只有 lastUpdateId + bids/asks，没有 e
                if (data.has("bids") && data.has("asks")) {
                    val d = depthFrom(data, System.currentTimeMillis())
                    synchronized(lock) {
                        depth = d
                        dirty = true
                    }
                }
            }
        }
    }

    // ── REST 轮询（首屏 + WS 不可用时兜底）──

    private fun pollRest() {
        if (closed) return
        val q = "symbol=" + enc(symbol)

        restGet("/api/v3/klines?$q&interval=" + enc(interval) + "&limit=$HISTORY") { body ->
            val arr = JsonParser.parseString(body).asJsonArray
            val list = arr.mapIndexed { i, e ->
                candleFromArray(e.asJsonArray, closed = i < arr.size() - 1)
            }
            synchronized(lock) {
                klines.clear()
                klines.addAll(list.takeLast(HISTORY))
                dirty = true
            }
        }

        // 轮询兜底用浅盘口（limit=50，对齐 ooc loadSnapshot）；WS 正常时由全簿覆盖
        restGet("/api/v3/depth?$q&limit=50") { body ->
            val obj = JsonParser.parseString(body).asJsonObject
            val d = depthFrom(obj, System.currentTimeMillis())
            synchronized(lock) {
                depth = d
                dirty = true
            }
        }

        restGet("/api/v3/aggTrades?$q&limit=$TRADE_KEEP") { body ->
            val list = JsonParser.parseString(body).asJsonArray
                .map { tradeFrom(it.asJsonObject) }
                .sortedByDescending { it.time }
                .take(TRADE_KEEP)
            synchronized(lock) {
                trades.clear()
                trades.addAll(list)
                dirty = true
            }
        }

        // 自选列表要所有币种的 24h 行情，一次批量拉回
        val all = MarketData.symbols.joinToString(",", "[", "]")
        restGet("/api/v3/ticker/24hr?symbols=" + enc(all)) { body ->
            val arr = JsonParser.parseString(body).asJsonArray
            synchronized(lock) {
                arr.forEach { e -> tickerFromRest(e.asJsonObject).let { tickers[it.symbol] = it } }
                dirty = true
            }
        }
    }

    /**
     * 拉全簿快照重建本地簿（建连 / 重连 / 增量缺帧）。
     * 1.5s 内不重复请求：10Hz 增量流缺帧时不会把 REST 打成热循环。
     */
    private fun requestBook() {
        if (closed) return
        val now = System.currentTimeMillis()
        if (now - lastBookFetchMs < 1500L) return
        lastBookFetchMs = now
        restGet("/api/v3/depth?symbol=" + enc(symbol) + "&limit=$BOOK_LIMIT") { body ->
            val obj = JsonParser.parseString(body).asJsonObject
            book.reset(
                obj.lng("lastUpdateId"),
                obj.get("bids")?.takeIf { it.isJsonArray }?.asJsonArray,
                obj.get("asks")?.takeIf { it.isJsonArray }?.asJsonArray
            )
            synchronized(lock) { dirty = true }
        }
    }

    /** 异步 GET：回调在 HttpClient 线程，失败交给下一轮（不覆盖现有数据）。 */
    private fun restGet(path: String, use: (String) -> Unit) {
        http.sendAsync(
            HttpRequest.newBuilder(URI(BINANCE_REST + path)).timeout(Duration.ofSeconds(10)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        ).whenComplete { res, err ->
            if (closed) return@whenComplete
            if (err != null || res == null || res.statusCode() !in 200..299) {
                // WS 也不通且 REST 连续失败：判定离线（地域封禁 / 断网）
                if (restFails.incrementAndGet() >= 2 && !wsUp) onStatus(FeedStatus.OFFLINE)
                // 首屏还没拿到行情、或已降级轮询时，把失败原因透出到 footer；
                // 稳态（WS 正常）的 REST 抖动保持静默，对齐 ooc syncBook/pollSnapshot 的 catch
                if (!published || !wsUp) {
                    val code = res?.statusCode()
                    onErr(
                        if (code != null) "Binance $code: " + path.substringBefore('?')
                        else "行情请求失败，正在重试"
                    )
                }
                return@whenComplete
            }
            if (restFails.getAndSet(0) >= 2 && !wsUp) onStatus(FeedStatus.POLLING)
            runCatching { use(res.body()) }
        }
    }

    // ── 状态合并 ──

    private fun upsert(c: Candle) = synchronized(lock) {
        val last = klines.lastOrNull()
        when {
            last == null -> klines.add(c)
            c.time > last.time -> {
                klines.add(c)
                if (klines.size > HISTORY) klines.removeAt(0)
            }
            c.time == last.time -> klines[klines.size - 1] = c
        }
        dirty = true
    }

    private fun putTicker(t: Ticker) = synchronized(lock) {
        if (t.symbol == symbol) ticker = t
        tickers[t.symbol] = t
        dirty = true
    }

    private fun addTrade(t: Trade) = synchronized(lock) {
        trades.add(0, t)
        while (trades.size > TRADE_KEEP) trades.removeAt(trades.size - 1)
        dirty = true
    }

    private fun publish() {
        if (closed) return
        val s = synchronized(lock) {
            if (!dirty) return
            dirty = false
            // WS 正常时用全簿（深度图 ±0.5% 完整）；轮询/首屏用浅盘口兜底
            val d = if (wsUp && book.ready()) book.depth() else depth
            FeedSnap(
                symbol = symbol,
                interval = interval,
                klines = klines.toList(),
                ticker = ticker,
                tickers = HashMap(tickers),
                depth = d,
                trades = trades.toList()
            )
        }
        published = true
        onSnap(s)
    }
}

// ════════════════════════════════════════════════════
// 报文映射（REST 长字段名 / WS 单字段名 两套）
// ════════════════════════════════════════════════════

private fun JsonObject.str(key: String): String? {
    val e = get(key) ?: return null
    return if (e.isJsonNull) null else e.asString
}

private fun JsonObject.dbl(key: String): Double = str(key)?.toDoubleOrNull() ?: 0.0

private fun JsonObject.lng(key: String): Long = str(key)?.toLongOrNull() ?: 0L

private fun JsonObject.bool(key: String): Boolean =
    get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false

/** REST /klines 一元数组：[openTime, open, high, low, close, volume, closeTime, ...] */
private fun candleFromArray(a: JsonArray, closed: Boolean) = Candle(
    time = a[0].asLong,
    open = a[1].asDouble,
    high = a[2].asDouble,
    low = a[3].asDouble,
    close = a[4].asDouble,
    volume = a[5].asDouble,
    closed = closed
)

/** WS @kline 的 k 对象：t/o/h/l/c/v + x（是否收盘）。 */
private fun candleFromKline(k: JsonObject) = Candle(
    time = k.lng("t"),
    open = k.dbl("o"),
    high = k.dbl("h"),
    low = k.dbl("l"),
    close = k.dbl("c"),
    volume = k.dbl("v"),
    closed = k.bool("x")
)

/** REST /ticker/24hr：长字段名。 */
private fun tickerFromRest(j: JsonObject) = Ticker(
    symbol = j.str("symbol") ?: "",
    last = j.dbl("lastPrice"),
    change = j.dbl("priceChange"),
    changePercent = j.dbl("priceChangePercent"),
    high = j.dbl("highPrice"),
    low = j.dbl("lowPrice"),
    open = j.dbl("openPrice"),
    volume = j.dbl("volume"),
    quoteVolume = j.dbl("quoteVolume"),
    bid = j.dbl("bidPrice"),
    ask = j.dbl("askPrice"),
    time = j.lng("closeTime")
)

/** WS @ticker：单字段名（c=最新价、o=开盘价、P=涨跌幅）。 */
private fun tickerFromWs(j: JsonObject): Ticker {
    val last = j.dbl("c")
    val open = j.dbl("o")
    return Ticker(
        symbol = j.str("s") ?: "",
        last = last,
        change = last - open,
        changePercent = j.dbl("P"),
        high = j.dbl("h"),
        low = j.dbl("l"),
        open = open,
        volume = j.dbl("v"),
        quoteVolume = j.dbl("q"),
        bid = j.dbl("b"),
        ask = j.dbl("B"),
        time = j.lng("E")
    )
}

/** REST /aggTrades 与 WS @aggTrade 同字段名（a/p/q/T/m）。 */
private fun tradeFrom(j: JsonObject) = Trade(
    id = j.lng("a"),
    price = j.dbl("p"),
    qty = j.dbl("q"),
    time = j.lng("T"),
    buyerMaker = j.bool("m")
)

/** depth（REST /depth 与 WS depth20 同结构）：bids/asks 为 [[price, qty], ...]。 */
private fun depthFrom(j: JsonObject, time: Long): Depth {
    fun side(key: String): List<DepthLevel> {
        val arr = j.get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return arr.map { e ->
            val a = e.asJsonArray
            DepthLevel(a[0].asDouble, a[1].asDouble)
        }
    }
    return Depth(side("bids"), side("asks"), time)
}
