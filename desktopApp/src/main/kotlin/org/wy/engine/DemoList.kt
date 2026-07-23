package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue
import java.util.Date

fun StateHolder<Node>.demoList() {
    var list by createSignal(emptyList<Long>())

    object : Node(this) {
        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {

            object : ScrollNode(this), FlexParam {
                override val direction: Direction = Direction.x
                override val layout: LayoutDirection = FlexObject(this)
                override val alignItem: AlignItem = AlignItem.stretch

                //这里却一定要重置成非增长型。
                override val directionJustify: DirectionJustify = DirectionJustify.start
                override val alignFix: Boolean = true
                override fun argSize(direction: Direction): LayoutSize {
                    return LayoutSize(300f, false)
                }
                val scroll = this
                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    object : ScrollContent(this), FlexParam {
                        override val grow: Float = 1f
                        override val alignFix = true
                        override val gap: Float = 10f
                        override val alignItem: AlignItem = AlignItem.stretch
                        override val layout: LayoutDirection = FlexObject(this)
                        override fun draw(canvas: PlatformCanvas) {
                            strokeInnerRect(canvas)
                            super.draw(canvas)
                        }

                        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                            renderForEach<Long, Long, Unit>({ callback ->
                                list.forEach {
                                    callback(it, it)
                                }
                            }) { key, it ->
                                object : RectNode(this) {
                                    //                                    override val argWidth: LayoutSize
//                                        get() = LayoutSize(30f,false)
                                    override val argHeight: LayoutSize
                                        get() = LayoutSize(30f, false)

                                    override fun draw(canvas: PlatformCanvas) {
                                        fillInnerRect(canvas)
                                        super.draw(canvas)
                                    }

                                    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                                        object : TextNode(this) {
                                            override val text: String
                                                get() = "key-$key-index-${it.index}"
                                        }
                                    }

                                    override fun mouseClick(e: MouseEvent) {
                                        list = list.filter { it != key }
                                    }
                                }
                            }
                        }
                    }

                    object : RectNode(this) , FlexParam{

                        override val alignFix: Boolean = true
                        override val alignItem: AlignItem = AlignItem.stretch
                        override val directionJustify: DirectionJustify = DirectionJustify.start
                        override val layout: LayoutDirection= FlexObject(this)
                        override val argWidth: LayoutSize = LayoutSize(10f, false)

                        override fun draw(canvas: PlatformCanvas) {
                            strokeInnerRect(canvas)
                            super.draw(canvas)
                        }

                        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                            renderForEach({
                                val o = scroll.scrollBarSize(Direction.y)
                                it(o != null, o)
                            }) { key, et ->
                                if (key) {
                                    object : RectNode(this) {
                                        override val argWidth: LayoutSize
                                            get() = super.argWidth
                                        override val argHeight: LayoutSize
                                            get() = LayoutSize(et.value?.size?:0f,false)
                                        override val y: Float
                                            get() = et.value?.offset?:0f

                                        override fun draw(canvas: PlatformCanvas) {
                                            fillInnerRect(canvas)
                                            super.draw(canvas)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    object : RectNode(this) {

        override val argWidth: LayoutSize
            get() = LayoutSize(100f, false)
        override val argHeight: LayoutSize
            get() = LayoutSize(30f, false)

        override fun draw(canvas: PlatformCanvas) {
            canvas.fillRect(
                w = innerSize(Direction.x),
                h = innerSize(Direction.y),
                color = rgba(0, 244, 0)
            )
            super.draw(canvas)
        }

        override fun mouseClick(e: MouseEvent) {
            list = mutableListOf<Long>().also {
                it.addAll(list)
                it.add(Date().time)
            }
        }
    }
}