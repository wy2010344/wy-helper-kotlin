package org.wy.engine.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.Direction
import org.wy.engine.KeyCode
import org.wy.engine.LayoutSize
import org.wy.engine.Node
import org.wy.engine.PlatformCanvas
import org.wy.engine.PointerEvent
import org.wy.engine.RectNode
import org.wy.engine.innerSize
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.GrowChild
import org.wy.engine.layout.LayoutDirection
import org.wy.engine.size


/**
 * 浮层基类：作为业务 [Renderer] 子树中最后声明的子节点铺满窗口，
 * 提供打开 / 关闭、焦点移入与还原、Esc 关闭、点击外部关闭与 disabled 语义。
 *
 * 打开状态由业务通过 [isOpen] 闭包驱动，关闭时整个节点从渲染树隐藏，不拦截任何事件。
 * 打开时自动聚焦内容里第一个可聚焦元素并记录原焦点，关闭时还原；[trapsFocus] 为 true
 * 时声明 [org.wy.engine.Node.focusTrap]，Tab 只在浮层内循环。Esc 或点击浮层空白处调用
 * [onDismiss]，内容节点需自行拦截冒泡（如点击面板不关闭）。
 *
 * 子类提供 / [onDismiss] 与 [contentChildren]，[dialog] / [dropdown] 等工厂给出默认样式。
 */
open class OverlayBase(
    context: StateHolder<Node, List<Node>>,
) : RectNode(context), FlexParam {

    /** 是否打开（子类可 override 为信号驱动，默认 true）。 */
    open val enabled: Boolean = true
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.x
    override val directionJustify: DirectionJustify get() = DirectionJustify.center
    override val alignItem: AlignItem get() = AlignItem.center
    override val alignFix: Boolean get() = true
    override val notInLayout: Boolean
        get() = true

    override fun argPosition(direction: Direction): Float {
        return 0f
    }

    override fun argSize(direction: Direction): LayoutSize {
        return LayoutSize(layoutParent?.innerSize(direction) ?: 0f, false)
    }

    /** 关闭回调（Esc / 点击浮层空白处触发）。 */
    open fun onDismiss() {}

    /** 打开期间是否圈定焦点（模态为 true，轻量浮层如 Dropdown 为 false）。 */
    protected open val trapsFocus: Boolean get() = false

    private val g = engineGlobal

    /** 打开前焦点，用于关闭时还原。 */
    private var savedFocus: Node? = null

    /** 打开期间每帧同步焦点（draw 时注册，渲染后同步消费，保证节点已存在）。 */

    init {
        context.addDestroy(g.registerKeyPress { e ->
            // 仅当焦点在本浮层内（本浮层为最上层打开者）时响应 Esc
            if (enabled && e.code == KeyCode.Escape && isDescendantOf(g.focused)) {
                onDismiss()
            }
        })
    }

    /** 按当前打开状态执行一次焦点移入 / 还原（测试与效果共用）。 */
    internal fun syncFocusNow() {
        if (enabled) {
            enterFocus()
        } else {
            exitFocus()
        }
    }

    private fun isDescendantOf(node: Node?): Boolean {
        var cur = node
        while (cur != null) {
            if (cur === this) return true
            cur = cur.parent
        }
        return false
    }

    private fun enterFocus() {
        val current = g.focused
        // 焦点已在浮层内时不重复保存，避免覆盖为浮层内部节点
        if (current == null || !isDescendantOf(current)) {
            savedFocus = current
            focusFirstInside()
        }
    }

    private fun exitFocus() {
        // 关闭时焦点在浮层内 → 还原给打开前的持有者
        if (g.focused != null && isDescendantOf(g.focused)) {
            g.focused = savedFocus
        }
        savedFocus = null
    }

    private fun focusFirstInside() {
        fun firstFocusable(node: Node): Node? {
            if (node.focusable && !node.hide) return node
            node.children.forEach { firstFocusable(it)?.let { n -> return n } }
            return null
        }
        children.forEach { firstFocusable(it)?.let { n -> g.focused = n; return } }
    }

    /** 打开期间圈定焦点（Dialog 模态为 true）。 */
    override val focusTrap: Boolean get() = enabled && trapsFocus

    /** 打开状态即可见性：关闭时整个节点从渲染树隐藏，不占命中测试。 */
    override val hide: Boolean get() = !(enabled)

    /** 绘制遮罩（默认不绘制，Dialog 覆盖为半透明黑）。 */
    open fun drawScrim(canvas: PlatformCanvas) {}

    /** 点击浮层空白处关闭（内容节点在子节点上拦截冒泡，点击内容不触发）。 */
    override fun onPointerClick(e: PointerEvent) {
        if (enabled) onDismiss()
    }

    override fun draw(canvas: PlatformCanvas) {
        g.addPostRenderEffect { syncFocusNow() }
        drawScrim(canvas)
        super.draw(canvas)
    }

    /** 浮层内容（在内容节点中声明），由子类 / 工厂提供。 */
    open fun StateHolderWithNode<Node, List<Node>>.contentChildren() {}
}
