package org.wy.engine

import com.wy.layout.Layout
import com.wy.layout.LayoutInsideObject
import com.wy.mve.renderRoot
import org.wy.engine.layout.LayoutNode
import org.wy.engine.layout.LayoutSize
import org.wy.lib.EmptyFun
import org.wy.lib.GetValue
import org.wy.signal.TrackSignal
import org.wy.signal.memo

abstract class Renderer : Node, LayoutNode {

    final override val parent: Node? = null
    abstract val width: Float
    abstract val height: Float

    override val layoutParent: LayoutNode? = null
    override val layoutIndex: Int = 0

    final override fun size(direction: Direction): LayoutSize = LayoutSize(
        when (direction) {
            Direction.x -> width
            Direction.y -> height
        }, false
    )


    override val layoutX: GetValue<Layout> = memo {
        layout(Direction.x).createLayout(object : LayoutInsideObject<LayoutNode> {
            override val children: List<LayoutNode>
                @Suppress("SuspiciousIndentation")
                get() = layoutChildren
            override val innerSize: Float
                get() = innerSize(Direction.x)
        })
    }

    override val layoutY: GetValue<Layout> = memo {
        layout(Direction.y).createLayout(object : LayoutInsideObject<LayoutNode> {
            override val children: List<LayoutNode>
                get() = layoutChildren
            override val innerSize: Float
                get() = innerSize(Direction.y)
        })
    }


    private val _layoutChildren = memo {
        children.filterIsInstance<LayoutNode>()
    }
    override val layoutChildren: List<LayoutNode>
        get() = _layoutChildren()


    private val moveList = mutableSetOf<MouseCallback>()
    private val upList = mutableSetOf<MouseCallback>()
    private val wheelList = mutableSetOf<WheelCallback>()

    private val state = renderRoot<Node>(this@Renderer, ::collectIndex) {
        provide(engineGlobalContext, object : EngineGlobal {
            override fun registerMouseMove(callback: MouseCallback): EmptyFun {
                moveList.add(callback)
                return {
                    moveList.remove(callback)
                }
            }

            override fun registerMouseUp(callback: MouseCallback): EmptyFun {
                upList.add(callback)
                return {
                    upList.remove(callback)
                }
            }

            override fun registerMouseWheel(callback: WheelCallback): EmptyFun {
                wheelList.add(callback)
                return {
                    wheelList.remove(callback)
                }
            }
        })
        buildChildren()
    }

    fun destroy() {
        moveList.clear()
        upList.clear()
        wheelList.clear()
        state.destroy()
    }


    override val children: List<Node>
        get() = state.target()

    abstract fun frameCallback()
    private var scheduled = false
    private val signal = object : TrackSignal<Unit>() {
        override fun get(old: Unit?, inited: Boolean) {
            scheduled = true
            frameCallback()
        }
    }

    fun render(canvas: PlatformCanvas) {
        canvas.clear(rgba(255, 255, 255))
        signal.collect {
            width
            height
            draw(canvas)
        }
        scheduled = false
    }

    private fun mouseEventOf(x: Float, y: Float, type: MouseEventEnum) {
        var nodeWithPosition = hitest(x, y)
        if (nodeWithPosition == null) {
            return
        }
        var list = mutableListOf<NodeWithPosition>()
        //这里检查时，如果直接事件发生，造成状态改变，再向上查询时会出错。
        while (nodeWithPosition != null) {
            //捕获
            val e = MouseEvent(nodeWithPosition.x, nodeWithPosition.y)
            sendMouseEvent(nodeWithPosition.node, type, e, true)
            if (e.stoppedProgression) {
                return
            }
            list.add(nodeWithPosition)
            nodeWithPosition = nodeWithPosition.next
        }
        list.asReversed().forEach {
            //冒泡
            val e = MouseEvent(it.x, it.y)
            sendMouseEvent(it.node, type, e, false)
            it.node.mouseClick(e)
            if (e.stoppedProgression) {
                return
            }
        }
    }

    fun mouseClick(x: Float, y: Float) {
        mouseEventOf(x, y, MouseEventEnum.click)
    }

    private fun sendMouseEvent(node: Node, type: MouseEventEnum, e: MouseEvent, capture: Boolean) {
        when (type) {
            MouseEventEnum.click -> if (capture) node.mouseClickCapture(e) else node.mouseClick(e)
            MouseEventEnum.down -> if (capture) node.mouseDownCapture(e) else node.mouseDown(e)
            MouseEventEnum.up -> if (capture) node.mouseUpCapture(e) else node.mouseUp(e)
        }
    }

    fun mouseDown(x: Float, y: Float) {
        mouseEventOf(x, y, MouseEventEnum.down)
    }

    fun mouseUp(x: Float, y: Float) {
        mouseEventOf(x, y, MouseEventEnum.up)
        upList.forEach { it(x, y) }
    }

    fun mouseMove(x: Float, y: Float) {
        moveList.forEach { it(x, y) }
    }

    fun mouseWheel(x: Float, y: Float, deltaX: Float, deltaY: Float) {
        wheelList.forEach { it(x, y, deltaX, deltaY) }
    }
}

private enum class MouseEventEnum {
    click, down, up
}
