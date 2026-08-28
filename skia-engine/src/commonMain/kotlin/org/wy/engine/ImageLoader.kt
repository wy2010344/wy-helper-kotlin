package org.wy.engine

/**
 * 图片异步加载薄层：负责「取字节 → 解码 → 结果回调 + 去重缓存」。
 *
 * 设计约束（同 [UrlOpener]）：
 * - 引擎不持有平台网络环境，取字节实现由宿主通过 [fetcher] 注入
 *   （Desktop 开线程/OkHttp、Android 用协程+网络栈、Web 用 fetch），实现方可自由选择异步策略。
 * - [fetcher] 必须恰好回调一次 onBytes；本层在取到字节后同步调用 [decodeImage] 解码并回调结果。
 * - 同一 URL 并发/重复请求只 fetch 一次；解码成功结果进内存缓存，命中后直接回调缓存值。
 * - 解码失败（返回 null）不缓存，下次请求重新取字节重试。
 *
 * 说明：本层不承诺线程安全，回调建议回到宿主的主线程（fetcher 内自行切换）。
 */
class ImageLoader {

    /** 平台宿主注入的取字节实现：传入 url，把字节结果（失败为 null）通过 [onBytes] 回调。 */
    var fetcher: ((url: String, onBytes: (ByteArray?) -> Unit) -> Unit)? = null

    /** 成功解码的内存缓存：url -> PlatformImage（仅缓存成功结果）。 */
    private val cache = mutableMapOf<String, PlatformImage>()

    /** 进行中的请求：url -> 排队等待同一结果的回调列表。 */
    private val inflight = mutableMapOf<String, MutableList<(PlatformImage?) -> Unit>>()

    /**
     * 发起加载：异步取字节并解码后回调 [onImage]（成功为解码结果，失败/未注入 fetcher 为 null）。
     * 同 URL 已缓存则立即回调节缓存值；正在加载则排队复用本次加载结果。
     */
    fun load(url: String, onImage: (PlatformImage?) -> Unit) {
        cache[url]?.let {
            onImage(it)
            return
        }

        inflight[url]?.let {
            it.add(onImage)
            return
        }

        val list = mutableListOf(onImage)
        inflight[url] = list

        val f = fetcher
        if (f == null) {
            engineLogWarn("ImageLoader 未注入 fetcher，无法加载图片: url=$url")
            finishInflight(url, null)
            return
        }

        f(url) { bytes ->
            finishInflight(url, bytes?.let { decodeImage(it) })
        }
    }

    /** 结算一批等待回调：[image] 非 null 时写入缓存；随后按序回调并清空 inflight。 */
    private fun finishInflight(url: String, image: PlatformImage?) {
        if (image != null) {
            cache[url] = image
        }
        val pending = inflight.remove(url)
        pending?.forEach { it(image) }
    }
}