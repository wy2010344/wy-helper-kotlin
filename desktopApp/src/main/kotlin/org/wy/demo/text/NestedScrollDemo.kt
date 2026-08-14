package org.wy.demo.text

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.helper.SimpleScrollBar
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue

// ---- 嵌套滚动 Demo ----

fun StateHolder<Node, List<Node>>.nestedScrollDemo() {
    // 外层：纵向滚动
    object : RectNode(this), FlexParam {
        override val direction: Direction = Direction.y
        override val layout: LayoutDirection = FlexObject(this)
        override val argWidth: LayoutSize get() = LayoutSize(500f, false)
        override val argHeight: LayoutSize get() = LayoutSize(300f, false)
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 4f

        val outerScroll = Scroll(this, Direction.y).also { registerScroll(it) }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            // 内容区
            object : ScrollContent(this), FlexParam {
                override val layout: LayoutDirection = FlexObject(this)
                override val scrollDirection: Direction = Direction.y
                override val alignFix: Boolean get() = true
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val gap: Float get() = 8f

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    // 标题
                    object : WrappedTextNode(this) {
                        override val autoWidth: Boolean get() = true
                        override val text: String get() = "外层纵向滚动 (标题)"
                        override val fontSize: Float get() = 16f
                        override val fontWeight: Int get() = 700
                        override val color: ColorInt get() = rgba(60, 60, 80)
                    }

                    // 内层：横向滚动
                    innerHorizontalScroll()

                    // 更多内容使外层可滚动
                    repeat(3) { i ->
                        object : RectNode(this), FlexParam {
                            override val direction: Direction = Direction.x
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
            object : SimpleScrollBar(this, Direction.y) {
                override val scroll: Scroll get() = outerScroll
            }
        }
    }
}

// ---- 内层横向滚动 ----

fun StateHolder<Node, List<Node>>.innerHorizontalScroll() {
    object : RectNode(this), FlexParam {
        override val direction: Direction = Direction.x
        override val layout: LayoutDirection = FlexObject(this)
        override val argHeight: LayoutSize get() = LayoutSize(80f, false)
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 4f

        val innerScroll = Scroll(this, Direction.x).also { registerScroll(it) }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            object : ScrollContent(this), FlexParam {
                override val layout: LayoutDirection = FlexObject(this)
                override val scrollDirection: Direction = Direction.x
                override val alignFix: Boolean get() = true
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val gap: Float get() = 4f

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    repeat(10) { i ->
                        object : RectNode(this), FlexParam {
                            override val argWidth: LayoutSize get() = LayoutSize(60f, false)
                            override val argHeight: LayoutSize get() = LayoutSize(60f, false)
                            override val alignFix: Boolean get() = true
                            override val alignItem: AlignItem get() = AlignItem.stretch

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

            object : SimpleScrollBar(this, Direction.x) {
                override val scroll: Scroll get() = innerScroll
            }
        }
    }
}