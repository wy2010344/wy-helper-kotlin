package org.wy.engine

import android.graphics.Picture

actual class CachedPicture {
    private var picture: Picture? = null

    actual fun record(width: Float, height: Float, draw: DrawContext.() -> Unit) {
        picture = Picture()
        val androidCanvas = picture!!.beginRecording(width.toInt(), height.toInt())
        val pc = PlatformCanvas(androidCanvas)
        val ctx = DrawContext(pc)
        ctx.x = 0f
        ctx.y = 0f
        ctx.width = width
        ctx.height = height
        ctx.draw()
        picture!!.endRecording()
    }

    actual fun draw(canvas: PlatformCanvas, x: Float, y: Float) {
        val pic = picture ?: return
        canvas.androidCanvas.save()
        canvas.androidCanvas.translate(x, y)
        canvas.androidCanvas.drawPicture(pic)
        canvas.androidCanvas.restore()
    }
}
