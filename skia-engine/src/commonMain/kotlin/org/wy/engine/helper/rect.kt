package org.wy.engine.helper

import com.wy.mve.StateHolder
import org.wy.engine.Direction
import org.wy.engine.Node
import org.wy.engine.PlatformCanvas
import org.wy.engine.RectNode
import org.wy.engine.layout.LayoutSize
import org.wy.engine.rgba

fun StateHolder<Node>.rect(direction: Direction= Direction.y, size: Float=100f){
    val provideDirection=direction
    val provideSize=size
    object : RectNode(this){
        override fun toString(): String {
            return "rect"
        }
        override fun size(direction: Direction): LayoutSize {
            if(provideDirection==direction){
                return LayoutSize(provideSize,false)
            }
            //父容器决定宽度
            return sizeFromParent(direction)
        }

        override fun drawSelf(canvas: PlatformCanvas) {
            canvas.fillRect(w=innerSize(Direction.x),h=innerSize(Direction.y), color = rgba(255,0,0))
        }
    }
}