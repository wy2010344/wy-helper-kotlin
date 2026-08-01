package org.wy.engine

import org.jetbrains.skia.Image

actual class PlatformImage internal constructor(
    internal val skImage: Image
) {
    actual val width: Int get() = skImage.width
    actual val height: Int get() = skImage.height
}

actual fun decodeImage(bytes: ByteArray): PlatformImage? {
    return try {
        Image.makeFromEncoded(bytes)?.let(::PlatformImage)
    } catch (_: Throwable) {
        null
    }
}
