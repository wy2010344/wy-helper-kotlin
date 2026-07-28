package org.wy.engine

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.Paint

actual class PlatformCanvas(val skCanvas: Canvas) {
    actual fun clear(int: Int) {
        skCanvas.clear(int)
    }

    actual fun save() {
        skCanvas.save()
    }

    actual fun restore() {
        skCanvas.restore()
    }

    actual fun translate(dx: Float, dy: Float) {
        skCanvas.translate(dx, dy)
    }

    actual fun clipRect(x: Float, y: Float, w: Float, h: Float) {
        skCanvas.clipRect(x, y, x + w, y + h, ClipMode.INTERSECT, false)
    }

    actual fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        val paint = Paint().apply {
            this.color = color
            isAntiAlias = true
        }
        skCanvas.drawRect(x, y, x + w, y + h, paint)
    }

    actual fun strokeRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color: Int,
        strokeWidth: Float,
    ) {
        val paint = Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
            this.setStroke(true)
            isAntiAlias = true
        }
        skCanvas.drawRect(x, y, x + w, y + h, paint)
    }

    actual fun drawParagraph(paragraph: PlatformParagraph, x: Float, y: Float) {
        paragraph.paragraph.paint(skCanvas, x, y)
    }
}
