package org.wy.engine.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.Direction
import org.wy.engine.LayoutNode
import org.wy.engine.LayoutSize
import org.wy.engine.Node
import org.wy.engine.PlatformCanvas
import org.wy.engine.PointerEvent
import org.wy.engine.RectNode
import org.wy.engine.StartEnd
import org.wy.engine.absoluteX
import org.wy.engine.absoluteY
import org.wy.engine.fillOuterRoundRect
import org.wy.engine.outerHeight
import org.wy.engine.strokeOuterRoundRect
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection

/**
 * 下拉浮层基类：面板锚定在 [anchor] 节点下方，点击外部 / Esc 关闭。
 *
 * 不圈定焦点（Tab 可从面板逃逸回主界面）；打开时自动聚焦面板内第一个可聚焦元素，
 * 关闭时还原打开前焦点。点击面板不关闭（面板在子节点上拦截冒泡），点击浮层空白处关闭。
 *
 * 样式可定制：override [panelWidth] / [offsetY] / [panel] / [contentChildren]，
 * 或直接用 [dropdown] 工厂（默认样式来自 [Theme.current]）。
 */
open class DropdownBase(
    context: StateHolder<Node, List<Node>>,
    private val anchor: () -> LayoutNode?,
    enabled: Boolean = true,
) : OverlayBase(context, enabled) {

    /** 面板宽度（高度由内容撑起）。 */
    open val panelWidth: Float get() = 160f

    /** 面板与锚点底部的间距。 */
    open val offsetY: Float get() = 4f

    /** 面板内容（下拉项等），由子类 / 工厂提供。 */
    override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {}

    /** 下拉面板：位置由锚点决定（相对本浮层根节点换算），点击不冒泡到浮层空白处。 */
    protected open fun StateHolderWithNode<Node, List<Node>>.panel() {
        object : RectNode(this), FlexParam {
            override val layout: LayoutDirection = FlexObject(this)
            override val direction: Direction get() = Direction.y
            override val directionJustify: DirectionJustify get() = DirectionJustify.grow
            override val alignItem: AlignItem get() = AlignItem.stretch
            override val alignFix: Boolean get() = true
            override val gap: Float get() = 2f
            override val argWidth: LayoutSize get() = LayoutSize(this@DropdownBase.panelWidth, false)

            override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                if (direction == Direction.x) 8f else 6f

            override fun argPosition(direction: Direction): Float {
                val a = anchor() ?: return 0f
                val overlay = this@DropdownBase
                return when (direction) {
                    // 锚点绝对坐标减去浮层根节点绝对坐标，换算成面板相对根节点的偏移
                    Direction.x -> a.absoluteX - overlay.absoluteX
                    Direction.y -> a.absoluteY + a.outerHeight + overlay.offsetY - overlay.absoluteY
                }
            }

            // 拦截冒泡，避免点击面板传播到浮层空白处触发关闭
            override fun onPointerClick(e: PointerEvent) {
                e.stopPropagation()
            }

            override fun draw(canvas: PlatformCanvas) {
                val c = Theme.current.colors
                val r = Theme.current.radius.card
                fillOuterRoundRect(canvas, r, c.surface)
                strokeOuterRoundRect(canvas, r, c.border, 1f)
                super.draw(canvas)
            }

            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                contentChildren()
            }
        }
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        panel()
    }
}

/**
 * 默认样式下拉浮层工厂。
 *
 * @param open 是否打开（闭包读取业务状态）
 * @param anchor 锚点节点（面板贴其底部显示）
 * @param onClose 关闭回调（Esc / 点击外部）
 * @param enabled 是否可用（false 时既不打开也不响应）
 * @param width 面板宽度
 * @param content 面板内容（下拉项等）
 */
fun StateHolder<Node, List<Node>>.dropdown(
    open: () -> Boolean,
    anchor: () -> LayoutNode?,
    onClose: () -> Unit,
    enabled: Boolean = true,
    width: Float = 160f,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit,
): DropdownBase = object : DropdownBase(this, anchor, enabled) {
    override fun isOpen(): Boolean = open()
    override fun onDismiss() = onClose()
    override val panelWidth: Float get() = width
    override fun StateHolderWithNode<Node, List<Node>>.contentChildren() = content()
}
