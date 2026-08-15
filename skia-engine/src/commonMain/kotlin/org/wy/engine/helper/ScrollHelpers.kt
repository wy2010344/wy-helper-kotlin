package org.wy.engine.helper

import com.wy.layout.AlignItem
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection

/**
 * scrollColumn：纵向滚动容器高层 helper。
 * 封装了 Scroll 创建、注册、ScrollContent 子类和滚动条添加，业务层只需传内容。
 *
 * 用法：
 * ```kotlin
 * scrollColumn(
 *     argWidth = LayoutSize(300f, false),
 *     argHeight = LayoutSize(400f, false)
 * ) {
 *     // 内容节点
 *     wWrappedTextNode { text = "Hello" }
 * }
 * ```
 */
fun StateHolder<Node, List<Node>>.scrollColumn(
    argWidth: LayoutSize = LayoutSize(0f, true),
    argHeight: LayoutSize = LayoutSize(0f, true),
    showScrollBar: Boolean = true,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit
) {
    scrollContainer(Direction.y, argWidth, argHeight, showScrollBar, content)
}

/**
 * scrollRow：横向滚动容器高层 helper。
 */
fun StateHolder<Node, List<Node>>.scrollRow(
    argWidth: LayoutSize = LayoutSize(0f, true),
    argHeight: LayoutSize = LayoutSize(0f, true),
    showScrollBar: Boolean = true,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit
) {
    scrollContainer(Direction.x, argWidth, argHeight, showScrollBar, content)
}

private fun StateHolder<Node, List<Node>>.scrollContainer(
    direction: Direction,
    argWidth: LayoutSize,
    argHeight: LayoutSize,
    showScrollBar: Boolean,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit
) {
    lateinit var scrollRef: Scroll
    object : RectNode(this), FlexParam {
        override val direction: Direction = direction
        override val layout: LayoutDirection = FlexObject(this)
        override val argWidth: LayoutSize get() = argWidth
        override val argHeight: LayoutSize get() = argHeight
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch

        val scroll = Scroll(this, direction).also {
            registerScroll(it)
            scrollRef = it
        }

        override fun onPointerWheel(e: PointerEvent) {
            scrollRef.scroll(e.wheelDelta)
        }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            object : ScrollContent(this@scrollContainer), FlexParam {
                override val y: Float
                    get() = if (direction == Direction.y) -scrollRef.value else 0f
                override val x: Float
                    get() = if (direction == Direction.x) -scrollRef.value else 0f
                override val direction: Direction = direction
                override val layout: LayoutDirection = FlexObject(this)
                override val alignFix: Boolean get() = true
                override val alignItem: AlignItem get() = AlignItem.stretch

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    content()
                }
            }

            if (showScrollBar) {
                object : SimpleScrollBar(this@scrollContainer) {
                    override val scroll: Scroll get() = scrollRef
                }
            }
        }
    }
}