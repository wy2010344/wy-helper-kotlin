package org.wy.engine

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect

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

    actual fun rotate(degrees: Float) {
        skCanvas.rotate(degrees)
    }

    actual fun scale(sx: Float, sy: Float) {
        skCanvas.scale(sx, sy)
    }

    actual fun saveLayerAlpha(alpha: Float) {
        skCanvas.saveLayer(
            null,
            Paint().apply { this.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt() }
        )
    }

    actual fun clipRect(x: Float, y: Float, w: Float, h: Float) {
        skCanvas.clipRect(x, y, x + w, y + h, ClipMode.INTERSECT, false)
    }

    actual fun clipRRect(x: Float, y: Float, w: Float, h: Float, radius: Float) {
        skCanvas.clipRRect(RRect.makeXYWH(x, y, w, h, radius.coerceAtLeast(0f)), ClipMode.INTERSECT, false)
    }

    actual fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        skCanvas.drawRect(x, y, x + w, y + h, Paint().apply {
            this.color = color
            isAntiAlias = true
        })
    }

    actual fun strokeRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color: Int,
        strokeWidth: Float,
    ) {
        skCanvas.drawRect(x, y, x + w, y + h, Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
            setStroke(true)
            isAntiAlias = true
        })
    }

    actual fun fillRoundRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float,
        color: Int
    ) {
        skCanvas.drawRRect(RRect.makeXYWH(x, y, w, h, radius.coerceAtLeast(0f)), Paint().apply {
            this.color = color
            isAntiAlias = true
        })
    }

    actual fun strokeRoundRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float,
        color: Int,
        strokeWidth: Float,
    ) {
        skCanvas.drawRRect(RRect.makeXYWH(x, y, w, h, radius.coerceAtLeast(0f)), Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
            setStroke(true)
            isAntiAlias = true
        })
    }

    actual fun fillOval(x: Float, y: Float, w: Float, h: Float, color: Int) {
        skCanvas.drawOval(x, y, x + w, y + h, Paint().apply {
            this.color = color
            isAntiAlias = true
        })
    }

    actual fun strokeOval(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color: Int,
        strokeWidth: Float,
    ) {
        skCanvas.drawOval(x, y, x + w, y + h, Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
            setStroke(true)
            isAntiAlias = true
        })
    }

    actual fun drawLine(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: Int,
        strokeWidth: Float,
    ) {
        skCanvas.drawLine(x1, y1, x2, y2, Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
            setStroke(true)
            isAntiAlias = true
        })
    }

    actual fun drawImage(image: PlatformImage, x: Float, y: Float, w: Float, h: Float) {
        skCanvas.drawImageRect(
            image.skImage,
            Rect(0f, 0f, image.skImage.width.toFloat(), image.skImage.height.toFloat()),
            Rect(x, y, x + w, y + h),
            org.jetbrains.skia.SamplingMode.DEFAULT,
            null,
            false
        )
    }

    actual fun drawParagraph(paragraph: PlatformParagraph, x: Float, y: Float) {
        paragraph.paragraph.paint(skCanvas, x, y)
    }
}
