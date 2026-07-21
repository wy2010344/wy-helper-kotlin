package org.wy.engine.layout

import com.wy.layout.Align
import com.wy.layout.AlignItem
import com.wy.layout.DirectionFixBetweenWhenOne
import com.wy.layout.DirectionJustify
import com.wy.layout.FlexObject
import com.wy.layout.LayoutFun
import com.wy.layout.StackObject
import com.wy.layout.absoluteLayout
import org.wy.engine.Direction
import org.wy.engine.LayoutNode
import org.wy.engine.opposite
import org.wy.engine.outerSize


interface FlexParam{
    val direction: Direction
        get() = Direction.y
    val directionJustify: DirectionJustify
        get() = DirectionJustify.grow
    val directionFixBetweenWhenOne: DirectionFixBetweenWhenOne
        get() = DirectionFixBetweenWhenOne.center
    val alignFix: Boolean
        get() = false
    val alignItem: AlignItem
        get() = AlignItem.center
    val gap: Float
        get() = 0f
    val reverse: Boolean
        get() = false
}

interface LayoutDirection {
    val layoutX: LayoutFun<LayoutNode>
    val layoutY: LayoutFun<LayoutNode>
}
class FlexObject(val arg: FlexParam):Flex(){
    override val gap: Float
        get() = arg.gap
    override val reverse: Boolean
        get() = arg.reverse
    override val alignItem: AlignItem
        get() = arg.alignItem
    override val alignFix: Boolean
        get() = arg.alignFix
    override val direction: Direction
        get() = arg.direction
    override val directionFixBetweenWhenOne: DirectionFixBetweenWhenOne
        get() = arg.directionFixBetweenWhenOne
    override val directionJustify: DirectionJustify
        get() = arg.directionJustify

}

val absoluteLayoutDirection:LayoutDirection = object : LayoutDirection{
    override val layoutX: LayoutFun<LayoutNode>
        get() = absoluteLayout()
    override val layoutY: LayoutFun<LayoutNode>
        get() = absoluteLayout()

}
abstract class Flex : FlexObject<LayoutNode> ,LayoutDirection{
    open val direction: Direction = Direction.x
    final override fun index(n: LayoutNode): Int {
        return n.layoutIndex
    }

    final override fun grow(n: LayoutNode): Float {
        return n.grow
    }

    final override fun outerSize(n: LayoutNode): Float {
        return n.outerSize(direction)
    }

    override val layoutX: LayoutFun<LayoutNode>
        get() = layout(Direction.x)
    override val layoutY: LayoutFun<LayoutNode>
        get() = layout(Direction.y)

    fun layout(direction: Direction) = when (this.direction == direction) {
        true -> this
        false -> cross
    }
    open val alignItem: AlignItem = AlignItem.center

    open val alignFix: Boolean=false
    private val cross = object : StackObject<LayoutNode> {
        override val alignItem: AlignItem
            get() = this@Flex.alignItem

        override val alignFix: Boolean
            get() = this@Flex.alignFix

        override fun align(n: LayoutNode): Align? {
            return n.align
        }

        override fun outerSize(n: LayoutNode): Float {
            return n.outerSize(direction.opposite)
        }
    }
}
