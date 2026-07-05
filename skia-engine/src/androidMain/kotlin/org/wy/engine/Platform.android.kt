package org.wy.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

actual class PlatformCanvas(val androidCanvas: Canvas)

actual fun platformClear(canvas: PlatformCanvas, width: Float, height: Float) {
    canvas.androidCanvas.drawColor(android.graphics.Color.rgb(0xF5, 0xF5, 0xF5))
}

actual fun platformSave(canvas: PlatformCanvas) {
    canvas.androidCanvas.save()
}

actual fun platformRestore(canvas: PlatformCanvas) {
    canvas.androidCanvas.restore()
}

actual fun platformTranslate(canvas: PlatformCanvas, dx: Float, dy: Float) {
    canvas.androidCanvas.translate(dx, dy)
}

actual fun PlatformCanvas.clipRect(x: Float, y: Float, w: Float, h: Float) {
    androidCanvas.clipRect(x, y, x + w, y + h)
}

actual fun PlatformCanvas.drawRect(x: Float, y: Float, w: Float, h: Float, color: ColorInt) {
    val paint = Paint().apply { this.color = color; isAntiAlias = true }
    androidCanvas.drawRect(x, y, x + w, y + h, paint)
}

actual fun PlatformCanvas.drawText(text: String, x: Float, y: Float, fontSize: Float, color: ColorInt): Float {
    val paint = Paint().apply {
        this.color = color
        this.textSize = fontSize
        isAntiAlias = true
        typeface = Typeface.DEFAULT
    }
    androidCanvas.drawText(text, x, y, paint)
    return paint.measureText(text)
}

actual fun PlatformCanvas.measureText(text: String, fontSize: Float): Float {
    val paint = Paint().apply { textSize = fontSize }
    return paint.measureText(text)
}
