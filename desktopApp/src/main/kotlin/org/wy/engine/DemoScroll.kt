package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.mve.StateHolder
import org.wy.engine.helper.drag
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.memo

/**
 * 演示 ScrollNode 滚动容器。
 *
 * 在固定大小的视口内放置多行文本，
 * 超出视口的部分被裁剪，可通过鼠标滚轮滚动查看。
 */
fun demoScroll(context: StateHolder<Node>) {
    object : ScrollNode(context) {
        override val argWidth: LayoutSize
            get() = LayoutSize(400f, false)
        override val argHeight: LayoutSize
            get() = LayoutSize(140f, false)

        override fun draw(canvas: PlatformCanvas) {
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
            super.draw(canvas)
        }

        override fun argPadding(direction: Direction, startEnd: StartEnd): Float {
            return 10f
        }

        val scroll = this
        override fun StateHolder<Node>.argChildren() {
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

                override val y: Float
                    get() = maxScrollBar()?.offset ?: 0f
                override val argWidth: LayoutSize
                    get() = LayoutSize(10f, false)
                override val argHeight: LayoutSize
                    get() = LayoutSize(maxScrollBar()?.size ?: 0f, false)


                override fun draw(canvas: PlatformCanvas) {
                    canvas.fillRect(
                        y = scroll.paddingBlockStart,
                        w = outerSize(Direction.x),
                        h = outerSize(Direction.y),
                        color = rgba(0, 0, 0)
                    )
                }
            }

            object : ScrollContent(this), FlexParam{
                    override val layout: LayoutDirection = FlexObject(this)
                override val argWidth: LayoutSize
                    get() = scroll.argWidth
                override val alignItem: AlignItem
                    get() = AlignItem.stretch

                override fun StateHolder<Node>.argChildren() {

                    object : WrappedTextNode(this) {
                        override val text: String
                            get() = (1..20).joinToString(" ") { "Line$it: Hello World! 中文测试。" }
                        override val fontSize: Float get() = 16f
                    }

                    object : RectNode(this) {

                        override val argHeight: LayoutSize
                            get() = LayoutSize(200f, false)

                        override fun draw(canvas: PlatformCanvas) {
                            canvas.fillRect(
                                w = innerSize(Direction.x),
                                h = innerSize(Direction.y),
                                color = rgba(0, 255, 0)
                            )
                        }
                    }
                }
            }
        }
    }
}
