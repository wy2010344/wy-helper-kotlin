package org.wy.engine

 interface CachedPicture{
    fun draw(canvas: PlatformCanvas, x: Float, y: Float)
}

expect fun recordPicture(width: Float,height: Float,callback:(canvas: PlatformCanvas)-> Unit): CachedPicture