package org.wy.engine

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * [ImageLoader] 缓存语义的确定性验证（jvmTest）：用 Skia 现场编码出合法 PNG，
 * 走真实 [decodeImage] 解码，覆盖「命中缓存不重复 fetch」与「失败后重试成功」。
 */
class ImageLoaderCacheTest {

    /** 生成一个真实可解码的 PNG 字节（2x2 空白）。 */
    private fun validPngBytes(): ByteArray {
        val surface = Surface.makeRasterN32Premul(2, 2)
        val image = surface.makeImageSnapshot()
        return image.encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    @Test
    fun successCachesAndSkipsRefetch() {
        var fetcherCalled = 0
        val loader = ImageLoader().apply {
            fetcher = { _, cb -> fetcherCalled++; cb(validPngBytes()) }
        }

        var first: PlatformImage? = null
        loader.load("https://a/hit.png") { first = it }
        assertNotNull(first, "合法 PNG 应解码成功")

        val callsAfterFirst = fetcherCalled
        var second: PlatformImage? = null
        loader.load("https://a/hit.png") { second = it }

        assertEquals(callsAfterFirst, fetcherCalled, "命中缓存不应再次 fetch")
        assertSame(first, second, "命中缓存应返回同一 PlatformImage 实例")
    }

    @Test
    fun failureRetriesOnNextLoad() {
        var fetcherCalled = 0
        val loader = ImageLoader().apply {
            fetcher = { _, cb ->
                fetcherCalled++
                // 第一次给坏字节（解码失败），第二次给合法 PNG
                cb(if (fetcherCalled == 1) byteArrayOf(1, 2, 3) else validPngBytes())
            }
        }

        var first: PlatformImage? = null
        loader.load("https://a/retry.png") { first = it }
        assertNull(first, "坏字节应解码失败")

        var second: PlatformImage? = null
        loader.load("https://a/retry.png") { second = it }
        assertEquals(2, fetcherCalled, "失败不缓存，应重新取字节")
        assertNotNull(second, "第二次字节合法应解码成功")
    }
}