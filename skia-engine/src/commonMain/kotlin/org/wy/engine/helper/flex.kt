package org.wy.engine.helper

import com.wy.layout.AlignItem
import com.wy.layout.LayoutFun
import com.wy.mve.StateHolder
import org.wy.engine.Direction
import org.wy.engine.Node
import org.wy.engine.RectNode
import org.wy.engine.layout.Flex
import org.wy.engine.layout.LayoutNode


fun StateHolder<Node>.flex(
    direction: Direction = Direction.x,
    alignItem: AlignItem = AlignItem.center,
    gap: Float=0f,
    children: StateHolder<Node>.()-> Unit
): RectNode {
    val flex= object :Flex(){
        override val direction: Direction = direction
        override val alignItem: AlignItem = alignItem
        override val gap: Float = gap
    }
    return object : RectNode(this){
        override fun layout(direction: Direction): LayoutFun<LayoutNode> {
            return flex.layout(direction)
        }

        override fun StateHolder<Node>.buildChildren() {
            children()
        }
    }
}