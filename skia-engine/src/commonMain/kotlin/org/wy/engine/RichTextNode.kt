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

    private val paragraph by memo {
        val maxW = innerSize(Direction.x)
        if (maxW <= 0f || fullText.isEmpty()) null
        else buildParagraph(spans, maxW)
    }

    override val argHeight: LayoutSize
        get() {
            val p = paragraph
            val h = p?.height ?: maxFontSizeInSpans() * 1.4f
            return LayoutSize(h, true)
        }

    private fun maxFontSizeInSpans(): Float {
        var maxFs = 0f
        for (span in spans) {
            maxFs = max(maxFs, span.style.fontSize)
        }
        return max(maxFs, 1f)
    }

    private var anchorIndex by createSignal(-1)
    private var focusIndex by createSignal(-1)

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
        anchorIndex = p.getGlyphPositionAtCoordinate(e.x, e.y)
        focusIndex = anchorIndex
        onMouseDown = true
        e.stopPropagation()
    }

    init {
        val engineGlobal = context.consume(engineGlobalContext)!!
        val d1 = engineGlobal.registerMouseUp { onMouseDown = false }
        val absoluteX by memo { absolutePosition(Direction.x) }
        val absoluteY by memo { absolutePosition(Direction.y) }
        val d2 = engineGlobal.registerMouseMove { e ->
            if (onMouseDown) {
                val p = paragraph ?: return@registerMouseMove
                focusIndex = p.getGlyphPositionAtCoordinate(e.x - absoluteX, e.y - absoluteY)
            }
        }
        context.addDestroy { d1(); d2() }
    }

    override fun draw(canvas: PlatformCanvas) {
        val p = paragraph ?: return

        if (anchorIndex >= 0 && focusIndex >= 0 && anchorIndex != focusIndex) {
            val selStart = min(anchorIndex, focusIndex)
            val selEnd = max(anchorIndex, focusIndex)
            val rects = p.getRectsForRange(selStart, selEnd)
            for (rect in rects) {
                canvas.fillRect(
                    x = rect.left,
                    y = rect.top,
                    w = rect.width,
                    h = rect.height,
                    color = selectionColor
                )
            }
        }

        canvas.drawParagraph(p, 0f, 0f)
        super.draw(canvas)
    }
}
