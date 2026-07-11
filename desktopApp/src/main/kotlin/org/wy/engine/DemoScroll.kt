package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.layout.LayoutFun
import com.wy.mve.StateHolder
import org.wy.engine.layout.Flex
import org.wy.engine.layout.LayoutNode
import org.wy.engine.layout.LayoutSize
import org.wy.engine.layout.StartEnd

/**
 * 演示 ScrollNode 滚动容器。
 *
 * 在固定大小的视口内放置多行文本，
 * 超出视口的部分被裁剪，可通过鼠标滚轮滚动查看。
 */
fun demoScroll(context: StateHolder<Node>) {
    val flex = object : Flex() {
        override val direction: Direction
            get() = Direction.y
        override val alignItem: AlignItem
            get() = AlignItem.stretch
    }
    object : ScrollNode(context) {
        override fun size(direction: Direction): LayoutSize = when (direction) {
            Direction.x -> LayoutSize(400f, false)
            Direction.y -> LayoutSize(140f, false)
        }

        override fun drawSelf(canvas: PlatformCanvas) {
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

        }

        override fun padding(direction: Direction, startEnd: StartEnd): Float {
            return 10f
        }

        override fun contentLayout(direction: Direction): LayoutFun<LayoutNode> {
            return flex.layout(direction)
        }

        override fun StateHolder<Node>.buildContentChildren() {
            object : WrappedTextNode(this) {
                override val text: String
                    get() = (1..20).joinToString(" ") { "Line$it: Hello World! 中文测试。" }
                override val fontSize: Float get() = 16f
                override val wrappingWidth: Float get() = 380f
            }
        }

        override fun toString(): String {
            return "ScrollNode"
        }

        val c = this
        override fun StateHolder<Node>.buildOtherChildren() {

            object : RectNode(this) {
                override fun toString(): String {
                    return "SrollBar"
                }

                override fun position(d: Direction): Float = when (d) {
                    Direction.x -> 0f
                    Direction.y -> scrollBarSize(Direction.y)?.second ?: 0f
                }

                override fun size(direction: Direction): LayoutSize = when (direction) {
                    Direction.x -> LayoutSize(10f, false)
                    //这个地方会陷入死循环，因为scrollBarSize里面，this会变成“ScrollBar”这个类
                    Direction.y -> LayoutSize(scrollBarSize(Direction.y)?.first ?: 0f, false)
                }

                override fun drawSelf(canvas: PlatformCanvas) {
                    canvas.fillRect(
                        y=c.innerStart(Direction.y),
                        w = outerSize(Direction.x),
                        h = outerSize(Direction.y),
                        color = rgba(0, 0, 0)
                    )
                }
            }
        }
    }
}
