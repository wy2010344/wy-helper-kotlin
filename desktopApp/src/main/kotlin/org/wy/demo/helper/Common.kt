package org.wy.demo.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection

/**
 * helper 组件 demo 公共脚手架。
 *
 * 每个 demo 文件自带 `fun main()`，构造 `object : SkiaApp(w, h), FlexParam`，
 * 在 `argChildren()` 里用 [page] 搭建纵向页面，[sectionTitle] / [hint] / [row] 辅助排版。
 */

/** 页面脚手架：纵向，高度由内容撑起（grow），宽度由父布局拉伸决定。 */
fun StateHolder<Node, List<Node>>.page(
    gap: Float = 12f,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit,
) {
    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.y
        // 主轴由子节点撑起高度；父（顶层 flex）问本节点高度时不再依赖父 → 避免"重复进入 memo"
        override val directionJustify: DirectionJustify get() = DirectionJustify.grow
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = gap

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            content()
        }
    }
}

/** 演示区标题。 */
fun StateHolder<Node, List<Node>>.sectionTitle(text: String) {
    object : WrappedTextNode(this) {
        override val autoWidth: Boolean get() = true
        override val text: String get() = text
        override val fontSize: Float get() = 15f
        override val fontWeight: Int get() = 700
        override val color: ColorInt get() = rgba(40, 40, 60)
    }
}

/** 演示区说明文字。 */
fun StateHolder<Node, List<Node>>.hint(text: String) {
    object : WrappedTextNode(this) {
        override val autoWidth: Boolean get() = true
        override val text: String get() = text
        override val fontSize: Float get() = 11f
        override val color: ColorInt get() = rgba(130, 130, 150)
    }
}

/** 横向一行：宽度由父布局拉伸，高度由内容撑起，用于把控件与文字并排摆放。 */
fun StateHolder<Node, List<Node>>.row(
    gap: Float = 10f,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit,
) {
    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.x
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        // 交叉轴高度由内容撑起（!alignFix），不被父布局反复询问自身尺寸
        override val alignFix: Boolean get() = false
        override val alignItem: AlignItem get() = AlignItem.center
        override val gap: Float get() = gap

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            content()
        }
    }
}
