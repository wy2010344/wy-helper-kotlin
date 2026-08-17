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
import org.wy.engine.Toast
import org.wy.engine.engineGlobalContext
import org.wy.engine.fillOuterRoundRect
import org.wy.engine.innerSize
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.engine.strokeOuterRoundRect

/**
 * 轻提示浮层基类：铺满窗口但整层不拦截命中（点击穿透到主界面），
 * 内容以暗色圆角条展示在窗口底部居中。
 *
 * 通过 [Toast] 机制全局挂载，始终在 Pop 之上；非重叠（新 toast 替换旧 toast）。
 * 不管理焦点；点击内容立即关闭；到时自动消失。
 *
 * 构造即显示，[durationMs] 到时自动调 [onDismiss] 移除。
 */
open class ToastBase(
    context: StateHolder<Node, List<Node>>,
    protected val durationMs: Long = 2000L,
) : RectNode(context), FlexParam {
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.y
    override val directionJustify: DirectionJustify get() = DirectionJustify.grow
    override val alignItem: AlignItem get() = AlignItem.center
    override val alignFix: Boolean get() = true
    /** 关闭回调（到时自动消失 / 点击内容）。 */
    open fun onDismiss() {}

    /** 时间源，测试可覆盖。 */
    protected open fun now(): Long = System.currentTimeMillis()

    private var shownAt: Long? = null

    /**
     * 超时效果：每帧由 draw 注册，渲染后同步消费，
     * 到时调 [onDismiss] 自动移除。
     */
    internal fun timeoutEffect() {
        val start = shownAt
        if (start == null) {
            shownAt = now()
        } else if (now() - start >= durationMs) {
            shownAt = null
            onDismiss()
        }
    }

    override val hide: Boolean get() = false

    /** 整层不拦截命中：点击穿透，只有内容条本身可点。 */
    override fun acceptHit(x: Float, y: Float): Boolean = false

    /** 点击内容立即关闭。 */
    override fun onPointerClick(e: PointerEvent) {
        onDismiss()
    }

    override fun draw(canvas: PlatformCanvas) {
        engineGlobal.addPostRenderEffect { timeoutEffect() }
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
 * 轻提示工厂：通过 [Toast] 机制全局挂载，始终在 Pop 之上，非重叠。
 *
 * 调用即显示（如在 `onClick` 中调用），到时自动消失或点击内容立即关闭。
 *
 * @param durationMs 显示时长（毫秒），默认 2 秒
 * @param content 提示内容（默认暗色圆角条）
 * @return [Toast] 句柄，可用于手动移除
 */
fun StateHolder<Node, List<Node>>.toast(
    durationMs: Long = 2000L,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit,
): Toast {
    val g = consume(engineGlobalContext)!!
    return g.appendToast { toast: Toast ->
        object : ToastBase(this, durationMs) {
            override fun onDismiss() {
                g.removeToast(toast)
            }
            override fun StateHolderWithNode<Node, List<Node>>.contentChildren() = content()
        }
    }
}
