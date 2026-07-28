package org.wy.engine

data class RichTextStyle(
    val fontFamily: String? = null,
    val fontSize: Float = 16f,
    val fontWeight: Int = 400,
    val color: ColorInt = rgba(0, 0, 0),
    val letterSpacing: Float = 0f,
    val wordSpacing: Float = 0f
)

data class RichTextSpan(
    val text: String,
    val style: RichTextStyle = RichTextStyle()
)

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
    val longestLine: Float
    fun paint(canvas: PlatformCanvas, x: Float, y: Float)
    fun getGlyphPositionAtCoordinate(dx: Float, dy: Float): Int
    fun getRectsForRange(start: Int, end: Int): List<TextRect>
}

expect fun buildParagraph(
    spans: List<RichTextSpan>,
    maxWidth: Float
): PlatformParagraph

expect fun buildParagraph(
    text: String,
    fontFamily: String?,
    fontWeight: Int,
    fontSize: Float,
    fontColor: ColorInt,
    lineHeight: Float,
    maxWidth: Float,
    wordBreak: WordBreak
): PlatformParagraph
