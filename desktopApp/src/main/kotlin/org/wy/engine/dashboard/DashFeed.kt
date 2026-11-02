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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities

// ════════════════════════════════════════════════════
// 数据源（对齐 ooc dashboard/feed.ts + market.ts）
//
//   LIVE —— 币安公共行情：REST 快照 + WS 组合流
//           wss://.../stream?streams=<全部自选>@ticker/<sym>@kline_<iv>/<sym>@aggTrade/<sym>@depth@100ms
//           深度 = REST 5000 档快照 + 增量帧维护本地全簿（对齐 ooc order-book.ts）。
//           WS 连续失败降级为 REST 轮询（4s），窗口/周期切换重建会话。
//   MOCK —— 本机 mock-api（C:\github\mock-api，npm run dev，端口 8787）：
//           REST /api/v3/** 每秒轮询一次，口径与 ooc 的 SSE(interval=1000) 一致；
//           ticker / kline / depth / trades 全部由同一条时间价格函数生成，天然同源。
//
// 不再自带本地模拟引擎：数据要么来自币安，要么来自 mock-api。
// 所有数据读取都走本对象，消费方（header/book/chart/activity）不直接碰网络层。
//
// 线程约定：网络回调在后台线程；信号（source/status/gen）只在 EDT 上写，
// 快照通过 SwingUtilities.invokeLater 投递，与渲染同线程。
// ════════════════════════════════════════════════════

internal enum class FeedSource { MOCK, LIVE }

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

/** 首屏是否齐备：至少要有 K 线、当前交易对 ticker、以及有档位的盘口。 */
private fun FeedSnap.complete(): Boolean =
    klines.isNotEmpty() &&
        ticker != null &&
        (depth?.bids?.isNotEmpty() == true || depth?.asks?.isNotEmpty() == true)

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

/** 空盘口：数据还没到（首屏 / 断线）时给渲染层一个安全值。 */
private val EMPTY_DEPTH = Depth(emptyList(), emptyList(), 0L)

internal object DashMarket {
    /** 默认接币安：只有用户显式切到 MOCK 才持久化为 mock（旧值 sim 一并视为 mock）。 */
    private const val PREF_SOURCE = "dash.source.v2"

    private val savedSource = prefs.get(PREF_SOURCE, "")
    val source: StoreRef<FeedSource> = createSignal(
        if (savedSource == "mock" || savedSource == "sim") FeedSource.MOCK else FeedSource.LIVE
    )
    val status: StoreRef<FeedStatus> = createSignal(FeedStatus.CONNECTING)
    /** 最近一次行情错误文案（空 = 无错误）；footer 红字展示（对齐 ooc 的 error 信号）。 */
    val error: StoreRef<String> = createSignal("")

    /** 数据版本：每次实时快照落地 +1；所有取数函数都读它，从而登记依赖。 */
    private val gen: StoreRef<Int> = createSignal(0)

    private var snap: FeedSnap? = null
    private var watchSym = ""
    private var watchIv = ""
    private var session: LiveSession? = null
    /** 自选列表独立会话（对齐 ooc createWatchlistSession）：不随交易对 / 周期 / 数据源切换清空。 */
    private var watchFeed: WatchSession? = null
    /** 自选行情快照：由 WatchSession 在 EDT 上写入，渲染读取即登记依赖。 */
    private val watchRef: StoreRef<List<Ticker>> = createSignal(emptyList())
    /** 会话代次：切换交易对 / 数据源后，旧会话的迟到回调直接丢弃。 */
    private var sessionGen = 0
    /** 自动降级到 mock-api 的节流时间戳（对齐 ooc SWITCH_GUARD_MS）。 */
    private var lastFallbackAt = 0L
    /** 截图预热：首份快照落地（或判定离线）时释放。 */
    private var warmLatch: CountDownLatch? = null
    private var warmGotData = false

    /** 顶栏开关：MOCK ⇄ LIVE。切到 MOCK 前先探活，服务没起就只提示、不改源（对齐 ooc toggleMockMode）。 */
    fun toggleSource() {
        if (source.value == FeedSource.MOCK) {
            applySource(FeedSource.LIVE)
            return
        }
        feedTimer.execute {
            val ok = probeMock()
            SwingUtilities.invokeLater {
                if (ok) applySource(FeedSource.MOCK) else error.value = MOCK_DOWN
            }
        }
    }

    private fun applySource(next: FeedSource) {
        source.value = next
        prefs.put(PREF_SOURCE, if (next == FeedSource.LIVE) "live" else "mock")
        session?.close()
        session = null
        snap = null
        status.value = FeedStatus.CONNECTING
        error.value = ""
        val sym = watchSym.ifEmpty { watchSymbols.first() }
        val iv = watchIv.ifEmpty { chartIntervals.first() }
        watchSym = sym
        watchIv = iv
        startSession(sym, iv)
        // 自选会话切源后重建；旧值保留在 watchRef 里，界面不会瞬间变空
        watchFeed?.close()
        watchFeed = null
        startWatchFeed()
        gen.value = gen.value + 1
    }

    /** mock-api 探活：GET /api/v3/ping（本地服务毫秒级响应，超时 3s 即视为没起）。 */
    private fun probeMock(): Boolean = runCatching {
        val res = http.send(
            HttpRequest.newBuilder(URI("$MOCK_REST/api/v3/ping"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.discarding()
        )
        res.statusCode() in 200..299
    }.getOrDefault(false)

    /** 截图入口调用：在 EDT 上拉起当前数据源（LIVE 或 mock-api），等首份快照落地。 */
    fun prewarm(symbol: String, interval: String, timeoutMs: Long = 10_000L): Boolean {
        val latch = CountDownLatch(1)
        SwingUtilities.invokeLater {
            watchSym = symbol
            watchIv = interval
            warmGotData = false
            warmLatch = latch
            startSession(symbol, interval)
            startWatchFeed()
        }
        val done = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        SwingUtilities.invokeLater { warmLatch = null }
        return done && warmGotData
    }

    /** 页面心跳里调（幂等）：交易对 / 周期变化时重建行情会话。 */
    fun ensureWatch(symbol: String, interval: String) {
        watchSym = symbol
        watchIv = interval
        startWatchFeed()
        val s = session
        if (s != null && !s.closed && s.symbol == symbol && s.interval == interval) return
        startSession(symbol, interval)
    }

    /** 自选会话幂等启动：与主行情互不影响。 */
    private fun startWatchFeed() {
        if (watchFeed?.closed == false) return
        watchFeed = WatchSession(source.value) { list ->
            SwingUtilities.invokeLater { watchRef.value = list }
        }.also { it.start() }
    }

    private fun startSession(symbol: String, interval: String) {
        session?.close()
        status.value = FeedStatus.CONNECTING
        val token = ++sessionGen
        session = LiveSession(
            symbol, interval, source.value,
            onSnap = { onSnapshot(token, it) },
            onStatus = { onStatus(token, it) },
            onErr = { onError(token, it) },
            onFail = { code -> fallbackIfBanned(code) }
        ).also { it.start() }
    }

    /**
     * 币安硬失败（418/429/403 或网络不可达）且本机 mock-api 活着时自动接管（对齐 ooc failover）。
     * 15s 内不重复切换，避免两边都挂时来回抖。
     */
    private fun fallbackIfBanned(code: Int?) {
        val hard = code == null || code == 403 || code == 418 || code == 429
        if (!hard) return
        val now = System.currentTimeMillis()
        if (now - lastFallbackAt < 15_000L) return
        lastFallbackAt = now
        feedTimer.execute {
            if (!probeMock()) return@execute
            SwingUtilities.invokeLater {
                if (source.value == FeedSource.LIVE) applySource(FeedSource.MOCK)
            }
        }
    }

    private fun onSnapshot(token: Int, s: FeedSnap) {
        SwingUtilities.invokeLater {
            if (token != sessionGen) return@invokeLater
            snap = s
            if (error.value.isNotEmpty()) error.value = ""
            gen.value = gen.value + 1
            // 预热等的是「首屏完整」：K 线 / ticker / 盘口任一先到都不算，
            // 否则截图会拿到只要到逐笔的半成品（图表与深度仍是空态）。
            if (!warmGotData && s.complete()) {
                warmGotData = true
                warmLatch?.countDown()
            }
        }
    }

    private fun onError(token: Int, message: String) {
        SwingUtilities.invokeLater {
            if (token != sessionGen) return@invokeLater
            if (error.value != message) error.value = message
        }
    }

    private fun onStatus(token: Int, s: FeedStatus) {
        SwingUtilities.invokeLater {
            if (token != sessionGen) return@invokeLater
            status.value = s
            // 判定离线时也释放预热等待，避免截图任务白等到超时
            if (s == FeedStatus.OFFLINE) warmLatch?.countDown()
        }
    }

    /** 当前快照（尚无数据时为空）；同时登记数据依赖。 */
    private fun active(): FeedSnap? {
        gen.value
        return snap
    }

    fun tickerOf(symbol: String): Ticker? {
        val s = active() ?: return null
        return s.tickers[symbol] ?: (if (symbol == s.symbol) s.ticker else null)
    }

    fun watchTickers(): List<Ticker> {
        watchRef.value
        return watchRef.value
    }

    fun klinesOf(symbol: String, interval: String): List<Candle> {
        val s = active() ?: return emptyList()
        return if (symbol == s.symbol && interval == s.interval) s.klines else emptyList()
    }

    fun depthOf(symbol: String): Depth {
        val s = active() ?: return EMPTY_DEPTH
        return if (symbol == s.symbol) s.depth ?: EMPTY_DEPTH else EMPTY_DEPTH
    }

    fun tradesOf(symbol: String): List<Trade> {
        val s = active() ?: return emptyList()
        return if (symbol == s.symbol) s.trades else emptyList()
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
/** mock-api 地址与轮询周期（对齐 ooc feed.ts 的 DEFAULT_MOCK_ORIGIN 与 SSE interval=1000）。 */
private const val MOCK_REST = "http://localhost:8787"
private const val MOCK_POLL_MS = 1000L
private const val MOCK_DOWN = "伪报文服务没响应：http://localhost:8787（先在 mock-api 项目里 npm run dev）"

private val http: HttpClient by lazy {
    // 必须显式 HTTP/1.1：默认 HTTP_2 对明文 http:// 会先发 h2c 升级头，
    // mock-api 的 Next 服务直接断连（EOF: header parser received no bytes）。
    HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(8))
        .build()
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
    source: FeedSource,
    private val onSnap: (FeedSnap) -> Unit,
    private val onStatus: (FeedStatus) -> Unit,
    private val onErr: (String) -> Unit,
    private val onFail: (Int?) -> Unit
) {
    private val mock = source == FeedSource.MOCK
    private val restBase = if (mock) MOCK_REST else BINANCE_REST
    /** mock-api 无 WS：REST 轮询直接拿全簿，不走 LocalBook。 */
    @Volatile private var mockReady = false
    private val lock = Any()
    private val klines = ArrayList<Candle>(HISTORY + 8)
    private var ticker: Ticker? = null
    private val tickers = HashMap<String, Ticker>()
    /** 轮询兜底用的浅盘口；WS 正常时由 [book] 的全簿覆盖。 */
    private var depth: Depth? = null
    private val book = LocalBook(lock)
    /** 最新在前（与 ooc pushTrade 同口径）。 */
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
        if (mock) {
            // mock-api：每秒一次 REST 轮询（对齐 ooc SSE 的 interval=1000），无 WS / 无本地簿
            pollRest()
            pollTask = feedTimer.scheduleAtFixedRate(
                { pollRest() }, MOCK_POLL_MS, MOCK_POLL_MS, TimeUnit.MILLISECONDS
            )
        } else {
            requestBook() // 全簿快照（5000 档/侧）：深度图与盘口都吃它
            pollRest() // 首屏：历史 K 线 / 24h 行情 / 浅盘口 / 成交
            pollTask = feedTimer.scheduleAtFixedRate(
                { if (!wsUp) pollRest() }, POLL_MS, POLL_MS, TimeUnit.MILLISECONDS
            )
            connect()
        }
        publishTask = feedTimer.scheduleAtFixedRate(
            { publish() }, PUBLISH_MS, PUBLISH_MS, TimeUnit.MILLISECONDS
        )
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
        // 只订阅当前交易对（对齐 ooc createMarketSession）；自选列表由独立会话负责。
        val streams = "$s@kline_$interval/$s@aggTrade/$s@depth@100ms"
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

        // LIVE 轮询兜底用浅盘口（limit=50，对齐 ooc loadSnapshot）；
        // MOCK 没有 WS，直接每次拿全簿（limit=5000），深度图与盘口才有完整曲线。
        val depthLimit = if (mock) BOOK_LIMIT else 50
        restGet("/api/v3/depth?$q&limit=$depthLimit") { body ->
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

        // 当前交易对的 24h 行情（对齐 ooc loadSnapshot 的单币种请求）。
        // 自选列表的批量 24h 行情由独立会话 WatchSession 负责，避免两边重复拉同一批数据。
        restGet("/api/v3/ticker/24hr?symbol=" + enc(symbol)) { body ->
            val t = tickerFromRest(JsonParser.parseString(body).asJsonObject)
            synchronized(lock) {
                tickers[t.symbol] = t
                ticker = t
                dirty = true
            }
        }
    }

    /**
     * 拉全簿快照重建本地簿（建连 / 重连 / 增量缺帧）。
     * 1.5s 内不重复请求：10Hz 增量流缺帧时不会把 REST 打成热循环。
     */
    private fun requestBook() {
        if (mock) return
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
            HttpRequest.newBuilder(URI(restBase + path)).timeout(Duration.ofSeconds(10)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        ).whenComplete { res, err ->
            if (closed) return@whenComplete
            if (err != null || res == null || res.statusCode() !in 200..299) {
                if (mock) mockReady = false
                val code = res?.statusCode()
                // 币安硬失败：交给外层决定是否降级到本机 mock-api
                if (!mock) onFail(code)
                // WS 也不通且 REST 连续失败：判定离线（地域封禁 / 断网 / mock-api 没起）
                if (restFails.incrementAndGet() >= 2 && !wsUp) onStatus(FeedStatus.OFFLINE)
                // 首屏还没拿到行情、或已降级轮询时，把失败原因透出到 footer；
                // 稳态（WS 正常）的 REST 抖动保持静默，对齐 ooc syncBook/pollSnapshot 的 catch
                if (!published || !wsUp) {
                    val code = res?.statusCode()
                    onErr(
                        when {
                            mock -> MOCK_DOWN
                            code != null -> "Binance $code: " + path.substringBefore('?')
                            else -> "行情请求失败，正在重试"
                        }
                    )
                }
                return@whenComplete
            }
            val wasDown = restFails.getAndSet(0) >= 2
            if (mock) {
                if (!mockReady) {
                    mockReady = true
                    onStatus(FeedStatus.MOCK)
                }
            } else if (wasDown && !wsUp) {
                onStatus(FeedStatus.POLLING)
            }
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
// 自选列表会话（对齐 ooc dashboard/market.ts 的 createWatchlistSession）
//
// 与主行情会话完全独立：LIVE 只订阅全部自选币种的 @ticker 组合流，mock-api
// 则按秒轮询批量 24h 行情；WS 抖动 ≥3 次后降级为 8s REST 轮询。
// 因此切换交易对 / 周期 / 数据源时，自选列表不会跟着主会话一起清空。
// ════════════════════════════════════════════════════

/** WS 抖动降级后的自选轮询周期（对齐 ooc 的 8000ms）。 */
private const val WATCH_POLL_MS = 8000L

private class WatchSession(
    val source: FeedSource,
    private val onTickers: (List<Ticker>) -> Unit
) {
    private val mock = source == FeedSource.MOCK
    private val restBase = if (mock) MOCK_REST else BINANCE_REST
    private val lock = Any()
    private val tickers = HashMap<String, Ticker>()

    @Volatile var closed = false
        private set
    private var ws: WebSocket? = null
    @Volatile private var wsUp = false
    @Volatile private var openedAt = 0L
    private var failures = 0
    private var retryDelay = 800L
    private var handlingDown = false
    private var healthyTask: ScheduledFuture<*>? = null
    private var pollTask: ScheduledFuture<*>? = null

    fun start() {
        fetch()
        if (mock) {
            // mock-api 没有 WS：与主行情同节奏，每秒批量轮询
            pollTask = feedTimer.scheduleAtFixedRate(
                { fetch() }, MOCK_POLL_MS, MOCK_POLL_MS, TimeUnit.MILLISECONDS
            )
        } else {
            connect()
            // WS 不通时的兜底轮询（对齐 ooc 的 8s）
            pollTask = feedTimer.scheduleAtFixedRate(
                { if (!wsUp) fetch() }, WATCH_POLL_MS, WATCH_POLL_MS, TimeUnit.MILLISECONDS
            )
        }
    }

    fun close() {
        closed = true
        pollTask?.cancel(false)
        healthyTask?.cancel(false)
        healthyTask = null
        ws?.abort()
        ws = null
    }

    private fun connect() {
        if (closed) return
        handlingDown = false
        val streams = watchSymbols.joinToString("/") { it.lowercase() + "@ticker" }
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

    /** 断线重连退避：连续「连上就断」时指数退避，稳定存活过才回到 800ms。 */
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
        if (failures >= 3) fetch() // 到降级阈值时先补一次 REST，别等下一个 8s
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
            webSocket.request(1)
            fetch() // 首屏补齐（WS 只有增量）
            healthyTask?.cancel(false)
            healthyTask = feedTimer.schedule({
                if (!closed && wsUp) {
                    failures = 0
                    retryDelay = 800L
                }
            }, HEALTHY_MS, TimeUnit.MILLISECONDS)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            sb.append(data)
            if (last) {
                val text = sb.toString()
                sb.setLength(0)
                val root = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                val obj = root?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: root
                if (obj != null && obj.str("e") == "24hrTicker") put(tickerFromWs(obj))
            }
            webSocket.request(1)
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            if (!closed) onDown()
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            onDown()
            return null
        }
    }

    private fun put(t: Ticker) {
        val list = synchronized(lock) {
            tickers[t.symbol] = t
            ordered()
        }
        onTickers(list)
    }

    private fun fetch() {
        if (closed) return
        // 与 ooc 一致：JSON.stringify(symbols) + encodeURIComponent，即 ["BTCUSDT","ETHUSDT"]
        val all = watchSymbols.joinToString(",", "[", "]") { "\"$it\"" }
        http.sendAsync(
            HttpRequest.newBuilder(URI("$restBase/api/v3/ticker/24hr?symbols=" + enc(all)))
                .timeout(Duration.ofSeconds(10)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        ).whenComplete { res, err ->
            if (closed) return@whenComplete
            if (err != null || res == null || res.statusCode() !in 200..299) return@whenComplete
            runCatching {
                val arr = JsonParser.parseString(res.body()).asJsonArray
                val list = synchronized(lock) {
                    arr.forEach { e -> tickerFromRest(e.asJsonObject).let { tickers[it.symbol] = it } }
                    ordered()
                }
                onTickers(list)
            }
        }
    }

    /** 固定按 watchSymbols 顺序输出，列表不会因收发顺序跳行。 */
    private fun ordered(): List<Ticker> = watchSymbols.mapNotNull { tickers[it] }
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

/** WS @ticker：单字段名（c=最新价、o=开盘价、P=涨跌幅；b/B=买一价/量，a/A=卖一价/量）。 */
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
        ask = j.dbl("a"),
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
