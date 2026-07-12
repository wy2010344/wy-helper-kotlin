package org.wy.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

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

    actual fun clipRect(x: Float, y: Float, w: Float, h: Float) {
        canvas.clipRect(x, y, x + w, y + h)
    }

    actual fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        val paint = Paint().apply { this.color = color; isAntiAlias = true }
        canvas.drawRect(x, y, x + w, y + h, paint)
    }

    actual fun strokeRect(x: Float, y: Float, w: Float, h: Float, color: Int, strokeWidth: Float) {
        val paint = Paint().apply {
            this.color = color;
            this.strokeWidth = strokeWidth
            isAntiAlias = true
        }
        canvas.drawRect(x, y, x + w, y + h, paint)

    }

    actual fun drawText(
        text: String,
        x: Float,
        y: Float,
        fontFamily: String?,
        fontWeight: Int,
        fontSize: Float,
        color: ColorInt
    ) {

        val paint = Paint().apply {
            this.color = color
            this.textSize = fontSize
            isAntiAlias = true
            typeface = Typeface.DEFAULT
        }
        canvas.drawText(text, x, y, paint)
    }

}


actual fun measureText(
    text: String,
    fontFamily: String?,
    fontWeight: Int,
    fontSize: Float
): Float {
    val paint = Paint().apply { textSize = fontSize }
    return paint.measureText(text)
}
