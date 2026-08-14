package org.wy.engine

data class RichTextStyle(
    val fontFamily: String? = null,
    val fontSize: Float = 16f,
    val fontWeight: Int = 400,
    val italic: Boolean = false,
    val color: ColorInt = rgba(0, 0, 0),
    val letterSpacing: Float = 0f,
    val wordSpacing: Float = 0f,
    val lineHeightMultiplier: Float? = null
)

data class RichTextSpan(
    val text: String,
    val style: RichTextStyle = RichTextStyle()
)

enum class RectStyle {
    TIGHT, FULL
}

enum class TextAlign {
    START, CENTER, END, JUSTIFY
}

data class TextRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

expect class PlatformParagraph {
    val height: Float
    val width: Float
    fun getGlyphPositionAtCoordinate(dx: Float, dy: Float): Int
    fun getRectsForRange(start: Int, end: Int, style: RectStyle): List<TextRect>
}

expect fun buildParagraph(
    spans: List<RichTextSpan>,
    maxWidth: Float,
    maxLines: Int = Int.MAX_VALUE,
    ellipsis: String = "\u2026",
    textAlign: TextAlign = TextAlign.START,
): PlatformParagraph
