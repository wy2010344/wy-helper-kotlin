package org.wy.engine

import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

actual class PlatformParagraph(private val layout: Layout) {
    actual val height: Float get() = layout.height.toFloat()

    fun draw(canvas: AndroidCanvas, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    actual val width: Float
        get() = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: 0f

    actual fun getGlyphPositionAtCoordinate(dx: Float, dy: Float): Int {
        val line = (0 until layout.lineCount).indexOfFirst {
            dy >= layout.getLineTop(it) && dy <= layout.getLineBottom(it)
        }
        val clampedLine = if (line < 0) layout.lineCount - 1 else line
        return layout.getOffsetForHorizontal(clampedLine, dx)
    }

    actual fun getRectsForRange(start: Int, end: Int, style: RectStyle): List<TextRect> {
        if (start >= end) return emptyList()
        val result = mutableListOf<TextRect>()
        for (line in 0 until layout.lineCount) {
            val lineStart = layout.getLineStart(line)
            val lineEnd = layout.getLineEnd(line)
            val ls = maxOf(start, lineStart)
            val le = minOf(end, lineEnd)
            if (ls < le) {
                result.add(
                    TextRect(
                        layout.getPrimaryHorizontal(ls),
                        layout.getLineTop(line).toFloat(),
                        layout.getPrimaryHorizontal(le),
                        layout.getLineBottom(line).toFloat()
                    )
                )
            }
        }
        return result
    }
}

private fun toAlignment(textAlign: TextAlign): Layout.Alignment = when (textAlign) {
    TextAlign.START -> Layout.Alignment.ALIGN_NORMAL
    TextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
    TextAlign.END -> Layout.Alignment.ALIGN_OPPOSITE
    TextAlign.JUSTIFY -> Layout.Alignment.ALIGN_JUSTIFIED
}

actual fun buildParagraph(
    spans: List<RichTextSpan>,
    maxWidth: Float,
    maxLines: Int,
    ellipsis: String,
    textAlign: TextAlign,
): PlatformParagraph {
    val ssb = SpannableStringBuilder()
    var offset = 0
    for (span in spans) {
        if (span.text.isEmpty()) continue
        ssb.append(span.text)
        val end = offset + span.text.length
        ssb.setSpan(ForegroundColorSpan(span.style.color), offset, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.setSpan(RelativeSizeSpan(span.style.fontSize / 16f), offset, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (span.style.fontWeight >= 600) {
            ssb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), offset, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        span.style.fontFamily?.let {
            ssb.setSpan(TypefaceSpan(it), offset, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        offset = end
    }

    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16f
    }
    val builder = StaticLayout.Builder.obtain(ssb, 0, ssb.length, paint, maxWidth.toInt())
        .setAlignment(toAlignment(textAlign))
        .setLineSpacing(0f, 1f)
        .setIncludePad(true)
    if (maxLines != Int.MAX_VALUE) {
        builder.setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
    }

    return PlatformParagraph(builder.build())
}
