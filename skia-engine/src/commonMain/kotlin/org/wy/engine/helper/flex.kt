package org.wy.engine.helper

import com.wy.layout.AlignItem
import com.wy.layout.LayoutFun
import com.wy.mve.StateHolder
import org.wy.engine.Direction
import org.wy.engine.Node
import org.wy.engine.RectNode
import org.wy.engine.layout.Flex
import org.wy.engine.layout.LayoutNode
import org.wy.engine.layout.LayoutSize


/**
 * 可能是内部撑开，可能不是
 */
fun StateHolder<Node>.flex(
    direction: Direction = Direction.x,
    alignItem: AlignItem = AlignItem.center,
    gap: Float = 0f,
    size: RectNode.(direction: Direction) -> LayoutSize? = { null },
    provideSize: RectNode.(direction: Direction) -> Boolean = { true },
    children: StateHolder<Node>.(RectNode) -> Unit,
): RectNode {
    val pSize = size
    val ppSize = provideSize
    val flex = object : Flex() {
        override val direction: Direction = direction
        override val alignItem: AlignItem = alignItem
        override val gap: Float = gap
    }

    return object : RectNode(this) {
        override fun toString(): String {
            return "Flex"
        }

        override fun size(direction: Direction): LayoutSize {
            return pSize(direction) ?: sizeFromParent(direction)
        }

        override fun provideSize(direction: Direction): Boolean {
            if (pSize == null) {
                return true
            }
            return ppSize(direction)
        }

        override fun layout(direction: Direction): LayoutFun<LayoutNode> {
            return flex.layout(direction)
        }

        private fun getThis(): RectNode {
            return this
        }

        override fun StateHolder<Node>.buildChildren() {
            children(getThis())
        }
    }
}