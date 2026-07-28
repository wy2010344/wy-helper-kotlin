package org.wy.engine

import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontSlant
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.FontWidth
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.Paragraph
import org.jetbrains.skia.paragraph.ParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.RectHeightMode
import org.jetbrains.skia.paragraph.RectWidthMode
import org.jetbrains.skia.paragraph.TextStyle

actual class PlatformParagraph(internal val paragraph: Paragraph) {
    actual val height: Float get() = paragraph.height
    actual val longestLine: Float get() = paragraph.longestLine

    actual fun paint(canvas: PlatformCanvas, x: Float, y: Float) {
        paragraph.paint(canvas.skCanvas, x, y)
    }

    actual fun getGlyphPositionAtCoordinate(dx: Float, dy: Float): Int {
        return paragraph.getGlyphPositionAtCoordinate(dx, dy).position
    }

    actual fun getRectsForRange(start: Int, end: Int): List<TextRect> {
        if (start >= end) return emptyList()
        return paragraph.getRectsForRange(
            start, end, RectHeightMode.MAX, RectWidthMode.MAX
        ).map { TextRect(it.rect.left, it.rect.top, it.rect.right, it.rect.bottom) }
    }
}

private val defaultFontCollection: FontCollection by lazy {
    FontCollection().setDefaultFontManager(FontMgr.default)
}

private fun makeTextStyle(
    fontFamily: String?,
    fontWeight: Int,
    fontSize: Float,
    fontColor: ColorInt,
    letterSpacing: Float = 0f,
    wordSpacing: Float = 0f
): TextStyle {
    return TextStyle().apply {
        fontFamily?.let { setFontFamily(it) }
        setFontSize(fontSize)
        setFontStyle(
            FontStyle(fontWeight, FontWidth.NORMAL, FontSlant.UPRIGHT)
        )
        setColor(fontColor)
        if (letterSpacing != 0f) setLetterSpacing(letterSpacing)
        if (wordSpacing != 0f) setWordSpacing(wordSpacing)
    }
}

actual fun buildParagraph(
    spans: List<RichTextSpan>,
    maxWidth: Float
): PlatformParagraph {
    val style = ParagraphStyle()
    val builder = ParagraphBuilder(style, defaultFontCollection)

    for (span in spans) {
        if (span.text.isEmpty()) continue
        builder.pushStyle(makeTextStyle(
            span.style.fontFamily,
            span.style.fontWeight,
            span.style.fontSize,
            span.style.color,
            span.style.letterSpacing,
            span.style.wordSpacing
        ))
        builder.addText(span.text)
        builder.popStyle()
    }

    val paragraph = builder.build()
    paragraph.layout(maxWidth)
    return PlatformParagraph(paragraph)
}

actual fun buildParagraph(
    text: String,
    fontFamily: String?,
    fontWeight: Int,
    fontSize: Float,
    fontColor: ColorInt,
    lineHeight: Float,
    maxWidth: Float,
    wordBreak: WordBreak
): PlatformParagraph {
    val style = ParagraphStyle()
    val builder = ParagraphBuilder(style, defaultFontCollection)

    builder.pushStyle(makeTextStyle(fontFamily, fontWeight, fontSize, fontColor))
    builder.addText(text)
    builder.popStyle()

    val paragraph = builder.build()
    paragraph.layout(maxWidth)
    return PlatformParagraph(paragraph)
}
