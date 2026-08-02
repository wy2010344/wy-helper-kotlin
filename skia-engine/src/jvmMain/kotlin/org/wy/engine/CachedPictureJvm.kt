package org.wy.engine

import org.jetbrains.skia.Picture
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect


actual fun recordPicture(
    width: Float,
    height: Float,
    callback: (canvas: PlatformCanvas) -> Unit
): CachedPicture {
    val recorder = PictureRecorder()
    val rect = Rect(0f, 0f, width, height)
    val skCanvas = recorder.beginRecording(rect)
    callback(PlatformCanvas(skCanvas))
    val picture = recorder.finishRecordingAsPicture()
    return object : CachedPicture {
        override fun draw(canvas: PlatformCanvas, x: Float, y: Float) {
            canvas.skCanvas.save()
            canvas.skCanvas.translate(x, y)
            canvas.skCanvas.drawPicture(picture)
            canvas.skCanvas.restore()
        }
    }
}