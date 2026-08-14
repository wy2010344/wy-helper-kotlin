package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.lib.getValue
import org.wy.signal.getValue
import org.wy.signal.memo
import kotlin.math.max
import kotlin.math.min

open class RichTextNode(
    context: StateHolder<Node,List<Node>>
) : RectNode(context), Selectable {

    val selectionManager = context.consume(selectionManagerContext)!!

    open val spans: List<RichTextSpan> = emptyList()
    open val selectionColor: ColorInt = rgba(100, 100, 200, 60)

    private val fullText by memo { spans.joinToString("") { it.text } }

    open val autoWidth = false

    open val maxLines: Int = Int.MAX_VALUE
    open val ellipsis: String = "\u2026"
    open val textAlign: TextAlign = TextAlign.START

    protected val paragraph by memo {
        if (fullText.isEmpty()) return@memo null
        else buildParagraph(
            spans,
            if (autoWidth) Float.MAX_VALUE else innerWidth,
            maxLines,
            ellipsis,
            textAlign
        )
    }

    override val argWidth: LayoutSize
        get() = if (autoWidth) LayoutSize(paragraph?.width ?: 0f, true) else super.argWidth

    override val argHeight: LayoutSize
        get() {
            val p = paragraph
            val h = p?.height ?: (maxFontSizeInSpans() * 1.4f)
            return LayoutSize(h, true)
        }

    private fun maxFontSizeInSpans(): Float {
        var maxFs = 0f
        for (span in spans) {
            maxFs = max(maxFs, span.style.fontSize)
        }
        return max(maxFs, 1f)
    }

    override fun mouseDown(e: MouseEvent) {
        selectionManager.handleMouseDown(this, e.x, e.y, e.shift)
    }

    override fun draw(canvas: PlatformCanvas) {
        val p = paragraph ?: return
        canvas.drawParagraph(p, paddingInlineStart, paddingBlockStart)
        super.draw(canvas)
    }

    override fun getOffsetAt(localX: Float, localY: Float): Int {
        return paragraph?.getGlyphPositionAtCoordinate(localX, localY) ?: 0
    }
    override fun getRectsForRange(start: Int, end: Int): List<TextRect> {
        return paragraph?.getRectsForRange(start, end) ?: emptyList()
    }
    override fun getText(start: Int, end: Int): String {
        return fullText.substring(start, end.coerceAtMost(fullText.length))
    }
    override fun textLength(): Int = fullText.length
    override fun rootToLocal(rootX: Float, rootY: Float): Pair<Float, Float> {
        return rootX - absoluteX to rootY - absoluteY
    }
    override fun localToRoot(localX: Float, localY: Float): Pair<Float, Float> {
        return localX + absoluteX to localY + absoluteY
    }
    override val selectionOrder: Int get() = index
}