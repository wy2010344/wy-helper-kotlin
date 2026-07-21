package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue
import java.util.Date

fun StateHolder<Node>.demoList(){
    var list by createSignal(emptyList<Long>())

    renderForEach<Long, Long, Unit>({callback->
        list.forEach {
            callback(it,it)
        }
    }){key,it->
        object : RectNode(this){
            override val argWidth: LayoutSize
                get() = LayoutSize(100f,false)
            override val argHeight: LayoutSize
                get() = LayoutSize(30f,false)

            override fun draw(canvas: PlatformCanvas) {
                canvas.fillRect(w=innerSize(Direction.x),h=innerSize(Direction.y), color = rgba(0,244,0))
                super.draw(canvas)
            }

            override fun StateHolder<Node>.argChildren() {
                object : TextNode(this){
                    override val text: String
                        get() = "key-$key-index-${it.index}"
                }
            }

            override fun mouseClick(e: MouseEvent) {
                list=list.filter { it!=key }
            }
        }
    }

    object : RectNode(this){

        override val argWidth: LayoutSize
            get() = LayoutSize(100f,false)
        override val argHeight: LayoutSize
            get() = LayoutSize(30f,false)

        override fun draw(canvas: PlatformCanvas) {
            canvas.fillRect(w=innerSize(Direction.x),h=innerSize(Direction.y), color = rgba(0,244,0))
            super.draw(canvas)
        }

        override fun mouseClick(e: MouseEvent) {
            list=mutableListOf<Long>().also {
                it.addAll(list)
                it.add(Date().time)
            }
        }
    }
}