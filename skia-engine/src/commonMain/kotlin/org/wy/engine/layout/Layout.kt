package org.wy.engine.layout

import com.wy.layout.Align
import com.wy.layout.AlignItem
import com.wy.layout.FlexObject
import com.wy.layout.StackObject
import org.wy.engine.Direction
import org.wy.engine.opposite

abstract class Flex : FlexObject<LayoutNode> {
    open val direction: Direction = Direction.x
    override fun index(n: LayoutNode): Int {
        return n.layoutIndex
    }

    override fun grow(n: LayoutNode): Float {
        return n.grow
    }

    override fun outerSizeForParentLayout(n: LayoutNode): Float {
        return n.outerSize(direction)
    }

    fun layout(n: Direction) = when (n == direction) {
        true -> this
        false -> cross
    }

    open val alignItem: AlignItem = AlignItem.center

    private val cross = object : StackObject<LayoutNode> {
        override val alignItem: AlignItem
            get() = this@Flex.alignItem

        override fun align(n: LayoutNode): Align? {
            return n.align
        }

        override fun outerSizeFoorParentLayout(n: LayoutNode): Float {
            return n.outerSize(direction.opposite)
        }
    }
}
