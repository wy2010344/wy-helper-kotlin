package org.wy.engine

import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontSlant
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.FontWidth
import org.jetbrains.skia.paragraph.Alignment
import org.jetbrains.skia.paragraph.DecorationLineStyle
import org.jetbrains.skia.paragraph.DecorationStyle
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.Paragraph
import org.jetbrains.skia.paragraph.ParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.RectHeightMode
import org.jetbrains.skia.paragraph.RectWidthMode
import org.jetbrains.skia.paragraph.TextStyle

actual class PlatformParagraph(internal val paragraph: Paragraph) {
    actual val height: Float get() = paragraph.height

    actual val width: Float get() = paragraph.longestLine

    actual fun getGlyphPositionAtCoordinate(dx: Float, dy: Float): Int {
        return paragraph.getGlyphPositionAtCoordinate(dx, dy).position
    }

    actual fun getRectsForRange(start: Int, end: Int, style: RectStyle): List<TextRect> {
        if (start >= end) return emptyList()
        val (heightMode, widthMode) = when (style) {
            RectStyle.TIGHT -> RectHeightMode.TIGHT to RectWidthMode.TIGHT
            RectStyle.FULL -> RectHeightMode.MAX to RectWidthMode.MAX
        }
        return paragraph.getRectsForRange(
            start, end, heightMode, widthMode
        ).map { TextRect(it.rect.left, it.rect.top, it.rect.right, it.rect.bottom) }
    }

    actual fun getLineMetrics(): List<PlatformLineMetric> =
        paragraph.lineMetrics.map { PlatformLineMetric(it.startIndex, it.endIndex) }
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
    wordSpacing: Float = 0f,
    lineHeightMultiplier: Float? = null,
    decoration: TextDecoration = TextDecoration.None
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
        if (lineHeightMultiplier != null) setHeight(lineHeightMultiplier)
        if (decoration.hasAny) {
            setDecorationStyle(
                // underline / overline / lineThrough / gaps / color / lineStyle / thickness
                DecorationStyle(
                    decoration.underline,
                    false,
                    decoration.lineThrough,
                    false,
                    fontColor,
                    DecorationLineStyle.SOLID,
                    1f
                )
            )
        }
    }
}

private fun toAlignment(textAlign: TextAlign): Alignment = when (textAlign) {
    TextAlign.START -> Alignment.START
    TextAlign.CENTER -> Alignment.CENTER
    TextAlign.END -> Alignment.END
    TextAlign.JUSTIFY -> Alignment.JUSTIFY
}

actual fun buildParagraph(
    spans: List<RichTextSpan>,
    maxWidth: Float,
    maxLines: Int,
    ellipsis: String,
    textAlign: TextAlign,
): PlatformParagraph {
    val style = ParagraphStyle().apply {
        this.alignment = toAlignment(textAlign)
        if (maxLines != Int.MAX_VALUE) {
            this.maxLinesCount = maxLines
            this.ellipsis = ellipsis
        }
    }
    val builder = ParagraphBuilder(style, defaultFontCollection)

    for (span in spans) {
        if (span.text.isEmpty()) continue
        builder.pushStyle(
            makeTextStyle(
                span.style.fontFamily,
                span.style.fontWeight,
                span.style.fontSize,
                span.style.color,
                span.style.letterSpacing,
                span.style.wordSpacing,
                span.style.lineHeightMultiplier,
                span.style.decoration
            )
        )
        builder.addText(span.text)
        builder.popStyle()
    }

    val paragraph = builder.build()
    paragraph.layout(maxWidth)
    return PlatformParagraph(paragraph)
}
