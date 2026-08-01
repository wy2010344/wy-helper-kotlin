package org.wy.engine

import android.graphics.Canvas
import android.graphics.Paint

actual class PlatformCanvas(val canvas: Canvas) {
    actual fun clear(int: Int) {
        canvas.drawColor(int)
    }

    actual fun save() {
        canvas.save()
    }

    actual fun restore() {
        canvas.restore()
    }

    actual fun translate(dx: Float, dy: Float) {
        canvas.translate(dx, dy)
    }

    actual fun rotate(degrees: Float) {
        canvas.rotate(degrees)
    }

    actual fun scale(sx: Float, sy: Float) {
        canvas.scale(sx, sy)
    }

    actual fun saveLayerAlpha(alpha: Float) {
        canvas.saveLayerAlpha(null, (alpha.coerceIn(0f, 1f) * 255f).toInt())
    }

    actual fun clipRect(x: Float, y: Float, w: Float, h: Float) {
        canvas.clipRect(x, y, x + w, y + h)
    }

    actual fun clipRRect(x: Float, y: Float, w: Float, h: Float, radius: Float) {
        canvas.clipRRect(
            android.graphics.RectF(x, y, x + w, y + h),
            radius,
            radius,
            android.graphics.Region.Op.INTERSECT
        )
    }

    actual fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        canvas.drawRect(x, y, x + w, y + h, Paint().apply {
            this.color = color
            isAntiAlias = true
        })
    }

    actual fun strokeRect(x: Float, y: Float, w: Float, h: Float, color: Int, strokeWidth: Float) {
        canvas.drawRect(x, y, x + w, y + h, Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
            style = Paint.Style.STROKE
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
        canvas.drawRoundRect(x, y, x + w, y + h, radius, radius, Paint().apply {
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
        canvas.drawRoundRect(x, y, x + w, y + h, radius, radius, Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
            style = Paint.Style.STROKE
            isAntiAlias = true
        })
    }

    actual fun fillOval(x: Float, y: Float, w: Float, h: Float, color: Int) {
        canvas.drawOval(x, y, x + w, y + h, Paint().apply {
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
        canvas.drawOval(x, y, x + w, y + h, Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
            style = Paint.Style.STROKE
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
        canvas.drawLine(x1, y1, x2, y2, Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
            style = Paint.Style.STROKE
            isAntiAlias = true
        })
    }

    actual fun drawImage(image: PlatformImage, x: Float, y: Float, w: Float, h: Float) {
        val src = android.graphics.Rect(0, 0, image.bitmap.width, image.bitmap.height)
        val dst = android.graphics.RectF(x, y, x + w, y + h)
        canvas.drawBitmap(image.bitmap, src, dst, Paint().apply { isAntiAlias = true })
    }

    actual fun drawParagraph(paragraph: PlatformParagraph, x: Float, y: Float) {
        paragraph.draw(canvas, x, y)
    }
}
