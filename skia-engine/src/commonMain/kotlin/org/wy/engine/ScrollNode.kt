package org.wy.engine

import com.wy.layout.LayoutFun
import com.wy.layout.absoluteLayout
import com.wy.mve.StateHolder
import org.wy.engine.layout.LayoutNode
import org.wy.engine.layout.LayoutSize
import org.wy.engine.layout.StartEnd
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.memo
import org.wy.signal.setValue
import kotlin.math.max

open class ScrollNode(
    context: StateHolder<Node>
) : RectNode(context) {
    private var scrollX by createSignal(0f)
    private var scrollY by createSignal(0f)

    fun scroll(direction: Direction): Float = when (direction) {
        Direction.x -> scrollX
        Direction.y -> scrollY
    }

    override fun size(direction: Direction): LayoutSize {
        return sizeFromParent(direction)
    }
    /**
     * 内容区尺寸
     */
    fun contentSize(direction: Direction): Float {
        children.forEach {
            if (it is ContentClass) {
                return it.outerSize(direction)
            }
        }
        return 0f
    }

    /**
     * 最大可滚动
     */
    fun maxScroll(direction: Direction): Float {
        return contentSize(direction) - innerSize(direction)
    }


    override fun draw(canvas: PlatformCanvas) {
        drawSelf(canvas)
        children.forEach {
            canvas.save()
            if (it is ContentClass) {
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
     * length 滚动的长度
     * return <尺寸，位置>
     */
    fun scrollBarSize(
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

    private fun absX() = absolutePosition(Direction.x)
    private fun absY() = absolutePosition(Direction.y)


    /**
     * 滚动一定偏移
     */
    open fun scroll(delta: Float): Float {
        val next = (scrollY + delta).coerceIn(0f, maxScroll(Direction.y))
        val realDelta = next - scrollY
        scrollY = next
        return realDelta
    }

    init {
        val engineGlobal = context.consume(engineGlobalContext)!!
        val d0 = engineGlobal.registerMouseWheel {
            if (inRange(absX() + innerStart(Direction.x), it.x, innerSize(Direction.x))
                && inRange(absY() + innerStart(Direction.y), it.y, innerSize(Direction.y))
            ) {
                scroll(it.delta)
            }
        }
        context.addDestroy { d0(); }
    }

    final override fun layout(direction: Direction): LayoutFun<LayoutNode> {
        return absoluteLayout()
    }

    open fun provideContentSize(direction: Direction): LayoutSize?{
        return null
    }

    open fun contentLayout(direction: Direction): LayoutFun<LayoutNode> {
        return absoluteLayout()
    }

    open fun contentPadding(direction: Direction, startEnd: StartEnd): Float {
        return 0f
    }

    protected open fun StateHolder<Node>.buildContentChildren() {}
    override fun StateHolder<Node>.buildChildren() {
        buildScrollNode()
    }

    private var callTime = 0
    fun StateHolder<Node>.buildScrollNode() {
        if (callTime > 0) {
            throw Error("只允许调用一次")
        }
        callTime = 1
        object : ContentClass(this) {
            override fun provideSize(direction: Direction): Boolean {
                return false
            }
            override fun layout(direction: Direction): LayoutFun<LayoutNode> {
                return contentLayout(direction)
            }

            override fun padding(direction: Direction, startEnd: StartEnd): Float {
                return contentPadding(direction, startEnd)
            }

            override fun position(d: Direction): Float = this@ScrollNode.innerStart(d) + when (d) {
                Direction.x -> -this@ScrollNode.scrollX
                Direction.y -> -this@ScrollNode.scrollY
            }

            override fun size(direction: Direction): LayoutSize {
                return provideContentSize(direction)?:sizeFromChildren(direction)
            }

            override fun StateHolder<Node>.buildChildren() {
                this.buildContentChildren()
            }
        }
    }

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
    fun scrollToMove(delta: Float): Float{
        return delta *maxOffset / maxScroll
    }
}

private abstract class ContentClass(context: StateHolder<Node>) : RectNode(context)