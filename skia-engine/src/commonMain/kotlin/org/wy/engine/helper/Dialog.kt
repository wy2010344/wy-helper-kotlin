package org.wy.engine.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.Direction
import org.wy.engine.LayoutSize
import org.wy.engine.Node
import org.wy.engine.PlatformCanvas
import org.wy.engine.PointerEvent
import org.wy.engine.RectNode
import org.wy.engine.StartEnd
import org.wy.engine.fillOuterRect
import org.wy.engine.fillOuterRoundRect
import org.wy.engine.rgba
import org.wy.engine.strokeOuterRoundRect
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection

/**
 * 模态对话框基类：在 [OverlayBase] 基础上打开期间圈定焦点（Tab 只在框内循环）、
 * 绘制全屏遮罩（点击关闭），内容为居中的面板卡片。
 *
 * 交互：打开时自动聚焦面板内第一个可聚焦元素并还原原焦点；Esc 关闭、点击遮罩关闭
 * （点击面板不关闭）；disabled 时不打开、不拦截事件。
 *
 * 样式可定制：override [drawScrim] / [panelWidth] / [panel] / [contentChildren]，
 * 或直接用 [dialog] 工厂（默认样式来自 [Theme.current]）。
 */
open class DialogBase(
    context: StateHolder<Node, List<Node>>,
    enabled: Boolean = true,
) : OverlayBase(context, enabled) {

    /** 模态：打开期间圈定焦点。 */
    override val trapsFocus: Boolean get() = true

    /** 面板宽度（高度由内容撑起）。 */
    open val panelWidth: Float get() = 380f

    /** 绘制遮罩（默认半透明黑铺满窗口）。 */
    override fun drawScrim(canvas: PlatformCanvas) {
        fillOuterRect(canvas, rgba(0, 0, 0, 110))
    }

    /** 居中面板：点击不冒泡到遮罩，默认绘制白色圆角卡片。 */
    protected open fun StateHolderWithNode<Node, List<Node>>.panel() {
        object : RectNode(this), FlexParam {
            override val layout: LayoutDirection = FlexObject(this)
            override val direction: Direction get() = Direction.y
            override val directionJustify: DirectionJustify get() = DirectionJustify.grow
            override val alignItem: AlignItem get() = AlignItem.stretch
            override val alignFix: Boolean get() = true
            override val gap: Float get() = 10f
            override val argWidth: LayoutSize get() = LayoutSize(this@DialogBase.panelWidth, false)

            override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                if (direction == Direction.x) 20f else 18f

            // 拦截冒泡，避免点击面板传播到遮罩触发关闭
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
 * 默认样式模态对话框工厂。
 *
 * @param open 是否打开（闭包读取业务状态）
 * @param onClose 关闭回调（Esc / 点击遮罩）
 * @param enabled 是否可用（false 时既不打开也不响应）
 * @param width 面板宽度
 * @param content 面板内容
 */
fun StateHolder<Node, List<Node>>.dialog(
    open: () -> Boolean,
    onClose: () -> Unit,
    enabled: Boolean = true,
    width: Float = 380f,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit,
): DialogBase = object : DialogBase(this, enabled) {
    override fun isOpen(): Boolean = open()
    override fun onDismiss() = onClose()
    override val panelWidth: Float get() = width
    override fun StateHolderWithNode<Node, List<Node>>.contentChildren() = content()
}
