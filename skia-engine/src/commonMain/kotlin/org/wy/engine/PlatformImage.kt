package org.wy.engine

expect class PlatformImage {
    val width: Int
    val height: Int
}

expect fun decodeImage(bytes: ByteArray): PlatformImage?
