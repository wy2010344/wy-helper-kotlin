package org.wy.demo.text

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.helper.SimpleScrollBar
import org.wy.engine.helper.SimpleScrollNode
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.GrowChild
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue

// ---- 嵌套滚动 Demo ----

fun main() {
    object : SkiaApp(900, 700), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val directionJustify: DirectionJustify get() = DirectionJustify.center
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 8f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            nestedScrollDemo()
        }
    }
}

fun StateHolder<Node, List<Node>>.nestedScrollDemo() {
   object :SimpleScrollNode(this){        override val argHeight: LayoutSize get() = LayoutSize(300f, false)

       override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {

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
}

// ---- 内层横向滚动 ----

fun StateHolder<Node, List<Node>>.innerHorizontalScroll() {

    object : SimpleScrollNode(this, Direction.x) {
        override val argHeight: LayoutSize = LayoutSize(80f, false)
        override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
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

        override fun onPointerWheel(e: PointerEvent) {
            // 横向滚动只响应横向滚轮（Shift+滚轮），纵向滚轮放行给外层
            if (!engineGlobal.shift) return
            super.onPointerWheel(e)
        }
    }
}

// ---- 内层纵向滚动（同向嵌套：内层先滚，滚完放行给外层） ----

fun StateHolder<Node, List<Node>>.innerVerticalScroll() {

    object : SimpleScrollNode(this) {
        override val argHeight: LayoutSize = LayoutSize(100f, false)
        override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
            repeat(100) { i ->
                object : RectNode(this), FlexParam {
                    override val layout: LayoutDirection = FlexObject(this)
                    override val argHeight: LayoutSize get() = LayoutSize(30f, false)
                    override val alignFix: Boolean get() = true
                    override val alignItem: AlignItem get() = AlignItem.stretch
                    override val directionJustify: DirectionJustify
                        get() = DirectionJustify.start

                    override fun draw(canvas: PlatformCanvas) {
                        fillInnerRect(
                            canvas,
                            rgba(220 - i % 3 * 20, 235, 200 + i % 4 * 10, 255)
                        )
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
}