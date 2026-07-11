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

    /**
     * 内容区尺寸
     */
    fun contentSize(direction: Direction): Float {
        val pane = children.firstOrNull() as? RectNode ?: return 0f
        return pane.outerSize(direction)
    }

    /**
     * 最大可滚动
     */
    fun maxScroll(direction: Direction): Float {
        return contentSize(direction) - innerSize(direction)
    }


    override fun draw(canvas: PlatformCanvas) {
        drawSelf(canvas)
        children.forEachIndexed { index, it ->
            canvas.save()
            if (index == 0) {
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
    ): Pair<Float, Float>? {
        val length = if (length > 0) length else innerSize(direction)
        val v = innerSize(direction)
        val c = contentSize(direction)
        val m = maxScroll(direction)
        if (m > 0) {
            //
            val thumb = max(20f, length * v / c)
            //最大偏移*偏移比例
            val move = (length - thumb) * scroll(direction) / m
            return thumb to move
        }
        return null
    }

    private fun absX() = absolutePosition(Direction.x)
    private fun absY() = absolutePosition(Direction.y)


    /**
     * 滚动一定偏移
     */
    open fun scroll(delta: Float) {
        scrollY = (scrollY + delta).coerceIn(0f, maxScroll(Direction.y))
    }

    init {
        val engineGlobal = context.consume(engineGlobalContext)!!
        val d0 = engineGlobal.registerMouseWheel { x, y, delta ->
            if (inRange(absX() + innerStart(Direction.x), x, innerSize(Direction.x))
                && inRange(absY() + innerStart(Direction.y), y, innerSize(Direction.y))
            ) {
                scroll(delta)
            }
        }
        context.addDestroy { d0(); }
    }

    final override fun layout(direction: Direction): LayoutFun<LayoutNode> {
        return absoluteLayout()
    }

    open val contentSize: (LayoutNode.(direction: Direction) -> LayoutSize)? = null

    open fun contentLayout(direction: Direction): LayoutFun<LayoutNode> {
        return absoluteLayout()
    }

    open fun contentPadding(direction: Direction, startEnd: StartEnd): Float {
        return 0f
    }

    protected open fun StateHolder<Node>.buildContentChildren() {}

    open fun StateHolder<Node>.buildOtherChildren() {}

    final override fun StateHolder<Node>.buildChildren() {
        object : RectNode(this) {
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
                contentSize?.let { return it.invoke(this, direction) }
                return super.size(direction)
            }

            override fun StateHolder<Node>.buildChildren() {
                this.buildContentChildren()
            }
        }
        this.buildOtherChildren()
    }
}
