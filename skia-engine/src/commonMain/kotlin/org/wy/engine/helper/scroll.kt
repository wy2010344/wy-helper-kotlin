package org.wy.engine.helper

import com.wy.layout.AlignItem
import com.wy.layout.LayoutFun
import com.wy.mve.StateHolder
import org.wy.engine.Direction
import org.wy.engine.Node
import org.wy.engine.PlatformCanvas
import org.wy.engine.ScrollNode
import org.wy.engine.layout.Flex
import org.wy.engine.layout.LayoutNode
import org.wy.engine.layout.LayoutSize
import org.wy.engine.layout.StartEnd
import org.wy.engine.opposite

fun StateHolder<Node>.scroll(
    direction: Direction = Direction.y,
    size: (ScrollNode.(Direction) -> LayoutSize)? = null,
    padding: (ScrollNode.(Direction, StartEnd) -> Float)? = null,
    drawSelf:(ScrollNode.(PlatformCanvas)-> Unit)={},
    buildChildren:StateHolder<Node>.(n: ScrollNode ) -> Unit = {},
    buildContentChildren: StateHolder<Node>.(n: ScrollNode) -> Unit
) {
    val pSize = size
    val providePadding = padding
    val provideDrawSelf=drawSelf
    val flex = object : Flex() {
        override val direction: Direction
            get() = direction
        override val alignItem: AlignItem
            get() = AlignItem.stretch
    }
    object : ScrollNode(this) {
        override fun provideContentSize(direction: Direction): LayoutSize? {
            if (direction.opposite == flex.direction) {
                return LayoutSize(this.innerSize(direction), false)
            }
            return null
        }

        override fun contentLayout(direction: Direction): LayoutFun<LayoutNode> {
            return flex.layout(direction)
        }

        override fun size(direction: Direction): LayoutSize {
            if (pSize != null) {
                return pSize(direction)
            }
            return super.size(direction)
        }

        override fun padding(direction: Direction, startEnd: StartEnd): Float {
            if (providePadding != null) {
                return providePadding(direction, startEnd)
            }
            return super.padding(direction, startEnd)
        }

        override fun drawSelf(canvas: PlatformCanvas) {
            provideDrawSelf(canvas)
        }
        private fun getThis(): ScrollNode{
            return this
        }
        override fun StateHolder<Node>.buildChildren() {
            buildChildren(getThis())
        }
        override fun StateHolder<Node>.buildContentChildren() {
            buildContentChildren(getThis())
        }
    }
}