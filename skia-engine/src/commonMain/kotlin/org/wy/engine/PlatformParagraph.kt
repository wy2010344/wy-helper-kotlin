package org.wy.engine

/**
 * 文本装饰线（可组合）。
 * 渲染为文字同色的实线；平台不支持时静默降级为无装饰。
 */
data class TextDecoration(
    val underline: Boolean = false,
    val lineThrough: Boolean = false
) {
    val hasAny: Boolean get() = underline || lineThrough

    companion object {
        val None = TextDecoration()
        val Underline = TextDecoration(underline = true)
        val LineThrough = TextDecoration(lineThrough = true)
    }
}

data class RichTextStyle(
    val fontFamily: String? = null,
    val fontSize: Float = 16f,
    val fontWeight: Int = 400,
    val italic: Boolean = false,
    val color: ColorInt = rgba(0, 0, 0),
    val letterSpacing: Float = 0f,
    val wordSpacing: Float = 0f,
    val lineHeightMultiplier: Float? = null,
    /** 装饰线；链接片段通常配合 underline 使用。 */
    val decoration: TextDecoration = TextDecoration.None
)

data class RichTextSpan(
    val text: String,
    val style: RichTextStyle = RichTextStyle(),
    /** 链接目标：非空时该片段可点击打开（由 [UrlOpener] 的平台实现执行）。 */
    val url: String? = null
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

/** 软行度量:[startIndex, endIndex) 为该行占用的字符区间（换行符归属按平台，上层自行归一化）。 */
data class PlatformLineMetric(val startIndex: Int, val endIndex: Int)

expect class PlatformParagraph {
    val height: Float
    val width: Float
    fun getGlyphPositionAtCoordinate(dx: Float, dy: Float): Int
    fun getRectsForRange(start: Int, end: Int, style: RectStyle): List<TextRect>

    /** 包含 [offset] 的词边界（半开区间 [start, end)）；无法分词时返回 null。 */
    fun getWordBoundary(offset: Int): Pair<Int, Int>?

    /** 全部软行度量（按行序）。 */
    fun getLineMetrics(): List<PlatformLineMetric>
}

expect fun buildParagraph(
    spans: List<RichTextSpan>,
    maxWidth: Float,
    maxLines: Int = Int.MAX_VALUE,
    ellipsis: String = "\u2026",
    textAlign: TextAlign = TextAlign.START,
): PlatformParagraph
