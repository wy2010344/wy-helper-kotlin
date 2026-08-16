package org.wy.engine.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.Direction
import org.wy.engine.KeyCode
import org.wy.engine.Node
import org.wy.engine.PlatformCanvas
import org.wy.engine.PointerEvent
import org.wy.engine.RectNode
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.GrowChild
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.addEffect

/**
 * 浮层基类：作为业务 [Renderer] 子树中最后声明的子节点铺满窗口，
 * 提供打开 / 关闭、焦点移入与还原、Esc 关闭、点击外部关闭与 disabled 语义。
 *
 * 打开状态由业务通过 [isOpen] 闭包驱动，关闭时整个节点从渲染树隐藏，不拦截任何事件。
 * 打开时自动聚焦内容里第一个可聚焦元素并记录原焦点，关闭时还原；[trapsFocus] 为 true
 * 时声明 [org.wy.engine.Node.focusTrap]，Tab 只在浮层内循环。Esc 或点击浮层空白处调用
 * [onDismiss]，内容节点需自行拦截冒泡（如点击面板不关闭）。
 *
 * 子类提供 [isOpen] / [onDismiss] 与 [contentChildren]，[dialog] / [dropdown] 等工厂给出默认样式。
 */
open class OverlayBase(
    context: StateHolder<Node, List<Node>>,
    protected val enabled: Boolean = true,
) : RectNode(context), FlexParam, GrowChild {
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.x
    override val directionJustify: DirectionJustify get() = DirectionJustify.center
    override val alignItem: AlignItem get() = AlignItem.center
    override val alignFix: Boolean get() = true

    /** 撑满窗口（作为渲染树最上层的浮层子节点）。 */
    override fun argGrow(direction: Direction): Float = 1f

    /** 是否打开（业务信号驱动）。 */
    open fun isOpen(): Boolean = false

    /** 关闭回调（Esc / 点击浮层空白处触发）。 */
    open fun onDismiss() {}

    /** 打开期间是否圈定焦点（模态为 true，轻量浮层如 Dropdown 为 false）。 */
    protected open val trapsFocus: Boolean get() = false

    private val g = engineGlobal

    /** 打开前焦点，用于关闭时还原。 */
    private var savedFocus: Node? = null

    /** 焦点同步效果是否已挂起（draw 只注册一次，避免每帧重复排队）。 */
    private var focusSyncPending = false

    /**
     * 焦点同步效果：draw 时注册 0 级触发，打开期间通过 1 级常驻链路持续待命，
     * 在每次真实批次中重新读取打开状态，翻转时执行 [syncFocusNow]。
     */
    internal val focusSyncEffect: () -> Unit = {
        focusSyncPending = false
        if (enabled && isOpen()) {
            addEffect(1, focusSyncEffect)
            focusSyncPending = true
        }
        syncFocusNow()
    }

    init {
        context.addDestroy(g.registerKeyPress { e ->
            // 仅当焦点在本浮层内（本浮层为最上层打开者）时响应 Esc
            if (enabled && isOpen() && e.code == KeyCode.Escape && isDescendantOf(g.focused)) {
                onDismiss()
            }
        })
    }

    /** 按当前打开状态执行一次焦点移入 / 还原（测试与效果共用）。 */
    internal fun syncFocusNow() {
        if (enabled && isOpen()) {
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
    override val focusTrap: Boolean get() = enabled && isOpen() && trapsFocus

    /** 打开状态即可见性：关闭时整个节点从渲染树隐藏，不占命中测试。 */
    override val hide: Boolean get() = !(enabled && isOpen())

    /** 绘制遮罩（默认不绘制，Dialog 覆盖为半透明黑）。 */
    open fun drawScrim(canvas: PlatformCanvas) {}

    /** 点击浮层空白处关闭（内容节点在子节点上拦截冒泡，点击内容不触发）。 */
    override fun onPointerClick(e: PointerEvent) {
        if (enabled && isOpen()) onDismiss()
    }

    override fun draw(canvas: PlatformCanvas) {
        // 与渲染同步：尚未挂起焦点同步效果时注册 0 级效果
        if (!focusSyncPending) {
            focusSyncPending = true
            addEffect(0, focusSyncEffect)
        }
        drawScrim(canvas)
        super.draw(canvas)
    }

    /** 浮层内容（在内容节点中声明），由子类 / 工厂提供。 */
    open fun StateHolderWithNode<Node, List<Node>>.contentChildren() {}
}
