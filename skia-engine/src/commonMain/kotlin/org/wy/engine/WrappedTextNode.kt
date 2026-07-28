package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.memo
import org.wy.signal.setValue
import kotlin.math.max
import kotlin.math.min

enum class WordBreak {
    PHRASE, BREAK_WORD, ANY_CHAR
}

open class WrappedTextNode(
    context: StateHolder<Node>
) : RectNode(context) {
    open val text: String = ""
    open val fontFamily: String? = null
    open val fontSize: Float = 16f
    open val fontWeight: Int = 400
    open val color: ColorInt = rgba(0, 0, 0)
    open val selectionColor: ColorInt = rgba(0, 100, 200, 60)
    open val lineHeight: Float
        get() = fontSize * 1.4f

    open val wordBreak: WordBreak = WordBreak.BREAK_WORD
    open val locale: String? = null

    protected var anchorIndex by createSignal(-1)
    protected var focusIndex by createSignal(-1)

    val selectionText: String?
        get() {
            if (anchorIndex < 0 || focusIndex < 0 || anchorIndex == focusIndex) return null
            val start = min(anchorIndex, focusIndex)
            val end = max(anchorIndex, focusIndex)
            return text.substring(start, end)
        }

    val paragraph = memo {
        if (text.isEmpty()) return@memo null
        buildParagraph(
            text = text,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontSize = fontSize,
            fontColor = color,
            lineHeight = lineHeight,
            maxWidth = innerSize(Direction.x).let { if (it > 0f) it else Float.MAX_VALUE },
            wordBreak = wordBreak
        )
    }

    override val argWidth: LayoutSize
        get() {
            val p = paragraph() ?: return LayoutSize(0f, true)
            return LayoutSize(p.longestLine.let { if (it > 0f) it else 0f }, true)
        }

    override val argHeight: LayoutSize
        get() {
            val p = paragraph() ?: return LayoutSize(lineHeight, true)
            return LayoutSize(p.height, true)
        }

    protected fun charAt(x: Float, y: Float): Int {
        val p = paragraph() ?: return 0
        return p.getGlyphPositionAtCoordinate(x, y)
    }

    private var onMouseDown = false
    override fun mouseDown(e: MouseEvent) {
        anchorIndex = charAt(e.x, e.y)
        focusIndex = anchorIndex
        onMouseDown = true
        e.stopPropagation()
    }

    init {
        val engineGlobal = context.consume(engineGlobalContext)!!
        val d1 = engineGlobal.registerMouseUp {
            onMouseDown = false
        }
        val absoluteX = memo { absolutePosition(Direction.x) }
        val absoluteY = memo { absolutePosition(Direction.y) }
        val d2 = engineGlobal.registerMouseMove {
            if (onMouseDown) {
                focusIndex = charAt(it.x - absoluteX(), it.y - absoluteY())
            }
        }
        context.addDestroy {
            d1()
            d2()
        }
    }

    override fun draw(canvas: PlatformCanvas) {
        val p = paragraph() ?: return

        if (anchorIndex >= 0 && focusIndex >= 0 && anchorIndex != focusIndex) {
            val start = min(anchorIndex, focusIndex)
            val end = max(anchorIndex, focusIndex)
            val rects = p.getRectsForRange(start, end)
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
