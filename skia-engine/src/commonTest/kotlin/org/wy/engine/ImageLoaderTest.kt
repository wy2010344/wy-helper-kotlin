package org.wy.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ImageLoader] 薄层纯逻辑测试：请求去重、失败/未注入路径。
 *
 * 不依赖真实解码结果（decode 由平台 [decodeImage] 完成）；缓存命中的确定性验证见 jvmTest。
 */
class ImageLoaderTest {

    @Test
    fun fetchOnceAndDecode() {
        var fetcherCalled = 0
        val loader = ImageLoader().apply {
            fetcher = { url, cb ->
                fetcherCalled++
                assertEquals("https://a/b.png", url)
                cb(byteArrayOf(1, 2, 3))
            }
        }
        var delivered = false
        loader.load("https://a/b.png") { delivered = true }
        assertEquals(1, fetcherCalled)
        assertTrue(delivered, "无论解码成败都必须回调一次")
    }

    @Test
    fun concurrentSameUrlFetchesOnce() {
        var fetcherCalled = 0
        // 模拟异步：先保存回调，由测试稍后手动结算
        var savedCb: ((ByteArray?) -> Unit)? = null
        val loader = ImageLoader().apply {
            fetcher = { _, cb -> fetcherCalled++; savedCb = cb }
        }
        var c1 = 0
        var c2 = 0
        loader.load("https://a/dup.png") { c1++ }
        loader.load("https://a/dup.png") { c2++ }
        assertEquals(1, fetcherCalled, "同 URL 并发加载只应 fetch 一次")
        assertEquals(0, c1)
        assertEquals(0, c2)
        savedCb!!(null)  // 结算在途请求
        assertEquals(1, c1)
        assertEquals(1, c2)
    }

    @Test
    fun fetchFailureYieldsNullWithoutCrash() {
        val loader = ImageLoader().apply {
            fetcher = { _, cb -> cb(null) }
        }
        var got: PlatformImage? = null
        loader.load("https://a/fail.png") { got = it }
        assertNull(got)
    }

    @Test
    fun unconfiguredLoaderYieldsNull() {
        val loader = ImageLoader()
        var got: PlatformImage? = null
        loader.load("https://a/none.png") { got = it }
        assertNull(got, "未注入 fetcher 时应回调 null，不崩溃")
    }
}