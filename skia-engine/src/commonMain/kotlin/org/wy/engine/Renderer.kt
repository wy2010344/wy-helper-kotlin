package org.wy.engine

import com.wy.layout.Layout
import com.wy.layout.LayoutInsideObject
import com.wy.mve.renderRoot
import org.wy.engine.layout.LayoutNode
import org.wy.engine.layout.LayoutSize
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
                get() =layoutChildren
            override val innerSizeForLayout: Float
                get() = innerSize(Direction.x)
        })
    }

    override val layoutY: GetValue<Layout> = memo {
        layout(Direction.y).createLayout(object : LayoutInsideObject<LayoutNode> {
            override val children: List<LayoutNode>
                get() = layoutChildren
            override val innerSizeForLayout: Float
                get() = innerSize(Direction.y)
        })
    }


    private val _layoutChildren = memo {
        children.filterIsInstance<LayoutNode>()
    }
    override val layoutChildren: List<LayoutNode>
        get() = _layoutChildren()
    private val state = renderRoot<Node>(this, ::collectIndex) {
        buildChildren()
    }

    fun destroy() {
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

    fun mouseClick(x: Float, y: Float) {
        var nodeWithPosition = hitest(x, y)
        if(nodeWithPosition==null){
            return
        }
        var list=mutableListOf<NodeWithPosition>()
        //这里检查时，如果直接事件发生，造成状态改变，再向上查询时会出错。
        while (nodeWithPosition!=null){
            //捕获
            val e= MouseEvent(nodeWithPosition.x,nodeWithPosition.y)
            nodeWithPosition.node.mouseClickCapture(e)
            if(e.stoppedProgression){
                return
            }
            list.add(nodeWithPosition)
            nodeWithPosition=nodeWithPosition.next
        }
        list.asReversed().forEach {
            //冒泡
            val e= MouseEvent(it.x,it.y)
            it.node.mouseClick(e)
            if(e.stoppedProgression){
                return
            }
        }
    }
}
