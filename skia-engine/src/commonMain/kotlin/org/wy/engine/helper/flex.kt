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
import org.wy.lib.Either
import org.wy.lib.Left
import org.wy.lib.Right

/**
 * 可能是内部撑开，可能不是
 */
fun StateHolder<Node>.flex(
    direction: Direction = Direction.x,
    alignItem: AlignItem = AlignItem.center,
    gap: Float = 0f,
    width: Either<LayoutSize, Pair<RectNode.()-> LayoutSize, Boolean>>?=null,
    height: Either<LayoutSize, Pair<RectNode.()-> LayoutSize, Boolean>>?=null,
    children: StateHolder<Node>.(RectNode) -> Unit,
): RectNode {
    val flex = object : Flex() {
        override val direction: Direction = direction
        override val alignItem: AlignItem = alignItem
        override val gap: Float = gap
    }

    return object : RectNode(this) {
        override fun toString(): String {
            return "Flex"
        }

        private fun getSize(d:Direction,s:Either<LayoutSize, Pair<RectNode.()-> LayoutSize, Boolean>>?):LayoutSize{
            if(s==null){
                return sizeFromParent(d)
            }
           return when(s){
                is Left -> s.value
                is Right ->{
                    val first=s.value.first
                    return first()
                }
            }
        }
        override fun size(direction: Direction): LayoutSize = when(direction){
            Direction.x -> getSize(direction,width)
            Direction.y -> getSize(direction,height)
        }
        private fun getSizeRelay(d:Direction,s:Either<LayoutSize, Pair<RectNode.()-> LayoutSize, Boolean>>?): Boolean{
            if(s==null){
                return false
            }
            return when(s){
                is Left -> false
                is Right -> s.value.second
            }
        }

        override fun sizeRelayChildren(direction: Direction): Boolean =when(direction){
            Direction.x -> getSizeRelay(direction,width)
            Direction.y -> getSizeRelay(direction,height)
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