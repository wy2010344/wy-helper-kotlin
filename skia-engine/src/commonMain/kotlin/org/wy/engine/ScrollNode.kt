package org.wy.engine

import com.wy.layout.Layout
import com.wy.layout.LayoutFun
import com.wy.layout.absoluteLayout
import com.wy.mve.StateHolder
import org.wy.engine.contentSize
import org.wy.engine.innerSize
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.scroll
import org.wy.lib.GetValue
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue
import kotlin.math.max

open class ScrollNode(
    context: StateHolder<Node>
) : RectNode(context) {
    var scrollX by createSignal(0f)
        private set
    var scrollY by createSignal(0f)
        private set


    override fun draw(canvas: PlatformCanvas) {
        drawChildren(canvas)
    }

    /**
     * 滚动一定偏移
     */
    fun scroll(delta: Float): Float {
        val next = (scrollY + delta).coerceIn(0f, maxScroll(Direction.y))
        val realDelta = next - scrollY
        scrollY = next
        return realDelta
    }

    init {
        val engineGlobal = context.consume(engineGlobalContext)!!
        val d0 = engineGlobal.registerMouseWheel {
            if (inRange(absoluteX + paddingInlineStart, it.x, innerSize(Direction.x))
                && inRange(absoluteY + paddingBlockStart, it.y, innerSize(Direction.y))
            ) {
                scroll(it.delta)
            }
        }
        context.addDestroy(d0)
    }
}

fun ScrollNode.drawChildren(canvas: PlatformCanvas){

    children.forEach {
        canvas.save()
        if (it is ScrollContent) {
            canvas.clipRect(
                padding(Direction.x, StartEnd.start),
                padding(Direction.y, StartEnd.start),
                innerSize(Direction.x),
                innerSize(Direction.y)
            )
        }
        canvas.translate(it.position(Direction.x), it.position(Direction.y))
        it.draw(canvas)
        canvas.restore()
    }
}
/**
 * 最大可滚动
 */
fun ScrollNode.maxScroll(direction: Direction): Float {
    return contentSize(direction) - innerSize(direction)
}

fun ScrollNode.scroll(direction: Direction): Float = when (direction) {
    Direction.x -> scrollX
    Direction.y -> scrollY
}


/**
 * length 滚动的长度
 * return <尺寸，位置>
 */
fun ScrollNode.scrollBarSize(
    direction: Direction,
    length: Float = 0f
): ScrollBarCalculate? {
    val length = if (length > 0) length else innerSize(direction)
    val v = innerSize(direction)
    val c = contentSize(direction)
    val m = maxScroll(direction)
    if (m > 0) {
        //
        val thumb = max(20f, length * v / c)
        //最大偏移*偏移比例
        val maxOffset = length - thumb
        val move = maxOffset * scroll(direction) / m
        return ScrollBarCalculate(thumb, move, m, maxOffset)
    }
    return null
}


/**
 * 内容区尺寸
 */
fun ScrollNode.contentSize(direction: Direction): Float {
    children.forEach {
        if (it is ScrollContent) {
            return it.outerSize(direction)
        }
    }
    return 0f
}

class ScrollBarCalculate(
    val size: Float,
    val offset: Float,
    val maxScroll: Float,
    val maxOffset: Float
) {
    fun moveToScroll(delta: Float): Float {
        return delta * maxScroll / maxOffset
    }

    fun scrollToMove(delta: Float): Float {
        return delta * maxOffset / maxScroll
    }
}

open class ScrollContent(context: StateHolder<Node>) : RectNode(context) {
    val scrollNode: ScrollNode

    init {
        if (parent !is ScrollNode) {
            throw Error("必须在ScrollNode下")
        }
        scrollNode = parent
    }

    final override val x: Float
        get() = -scrollNode.scrollX
    final override val y: Float
        get() = -scrollNode.scrollY
}