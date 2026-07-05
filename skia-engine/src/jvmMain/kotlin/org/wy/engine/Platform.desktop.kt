package org.wy.engine

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontSlant
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.FontWidth
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

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

    actual fun drawRect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        val paint = Paint().apply {
            this.color = color
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
        color: ColorInt
    ) {
        val paint = Paint().apply {
            this.color = color
            isAntiAlias = true
        }
        val font = loadSystemFont(
            fontFamily ?: chineseFontName, FontStyle(
                fontWeight,
                FontWidth.NORMAL,
                FontSlant.ITALIC
            )
        )
        skCanvas.drawString(
            text, x, y, getFont(
                fontFamily,
                fontWeight,
                fontSize
            ), paint
        )
    }

    actual companion object {
        val fontStyles=mutableMapOf<Int, FontStyle>()
        fun getFont(
            fontFamily: String?,
            fontWeight: Int,
            fontSize: Float
        ): Font {
            val family=fontFamily ?: chineseFontName
            val font = Font(
                loadSystemFont(
                    family,
                    fontStyles.getOrPut(fontWeight){
                        FontStyle(
                            fontWeight,
                            FontWidth.NORMAL,
                            FontSlant.ITALIC
                        )
                    }
                ),
                fontSize
            )
            return font
        }

        actual fun measureText(
            text: String,
            fontFamily: String?,
            fontWeight: Int,
            fontSize: Float,
        ): Float {
            val font = Font(
                loadSystemFont(
                    fontFamily ?: chineseFontName, FontStyle(
                        fontWeight,
                        FontWidth.NORMAL,
                        FontSlant.ITALIC
                    )
                ), fontSize
            )
            return getFont(
                fontFamily,
                fontWeight,
                fontSize
            ).measureTextWidth(text)
        }
    }


}
