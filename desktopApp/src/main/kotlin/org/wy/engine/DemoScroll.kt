package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.layout.LayoutFun
import com.wy.mve.StateHolder
import org.wy.engine.helper.drag
import org.wy.engine.helper.scroll
import org.wy.engine.layout.Flex
import org.wy.engine.layout.LayoutNode
import org.wy.engine.layout.LayoutSize
import org.wy.engine.layout.StartEnd
import org.wy.signal.memo

/**
 * 演示 ScrollNode 滚动容器。
 *
 * 在固定大小的视口内放置多行文本，
 * 超出视口的部分被裁剪，可通过鼠标滚轮滚动查看。
 */
fun demoScroll(context: StateHolder<Node>) {
    context.scroll(
        size = {
            when (it) {
                Direction.x -> LayoutSize(400f, false)
                Direction.y -> LayoutSize(140f, false)
            }
        },
        drawSelf = { canvas ->
            canvas.strokeRect(
                0f,
                0f,
                outerSize(Direction.x),
                outerSize(Direction.y),
                rgba(0, 0, 233)
            )
            canvas.strokeRect(
                padding(Direction.x, StartEnd.start), padding(Direction.y, StartEnd.start),
                innerSize(Direction.x), innerSize(Direction.y), rgba(0, 0, 233)
            )
        },
        padding = { _, _ -> 10f },
        buildChildren = { scroll ->
            with(scroll) {
                buildScrollNode()
            }
            val maxScrollBar = memo { scroll.scrollBarSize(Direction.y) }
            object : RectNode(this) {
                override fun mouseDown(e: MouseEvent) {
                    var lastY = e.globalY
                    drag {
                        val deltaY = it.y - lastY
                        val ms = maxScrollBar()
                        val rDelta = ms?.scrollToMove(scroll.scroll(ms.moveToScroll(deltaY))) ?: 0f
                        lastY += rDelta
                    }
                }

                override fun toString(): String {
                    return "SrollBar"
                }

                override fun position(d: Direction): Float = when (d) {
                    Direction.x -> 0f
                    Direction.y -> maxScrollBar()?.offset ?: 0f
                }

                override fun size(direction: Direction): LayoutSize = when (direction) {
                    Direction.x -> LayoutSize(10f, false)
                    Direction.y -> LayoutSize(maxScrollBar()?.size ?: 0f, false)
                }

                override fun drawSelf(canvas: PlatformCanvas) {
                    canvas.fillRect(
                        y = scroll.innerStart(Direction.y),
                        w = outerSize(Direction.x),
                        h = outerSize(Direction.y),
                        color = rgba(0, 0, 0)
                    )
                }
            }
        }
    ) {

        object : WrappedTextNode(this) {
            override val text: String
                get() = (1..20).joinToString(" ") { "Line$it: Hello World! 中文测试。" }
            override val fontSize: Float get() = 16f
        }

        object : RectNode(this) {
            override fun size(direction: Direction): LayoutSize {
                if (direction == Direction.y) {
                    return LayoutSize(200f, false)
                }
                return sizeFromParent(direction)
            }

            override fun drawSelf(canvas: PlatformCanvas) {
                canvas.fillRect(
                    w = innerSize(Direction.x),
                    h = innerSize(Direction.y),
                    color = rgba(0, 255, 0)
                )
            }
        }
    }
}
