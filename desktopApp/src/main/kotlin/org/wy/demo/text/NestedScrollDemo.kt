package org.wy.demo.text

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.helper.SimpleScrollBar
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.GrowChild
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue

// ---- 嵌套滚动 Demo ----

fun StateHolder<Node, List<Node>>.nestedScrollDemo() {
    // 外层：纵向滚动
    object : RectNode(this), FlexParam {
        override val direction: Direction = Direction.x
        override val layout: LayoutDirection = FlexObject(this)
        override val argHeight: LayoutSize get() = LayoutSize(300f, false)
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val directionJustify: DirectionJustify = DirectionJustify.start

        var outerScroll: Scroll=Scroll(this, Direction.y).also {
            //这里注册，后面是发现不了的。
            registerScroll(it)
        }


        override fun onPointerWheel(e: PointerEvent) {
            outerScroll.scroll(e.wheelDelta)
        }


        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            // 内容区
            object : ScrollContent(this), FlexParam, GrowChild {
                override val y: Float get() = -outerScroll.value
                override val layout: LayoutDirection = FlexObject(this)
                override val alignFix: Boolean get() = true
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val gap: Float get() = 8f
                override fun argGrow(direction: Direction): Float =1f

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {

                    // 标题
                    object : WrappedTextNode(this) {
//                        override val autoWidth: Boolean get() = true
                        override val text: String get() = "外层纵向滚动 (标题)"
                        override val fontSize: Float get() = 16f
                        override val fontWeight: Int get() = 700
                        override val color: ColorInt get() = rgba(60, 60, 80)
                    }

                    // 内层：横向滚动
                    innerHorizontalScroll()

                    // 内层：纵向滚动（滚完再放行给外层）
                    innerVerticalScroll()

                    // 更多内容使外层可滚动
                    repeat(100) { i ->
                        object : RectNode(this), FlexParam {
                            override val layout: LayoutDirection = FlexObject(this)
                            override val argHeight: LayoutSize get() = LayoutSize(40f, false)
                            override val alignFix: Boolean get() = true
                            override val alignItem: AlignItem get() = AlignItem.stretch

                            override fun draw(canvas: PlatformCanvas) {
                                fillInnerRect(canvas, rgba(230, 230, 245))
                                super.draw(canvas)
                            }

                            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                                object : WrappedTextNode(this) {
                                    override val autoWidth: Boolean get() = true
                                    override val text: String get() = "外层项 #${i + 1}"
                                    override val fontSize: Float get() = 13f
                                    override val color: ColorInt get() = rgba(50, 50, 70)
                                }
                            }
                        }
                    }
                }
            }

            // 外层滚动条
            object : SimpleScrollBar(this) {
                override val scroll: Scroll get() = outerScroll
            }
        }
    }
}

// ---- 内层横向滚动 ----

fun StateHolder<Node, List<Node>>.innerHorizontalScroll() {
    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val argHeight: LayoutSize get() = LayoutSize(80f, false)
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        val innerScroll = Scroll(this, Direction.x).also { registerScroll(it) }

        override val directionJustify: DirectionJustify
            get() = DirectionJustify.start
        override fun onPointerWheel(e: PointerEvent) {
            // 横向滚动只响应横向滚轮（Shift+滚轮），纵向滚轮放行给外层
            if (!engineGlobal.shift) return
            innerScroll.scroll(e.wheelDelta)
            e.stopPropagation()
        }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            object : ScrollContent(this), FlexParam , GrowChild{
                override val x: Float get() = -innerScroll.value
                override val direction: Direction = Direction.x
                override val layout: LayoutDirection = FlexObject(this)
                override val alignFix: Boolean get() = true
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val gap: Float get() = 4f
                override fun argGrow(direction: Direction): Float =1f

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    repeat(100) { i ->
                        object : RectNode(this), FlexParam {
                            override val argWidth: LayoutSize get() = LayoutSize(60f, false)
                            override val alignFix: Boolean get() = true
                            override val alignItem: AlignItem get() = AlignItem.stretch
                            override val directionJustify: DirectionJustify
                                get() = DirectionJustify.start
                            override val layout: LayoutDirection = FlexObject(this)

                            override fun draw(canvas: PlatformCanvas) {
                                fillInnerRect(canvas, rgba(200 + i * 5, 220 - i * 5, 240, 255))
                                super.draw(canvas)
                            }

                            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {

                                object : WrappedTextNode(this) {
                                    override val text: String get() = "$i"
                                    override val fontSize: Float get() = 14f
                                    override val fontWeight: Int get() = 700
                                    override val color: ColorInt get() = rgba(40, 40, 60)
                                }
                            }
                        }
                    }
                }
            }

            object : SimpleScrollBar(this) {
                override val scroll: Scroll get() = innerScroll
            }
        }
    }
}

// ---- 内层纵向滚动（同向嵌套：内层先滚，滚完放行给外层） ----

fun StateHolder<Node, List<Node>>.innerVerticalScroll() {
    val parentScroll=consumeScroll(Direction.y)
    object : RectNode(this), FlexParam {
        override val direction: Direction
            get() = Direction.x
        override val layout: LayoutDirection = FlexObject(this)
        override val argHeight: LayoutSize get() = LayoutSize(100f, false)
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val directionJustify: DirectionJustify
            get() = DirectionJustify.start
        val innerScroll = Scroll(this, Direction.y).also { registerScroll(it) }

        override fun onPointerWheel(e: PointerEvent) {
            // 同向嵌套：内层纵向先消费，滚到底（返回 0）再放行给外层
            val consumed = innerScroll.scroll(e.wheelDelta)
            val result=e.wheelDelta-consumed
            if (result != 0f) {
                parentScroll?.scroll(result)
            }
            e.stopPropagation()
        }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            object : ScrollContent(this), FlexParam, GrowChild {
                override val y: Float get() = -innerScroll.value
                override val layout: LayoutDirection = FlexObject(this)
                override val alignFix: Boolean get() = true
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val gap: Float get() = 4f
                override fun argGrow(direction: Direction): Float = 1f

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    repeat(100) { i ->
                        object : RectNode(this), FlexParam {
                            override val layout: LayoutDirection = FlexObject(this)
                            override val argHeight: LayoutSize get() = LayoutSize(30f, false)
                            override val alignFix: Boolean get() = true
                            override val alignItem: AlignItem get() = AlignItem.stretch
                            override val directionJustify: DirectionJustify
                                get() = DirectionJustify.start

                            override fun draw(canvas: PlatformCanvas) {
                                fillInnerRect(canvas, rgba(220 - i % 3 * 20, 235, 200 + i % 4 * 10, 255))
                                super.draw(canvas)
                            }

                            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                                object : WrappedTextNode(this) {
                                    override val text: String get() = "内层纵向 $i"
                                    override val fontSize: Float get() = 12f
                                    override val color: ColorInt get() = rgba(40, 60, 40)
                                }
                            }
                        }
                    }
                }
            }

            object : SimpleScrollBar(this) {
                override val scroll: Scroll get() = innerScroll
            }
        }
    }
}