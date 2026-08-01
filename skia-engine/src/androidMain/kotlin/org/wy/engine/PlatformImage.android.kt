package org.wy.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory

actual class PlatformImage internal constructor(
    internal val bitmap: Bitmap
) {
    actual val width: Int get() = bitmap.width
    actual val height: Int get() = bitmap.height
}

actual fun decodeImage(bytes: ByteArray): PlatformImage? {
    return try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let(::PlatformImage)
    } catch (_: Throwable) {
        null
    }
}
