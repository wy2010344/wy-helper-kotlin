package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.engine.layout.LayoutSize
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.memo
import org.wy.signal.setValue
import kotlin.math.max
import kotlin.math.min

open class TextNode(
    context: StateHolder<Node>
) : RectNode(context) {
    open val text: String = ""
    open val fontFamily: String? = null
    open val fontSize: Float = 16f

    open val lightHeight: Float
        get() = fontSize * 1.4f
    open val fontWeight = 400

    open val color = rgba(0, 0, 0)
    open val selectionColor = rgba(0, 100, 200, 60)

    private var anchorIndex by createSignal(-1)
    private var focusIndex by createSignal(-1)

    val selectionText: String?
        get() {
            if (anchorIndex < 0 || focusIndex < 0 || anchorIndex == focusIndex) return null
            val start = min(anchorIndex, focusIndex)
            val end = max(anchorIndex, focusIndex)
            return text.substring(start, end)
        }

    private val charPositions = memo {
        if (text.isEmpty()) return@memo listOf(0f)
        val positions = mutableListOf<Float>()
        for (i in text.indices) {
            positions.add(
                PlatformCanvas.measureText(
                    text.substring(0, i + 1),
                    fontFamily,
                    fontWeight,
                    fontSize
                )
            )
        }
        positions
    }

    private fun charAt(x: Float): Int {
        val positions = charPositions()
        for (i in positions.indices) {
            if (positions[i] > x) return i
        }
        return positions.size
    }

    override fun size(direction: Direction): LayoutSize {
        val positions = charPositions()
        return when (direction) {
            Direction.x -> LayoutSize(positions.lastOrNull() ?: 0f, true)
            Direction.y -> LayoutSize(fontSize * 1.4f, true)
        }
    }

    private var onMouseDown = false
    override fun mouseDown(e: MouseEvent) {
        anchorIndex = charAt(e.x)
        focusIndex = anchorIndex
        onMouseDown = true
        e.stopPropagation()
    }

    init {
        val engineGlobal = context.consume(engineGlobalContext)!!
        val d1 = engineGlobal.registerMouseUp { x, y ->
            onMouseDown = false
        }
        val absoluteX=memo { absolutePosition(Direction.x) }
        val d2 = engineGlobal.registerMouseMove { x, y ->
            if (onMouseDown) {
                focusIndex = charAt(x-absoluteX())
            }
        }
        context.addDestroy {
            d1()
            d2()
        }
    }

    override fun drawSelf(canvas: PlatformCanvas) {
        if (text.isEmpty()) return

        val positions = charPositions()
        val h = lightHeight
        if (anchorIndex >= 0 && focusIndex >= 0 && anchorIndex != focusIndex) {
            val start = min(anchorIndex, focusIndex)
            val end = max(anchorIndex, focusIndex)
            val leftX = if (start == 0) 0f else positions[start - 1]
            val rightX = positions[end - 1]
            canvas.drawRect(x = leftX, y = 0f, w = rightX - leftX, h = h, color = selectionColor)
        }
        canvas.drawText(
            text,
            0f,
            fontSize,
            fontFamily,
            fontWeight,
            fontSize = fontSize,
            color = color
        )
    }
}
