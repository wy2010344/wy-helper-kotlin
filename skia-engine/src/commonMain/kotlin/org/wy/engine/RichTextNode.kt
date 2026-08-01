package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.lib.getValue
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.memo
import org.wy.signal.setValue
import kotlin.math.max
import kotlin.math.min

open class RichTextNode(
    context: StateHolder<Node>
) : RectNode(context) {

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

    protected var anchorIndex by createSignal(-1)
    protected var focusIndex by createSignal(-1)

    val selectionText by memo {
        if (anchorIndex < 0 || focusIndex < 0 || anchorIndex == focusIndex) return@memo null
        val text = fullText
        val s = min(anchorIndex, focusIndex)
        val e = max(anchorIndex, focusIndex)
        text.substring(s, e)
    }

    private var onMouseDown = false

    override fun mouseDown(e: MouseEvent) {
        val p = paragraph ?: return
        anchorIndex =
            p.getGlyphPositionAtCoordinate(e.x - paddingInlineStart, e.y - paddingBlockStart)
        focusIndex = anchorIndex
        onMouseDown = true
    }

    init {
        val engineGlobal = context.consume(engineGlobalContext)!!
        val d1 = engineGlobal.registerMouseUp { onMouseDown = false }
        val absoluteX by memo { absolutePosition(Direction.x) }
        val absoluteY by memo { absolutePosition(Direction.y) }
        val d2 = engineGlobal.registerMouseMove { e ->
            if (onMouseDown) {
                val p = paragraph ?: return@registerMouseMove
                focusIndex = p.getGlyphPositionAtCoordinate(
                    e.x - absoluteX - paddingInlineStart,
                    e.y - absoluteY - paddingBlockStart
                )
            }
        }
        context.addDestroy { d1(); d2() }
    }

    override fun draw(canvas: PlatformCanvas) {
        val p = paragraph ?: return

        if (anchorIndex >= 0 && focusIndex >= 0 && anchorIndex != focusIndex) {
            val selStart = min(anchorIndex, focusIndex)
            val selEnd = max(anchorIndex, focusIndex)
            val rects = p.getRectsForRange(selStart, selEnd, RectStyle.TIGHT)
            for (rect in rects) {
                canvas.fillRect(
                    x = rect.left+paddingInlineStart,
                    y = rect.top+paddingBlockStart,
                    w = rect.width,
                    h = rect.height,
                    color = selectionColor
                )
            }
        }

        canvas.drawParagraph(p, paddingInlineStart, paddingBlockStart)
        super.draw(canvas)
    }
}
