package org.wy.engine

import com.wy.layout.LayoutFun
import com.wy.mve.StateHolder
import org.wy.engine.layout.Flex
import org.wy.engine.layout.LayoutNode
import org.wy.engine.layout.LayoutSize
import org.wy.signal.addEffect
import org.wy.signal.createSignal
import java.util.Date


fun main() {
    val count = createSignal(0)
    val flex=object : Flex(){
        override val direction: Direction
            get() = Direction.y
        override val gap: Float
            get() = 10f
    }

    val list=createSignal(listOf<Long>())

    object : SkiaApp(){
        override fun layout(direction: Direction): LayoutFun<LayoutNode> {
            return flex.layout(direction)
        }

        override fun StateHolder<Node>.buildChildren() {

            object : RectNode(this){

                override fun toString(): String {
                    return "firstChild"
                }
                override fun size(direction: Direction): LayoutSize {
                    return LayoutSize(100f,false)
                }

                override fun drawSelf(canvas: PlatformCanvas) {
                    canvas.drawRect(
                        w=outerSize(Direction.x),
                        h=outerSize(Direction.y)
                    )
                }

                override fun mouseClick(e: MouseEvent) {
                    count.value++
                    list.value=mutableListOf(Date().time).apply {
                        addAll(list.value)
                    }
                }
            }

            object : RectNode(this){

                override fun toString(): String {
                    return "SecondObj"
                }
                override fun size(direction: Direction): LayoutSize {
                    return LayoutSize(100f,false)
                }

                override fun drawSelf(canvas: PlatformCanvas) {
                    canvas.drawRect(
                        w=outerSize(Direction.x),
                        h=outerSize(Direction.y),
                        color = rgba(255,0,0)
                    )
                }

                override fun mouseClick(e: MouseEvent) {
                  println("second")
                }
            }

            renderForEach(
                {
                    list.value.forEachIndexed { index, lng -> it.invoke(lng,index) }
                },
                {key,e->
                    addDestroy {
                        println("销毁了...")
                    }
                   val node= object : RectNode(this){
                        override fun size(direction: Direction): LayoutSize {
                            return LayoutSize(200f,false)
                        }

                       override fun toString(): String {
                           return "list-${key}"
                       }
                        override fun drawSelf(canvas: PlatformCanvas) {
                            canvas.drawRect(
                                w=outerSize(Direction.x),
                                h=outerSize(Direction.y),
                                color = rgba(255,0,255)
                            )
                        }

                        override fun mouseClick(e: MouseEvent) {
                            list.value=list.value.filter { it!=key }
                        }
                    }
//
//
//                    addEffect{
//                        println("初始化...${key}--${e.index}--${node.outerSize(Direction.x)} ${node.outerSize(
//                            Direction.y)} ${node.position(Direction.x)} ${node.position(Direction.y)}")
//                    }

                }
            )



        }
    }
}
