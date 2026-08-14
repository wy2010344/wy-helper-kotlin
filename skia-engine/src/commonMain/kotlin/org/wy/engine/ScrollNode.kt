package org.wy.engine

import com.wy.mve.Context
import com.wy.mve.StateHolder
import org.wy.signal.OneSetStoreRef
import org.wy.signal.createLateSignal
import kotlin.math.max

val scrollXContext = Context<Scroll?>(null)
val scrollYContext = Context<Scroll?>(null)

fun scrollContext(direction: Direction): Context<Scroll?> =
    if (direction == Direction.x) scrollXContext else scrollYContext

class Scroll(
    val container: LayoutNode,
    val direction: Direction = Direction.y
) {
    private val ref: OneSetStoreRef<Float> = createLateSignal(0f)
    private val setValue = ref.getOnlySet()
    private val getValue = ref::get

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
    scroll.container.scrollCtrl = scroll
    val engineGlobal = consume(engineGlobalContext)!!
    val d0 = engineGlobal.registerMouseWheel {
        if (scroll.container.absoluteInInner(it.x, it.y)) {
            val consumed = scroll.scroll(it.delta)
            val remaining = it.delta - consumed
            if (remaining != 0f) {
                val parentScroll = consume(scrollContext(scroll.direction))
                if (parentScroll != null && parentScroll != scroll) {
                    parentScroll.scroll(remaining)
                }
            }
        }
    }
    addDestroy(d0)
}

fun LayoutNode.maxScroll(direction: Direction): Float {
    return max(0f, contentSize(direction) - innerSize(direction))
}

fun Scroll.scrollBarSize(
    direction: Direction,
    length: Float = 0f
): ScrollBarCalculate? {
    val length = if (length > 0) length else container.innerSize(direction)
    val v = container.innerSize(direction)
    val c = container.contentSize(direction)
    val m = container.maxScroll(direction)
    if (m > 0) {
        val thumb = max(20f, length * v / c)
        val maxOffset = length - thumb
        val move = maxOffset * value / m
        return ScrollBarCalculate(thumb, move, m, maxOffset)
    }
    return null
}

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

    init {
        val scroll = layoutParent?.scrollCtrl
        if (scroll != null && context != null) {
            context.provide(scrollContext(scroll.direction), scroll)
        }
    }

    private fun visibleRect(): RectF? {
        val sn = layoutParent ?: return null
        val left = sn.paddingInlineStart
        val top = sn.paddingBlockStart
        return RectF(left, top, left + sn.innerSize(Direction.x), top + sn.innerSize(Direction.y))
    }

    override fun clipRect(): RectF? = visibleRect()

    override fun acceptClip(x: Float, y: Float): Boolean {
        val r = visibleRect() ?: return true
        return x >= r.left && x <= r.right && y >= r.top && y <= r.bottom
    }
}