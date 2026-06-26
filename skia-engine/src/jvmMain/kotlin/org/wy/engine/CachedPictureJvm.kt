package org.wy.engine

import org.jetbrains.skia.Picture
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect

actual class CachedPicture {
    private var picture: Picture? = null

    actual fun record(width: Float, height: Float) {
        val recorder = PictureRecorder()
        val rect = Rect(0f, 0f, width, height)
        val skCanvas = recorder.beginRecording(rect)
        val pc = PlatformCanvas(skCanvas)
        picture = recorder.finishRecordingAsPicture()
    }

    actual fun draw(canvas: PlatformCanvas, x: Float, y: Float) {
        val pic = picture ?: return
        canvas.skCanvas.save()
        canvas.skCanvas.translate(x, y)
        canvas.skCanvas.drawPicture(pic)
        canvas.skCanvas.restore()
    }
}
