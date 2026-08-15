package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolderWithNode
import org.wy.engine.helper.SimpleScrollBar
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.GrowChild
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.memo
import org.wy.signal.setValue
import java.util.Date

fun main() {
    object : SkiaApp(), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val directionJustify: DirectionJustify
            get() = DirectionJustify.center
        override val alignFix: Boolean
            get() = true
        override val gap: Float
            get() = 10f
        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {

            class RowModal(
                val key: Long
            ) {
                var hide by createSignal(false)

            }

            var list by createSignal(emptyList<RowModal>())


            val g = consume(engineGlobalContext)

            object : WrappedTextNode(this) {
                override val autoWidth: Boolean
                    get() = true
                override val text: String
                    get() = "展示"

                override fun onPointerClick(e: PointerEvent) {
                    list.forEach { it.hide = false }
                    super.onPointerClick(e)
                }
            }

            object : Node(this) {
                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {

                    object : RectNode(this), FlexParam {
                        override val direction: Direction = Direction.x
                        override val layout: LayoutDirection = FlexObject(this)
                        override val alignItem: AlignItem = AlignItem.stretch

                        //这里却一定要重置成非增长型。
                        override val directionJustify: DirectionJustify = DirectionJustify.start
                        override val alignFix: Boolean = true
                        override fun argSize(direction: Direction): LayoutSize {
                            return LayoutSize(300f, false)
                        }

                        val scrollY = Scroll(this).also {
                            registerScroll(it)
                        }

                        override fun onPointerWheel(e: PointerEvent) {
                            scrollY.scroll(e.wheelDelta)
                        }

                        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                            object : ScrollContent(this), FlexParam, GrowChild {
                                override val y: Float
                                    get() = -scrollY.value

                                override fun argGrow(direction: Direction): Float = 1f
                                override val alignFix = true
                                override val gap: Float = 10f
                                override val alignItem: AlignItem = AlignItem.stretch
                                override val layout: LayoutDirection = FlexObject(this)
                                override fun draw(canvas: PlatformCanvas) {
                                    strokeInnerRect(canvas)
                                    super.draw(canvas)
                                }

                                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                                    renderForEach({ callback ->
                                        list.forEach {
                                            callback(it.key, it)
                                        }
                                    }) { key, it ->
                                        object : RectNode(this), FlexParam {
                                            override val hide: Boolean
                                                get() = it.value.hide
                                            override val layout: LayoutDirection = FlexObject(this)
                                            override val direction: Direction
                                                get() = Direction.x
                                            override val alignFix: Boolean
                                                get() = true
                                            override val directionJustify: DirectionJustify
                                                get() = DirectionJustify.between
                                            override val argHeight: LayoutSize
                                                get() = LayoutSize(30f, false)

                                            override fun draw(canvas: PlatformCanvas) {
                                                strokeInnerRect(canvas)
                                                super.draw(canvas)
                                            }

                                            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                                                object : WrappedTextNode(this) {
                                                    override val autoWidth: Boolean
                                                        get() = true
                                                    override val text: String
                                                        get() = "key-$key-index-${it.index}"
                                                }

                                                object : WrappedTextNode(this) {
                                                    override val autoWidth: Boolean
                                                        get() = true
                                                    override val text: String
                                                        get() = "隐藏"

                                                    override fun argPadding(
                                                        direction: Direction,
                                                        startEnd: StartEnd
                                                    ): Float {
                                                        return 1f
                                                    }

                                                    override fun onPointerClick(e: PointerEvent) {
                                                        it.value.hide = true
                                                    }

                                                    override fun draw(canvas: PlatformCanvas) {
                                                        strokeOuterRect(canvas)
                                                        super.draw(canvas)
                                                    }
                                                }
                                                object : WrappedTextNode(this) {
                                                    override val autoWidth: Boolean
                                                        get() = true
                                                    override val text: String
                                                        get() = "删除"

                                                    override fun onPointerClick(e: PointerEvent) {
                                                        list = list.filter { it.key != key }
                                                    }

                                                    override fun argPadding(
                                                        direction: Direction,
                                                        startEnd: StartEnd
                                                    ): Float {
                                                        return 1f
                                                    }

                                                    override fun draw(canvas: PlatformCanvas) {
                                                        strokeOuterRect(canvas)
                                                        super.draw(canvas)
                                                    }
                                                }
                                            }

                                        }
                                    }
                                }
                            }

                            object : SimpleScrollBar(this) {
                                override val scroll: Scroll
                                    get() = scrollY
                            }

                        }
                    }
                }
            }
            object : WrappedTextNode(this) {
                override val autoWidth: Boolean = true
                override fun argPaddingInline(startEnd: StartEnd): Float {
                    return 50f
                }

                override fun argPaddingBlock(startEnd: StartEnd): Float {
                    return 5f
                }

                val hovered by memo {
                    g?.moveHitTest?.include(this) ?: false
                }

                override fun toString(): String {
                    return "Button"
                }

                override val text: String
                    get() = "共有${list.size}条数据 "

                override fun draw(canvas: PlatformCanvas) {
                    strokeOuterRect(
                        canvas,
                        color = if (hovered) rgba(0, 188, 0) else rgba(0, 244, 0)
                    )
                    if (hovered) {

                        fillOuterRect(canvas, rgba(222, 222, 222))
                    }
                    super.draw(canvas)
                }

                override fun onPointerClick(e: PointerEvent) {
                    list = mutableListOf<RowModal>().also {
                        it.addAll(list)
                        it.add(RowModal(Date().time))
                    }
                }
            }
        }
    }
}
