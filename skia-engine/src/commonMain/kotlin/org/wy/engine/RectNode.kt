package org.wy.engine

import com.wy.layout.Layout
import com.wy.layout.LayoutInsideObject
import com.wy.mve.StateHolder
import org.wy.engine.layout.LayoutNode
import org.wy.lib.GetValue
import org.wy.signal.memo

abstract class RectNode(
    context: StateHolder<Node>
) : NodeI(context), LayoutNode {

    override var layoutIndex: Int = 0
        internal set
        get() {
            parent.children
            return field
        }

    private val _layoutChildren = memo { children.filterIsInstance<LayoutNode>() }
    override val layoutChildren: List<LayoutNode>
        get() = _layoutChildren()


    override val layoutX: GetValue<Layout> = memo {
        layout(Direction.x).createLayout(object : LayoutInsideObject<LayoutNode> {
            override val children: List<LayoutNode>
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

    override fun position(d: Direction): Float {
        val lp = layoutParent
        if (lp != null) {
            try {
                return lp.layoutValue(d).childPosition(layoutIndex)
            } catch (err: Throwable) {

            }
        }
        return 0f
    }

    final override val layoutParent: LayoutNode?
        get() {
            var p: Node? = parent
            while (p != null) {
                if (p is LayoutNode) {
                    return p
                }
                p = p.parent
            }
            return null
        }

    override fun acceptHit(x: Float, y: Float): Boolean {
        val w = outerSize(Direction.x)
        val h = outerSize(Direction.y)
        return x > 0 && x < w && y > 0 && y < h
    }
}