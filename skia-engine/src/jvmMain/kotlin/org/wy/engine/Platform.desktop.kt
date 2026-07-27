package org.wy.engine

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontSlant
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.FontWidth
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Point
import org.jetbrains.skia.shaper.Shaper
import org.jetbrains.skia.shaper.ShapingOptions

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

    actual fun drawText(
        text: String,
        x: Float,
        y: Float,
        fontFamily: String?,
        fontWeight: Int,
        fontSize: Float,
        color: ColorInt,
        letterSpacing: Float,
        wordSpacing: Float,
        isRTL: Boolean
    ) {
        if (text.isEmpty()) return
        val paint = Paint().apply {
            this.color = color
            isAntiAlias = true
        }
        val font = getFont(fontFamily, fontWeight, fontSize)
        val shaper = getShaper()
        val opts = ShapingOptions(
            fontMgr = null,
            features = null,
            isLeftToRight = !isRTL,
            isApproximateSpaces = false,
            isApproximatePunctuation = false
        )
        val blob = shaper.shape(text, font, opts, Float.MAX_VALUE, Point(0f, 0f))
        if (blob != null) {
            skCanvas.drawTextBlob(blob, x, y, paint)
        }
    }
}

private val fontStyles = mutableMapOf<Int, FontStyle>()
private fun getFont(
    fontFamily: String?,
    fontWeight: Int,
    fontSize: Float
): Font {
    val family = fontFamily ?: chineseFontName
    return Font(
        loadSystemFont(
            family,
            fontStyles.getOrPut(fontWeight) {
                FontStyle(
                    fontWeight,
                    FontWidth.NORMAL,
                    FontSlant.UPRIGHT
                )
            }
        ),
        fontSize
    )
}

private var sharedShaper: Shaper? = null
private fun getShaper(): Shaper {
    return sharedShaper ?: Shaper.make().also { sharedShaper = it }
}

actual fun measureText(
    text: String,
    fontFamily: String?,
    fontWeight: Int,
    fontSize: Float,
    letterSpacing: Float,
    wordSpacing: Float,
    isRTL: Boolean
): Float {
    if (text.isEmpty()) return 0f
    val font = getFont(fontFamily, fontWeight, fontSize)
    val shaper = getShaper()
    val opts = ShapingOptions(
        fontMgr = null,
        features = null,
        isLeftToRight = !isRTL,
        isApproximateSpaces = false,
        isApproximatePunctuation = false
    )
    val blob = shaper.shape(text, font, opts, Float.MAX_VALUE, Point(0f, 0f))
    return blob?.bounds?.width ?: font.measureTextWidth(text)
}
