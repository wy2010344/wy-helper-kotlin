package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.signal.OneSetStoreRef
import org.wy.signal.createLateSignal
import kotlin.math.max


class Scroll(
    val container: LayoutNode,
    val direction: Direction = Direction.y,
    value: OneSetStoreRef<Float> = createLateSignal(0f)
) {
    private val setValue = value.getOnlySet()
    private val getValue = value::get

    var value
        get() = getValue().coerceIn(0f, container.maxScroll(direction))
        set(value) {
            setValue(value)
        }

    fun scroll(delta: Float): Float {
        val next = (value + delta).coerceIn(0f, container.maxScroll(direction))
        val realDelta = next - value
        value = next
        return realDelta
    }
}

fun StateHolder<Node,List<Node>>.registerScroll(scroll: Scroll) {
    val engineGlobal = consume(engineGlobalContext)!!
    val d0 = engineGlobal.registerMouseWheel {
        if (scroll.container.absoluteInInner(it.x, it.y)) {
            scroll.scroll(it.delta)
        }
    }
    addDestroy(d0)
}

/**
 * 最大可滚动
 */
fun LayoutNode.maxScroll(direction: Direction): Float {
    return max(0f, contentSize(direction) - innerSize(direction))
}


/**
 * length 滚动的长度
 * return <尺寸，位置>
 */
fun Scroll.scrollBarSize(
    direction: Direction,
    length: Float = 0f
): ScrollBarCalculate? {
    val length = if (length > 0) length else container.innerSize(direction)
    val v = container.innerSize(direction)
    val c = container.contentSize(direction)
    val m = container.maxScroll(direction)
    if (m > 0) {
        //
        val thumb = max(20f, length * v / c)
        //最大偏移*偏移比例
        val maxOffset = length - thumb
        val move = maxOffset * value / m
        return ScrollBarCalculate(thumb, move, m, maxOffset)
    }
    return null
}


/**
 * 内容区尺寸
 */
fun LayoutNode.contentSize(direction: Direction): Float {
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

open class ScrollContent(context: StateHolder<Node,List<Node>>) : RectNode(context) {
    override fun acceptClip(x: Float, y: Float): Boolean {
        val sn = layoutParent!!
        val left = sn.paddingInlineStart
        val top = sn.paddingBlockStart
        val right = left + sn.innerSize(Direction.x)
        val bottom = top + sn.innerSize(Direction.y)
        return x > left && x < right && y > top && y < bottom
    }
}