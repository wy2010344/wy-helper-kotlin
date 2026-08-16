package org.wy.engine.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.Direction
import org.wy.engine.Node
import org.wy.engine.PlatformCanvas
import org.wy.engine.PointerEvent
import org.wy.engine.RectNode
import org.wy.engine.StartEnd
import org.wy.engine.fillOuterRoundRect
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.GrowChild
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.addEffect

/**
 * 轻提示浮层基类：铺满窗口但整层不拦截命中（点击穿透到主界面），
 * 内容以暗色圆角条展示在窗口底部居中。
 *
 * 不管理焦点（打开 / 关闭不移动焦点）；点击内容立即关闭；显示时长由 [durationMs]
 * 控制，到时自动调用 [onDismiss]，也可覆盖 [now] 注入时间源（测试用）。
 *
 * 样式可定制：override [body]（内容容器样式）/ [contentChildren]，
 * 或直接用 [toast] 工厂（默认样式来自 [Theme.current]）。
 */
open class ToastBase(
    context: StateHolder<Node, List<Node>>,
    protected val durationMs: Long = 2000L,
) : RectNode(context), FlexParam, GrowChild {
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.y
    override val directionJustify: DirectionJustify get() = DirectionJustify.end
    override val alignItem: AlignItem get() = AlignItem.center
    override val alignFix: Boolean get() = true

    /** 撑满窗口（作为渲染树最上层的浮层子节点）。 */
    override fun argGrow(direction: Direction): Float = 1f

    /** 是否显示（业务信号驱动）。 */
    open fun isShown(): Boolean = false

    /** 关闭回调（到时自动消失 / 点击内容）。 */
    open fun onDismiss() {}

    /** 显示开始时刻之后的累计时间判断基准，测试可覆盖注入时间源。 */
    protected open fun now(): Long = System.currentTimeMillis()

    /** 超时效果是否已挂起（draw 只注册一次，避免每帧重复排队）。 */
    private var timeoutSyncPending = false

    /** 本次显示的开始时刻（null 表示尚未开始计时）。 */
    private var shownAt: Long? = null

    /**
     * 超时效果：draw 时注册 0 级触发，显示期间通过 1 级常驻链路持续待命，
     * 每次批次重新判断是否达到 [durationMs]，到时调用 [onDismiss]。
     */
    internal val timeoutEffect: () -> Unit = {
        timeoutSyncPending = false
        if (isShown()) {
            val start = shownAt
            if (start == null) {
                shownAt = now()
            } else if (now() - start >= durationMs) {
                shownAt = null
                onDismiss()
            } else {
                addEffect(1, timeoutEffect)
                timeoutSyncPending = true
            }
        } else {
            shownAt = null
        }
    }

    /** 显示状态即可见性：不显示时整个节点从渲染树隐藏，不占命中测试。 */
    override val hide: Boolean get() = !isShown()

    /** 整层不拦截命中：点击主界面任意位置穿透，只有内容条本身可点。 */
    override fun acceptHit(x: Float, y: Float): Boolean = false

    /** 点击内容立即关闭（外部点击穿透，不触发）。 */
    override fun onPointerClick(e: PointerEvent) {
        if (isShown()) onDismiss()
    }

    override fun draw(canvas: PlatformCanvas) {
        // 与渲染同步：尚未挂起超时效果时注册 0 级效果
        if (!timeoutSyncPending) {
            timeoutSyncPending = true
            addEffect(0, timeoutEffect)
        }
        super.draw(canvas)
    }

    /** 内容条：点击命中即关闭，默认绘制暗色圆角条。 */
    protected open fun StateHolderWithNode<Node, List<Node>>.body() {
        object : RectNode(this), FlexParam {
            override val layout: LayoutDirection = FlexObject(this)
            override val direction: Direction get() = Direction.y
            override val directionJustify: DirectionJustify get() = DirectionJustify.grow
            override val alignItem: AlignItem get() = AlignItem.stretch
            override val gap: Float get() = 4f

            override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                if (direction == Direction.x) 16f else 10f

            override fun draw(canvas: PlatformCanvas) {
                fillOuterRoundRect(canvas, Theme.current.radius.control, Theme.current.colors.inverseSurface)
                super.draw(canvas)
            }

            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                contentChildren()
            }
        }
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        body()
    }

    /** 提示内容（在内容条中声明），由子类 / 工厂提供。 */
    open fun StateHolderWithNode<Node, List<Node>>.contentChildren() {}
}

/**
 * 默认样式轻提示工厂。
 *
 * @param shown 是否显示（闭包读取业务状态）
 * @param onClose 关闭回调（到时自动消失 / 点击内容）
 * @param durationMs 显示时长（毫秒）
 * @param content 提示内容（默认暗色圆角条）
 */
fun StateHolder<Node, List<Node>>.toast(
    shown: () -> Boolean,
    onClose: () -> Unit,
    durationMs: Long = 2000L,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit,
): ToastBase = object : ToastBase(this, durationMs) {
    override fun isShown(): Boolean = shown()
    override fun onDismiss() = onClose()
    override fun StateHolderWithNode<Node, List<Node>>.contentChildren() = content()
}
