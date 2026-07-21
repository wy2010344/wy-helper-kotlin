package org.wy.engine

import com.wy.layout.Layout
import com.wy.layout.LayoutError
import com.wy.layout.LayoutFun
import com.wy.mve.StateHolder
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.absoluteLayoutDirection
import org.wy.lib.GetValue
import org.wy.signal.memo

abstract class RectNode(
    context: StateHolder<Node>
) : Node(context), LayoutNode {

    override var layoutIndex: Int = 0
        internal set
        get() {
            layoutParent?.layoutChildren
            return field
        }

    val getLayoutChildren = memo { children.filterIsInstance<LayoutNode>() }
    override val layoutChildren: List<LayoutNode>
        get() = getLayoutChildren()

    override fun argPosition(direction: Direction): Float {
        val lp = layoutParent
        if (lp != null) {
            try {
                return lp.layoutValue(direction).childPosition(layoutIndex)
            } catch (err: LayoutError) {

            }
        }
        return 0f
    }

    override fun argSize(direction: Direction): LayoutSize {
        val x = layoutValue(direction)
        if (x.allowSizeFromChildren) {
            return LayoutSize(x.sizeFromChildren, true)
        }
        return sizeFromParent(direction)
    }

    final override val layoutParent: LayoutNode? = run {
        var p: Node? = parent
        while (p != null) {
            if (p is LayoutNode) {
                return@run p
            }
            p = p.parent
        }
        return@run null
    }

    override fun acceptHit(x: Float, y: Float): Boolean {
        val w = outerSize(Direction.x)
        val h = outerSize(Direction.y)
        return x > 0 && x < w && y > 0 && y < h
    }

    final override val layoutX: GetValue<Layout> = createLayout(Direction.x)
    final override val layoutY: GetValue<Layout> = createLayout(Direction.y)
}