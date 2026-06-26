package org.wy.engine

expect class CachedPicture() {
    fun record(width: Float, height: Float)
    fun draw(canvas: PlatformCanvas, x: Float, y: Float)
}
