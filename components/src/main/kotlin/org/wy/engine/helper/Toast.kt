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
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.engine.postDelayed
import org.wy.engine.strokeOuterRoundRect
import org.wy.lib.EmptyFun

/**
 * 轻提示浮层基类：铺满窗口但整层不拦截命中（点击穿透到主界面），
 * 内容以暗色圆角条展示在窗口底部居中。
 *
 * 通过 [Toast] 机制全局挂载，始终在 Pop 之上；非重叠（新 toast 替换旧 toast）。
 * 不管理焦点；点击内容立即关闭；到时自动消失。
 *
 * 构造即显示；init 时即按 [durationMs] 调度定时器开始倒计时（与渲染无关，
 * 即使没有后续重绘也会按时触发），节点销毁时取消定时器。
 */
open class ToastBase(
    context: StateHolder<Node, List<Node>>,
    protected val durationMs: Long = 2000L,
    private val scheduler: (delayMs: Long, action: () -> Unit) -> EmptyFun = ::postDelayed,
) : RectNode(context), FlexParam {
    override val layout: LayoutDirection = FlexObject(this)

    /** 关闭回调（到时自动消失 / 点击内容）。 */
    open fun onDismiss() {}

    init {
        // 挂载即开始倒计时（与渲染无关）；节点销毁时经 addDestroy 取消定时器。
        // 注意：init 中只调用构造参数与非 open 成员，避免虚调用穿透到未初始化的子类。
        addDestroy(scheduler(durationMs) { onDismiss() })
    }

    override val hide: Boolean get() = false

    /** 整层不拦截命中：点击穿透，只有内容条本身可点。 */
    override fun acceptHit(x: Float, y: Float): Boolean = false

    /** 点击内容立即关闭。 */
    override fun onPointerClick(e: PointerEvent) {
        onDismiss()
    }

    override val gap: Float get() = 4f

    override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
        if (direction == Direction.x) 16f else 10f

    override fun draw(canvas: PlatformCanvas) {
        fillOuterRoundRect(
            canvas,
            Theme.current.radius.control,
            Theme.current.colors.inverseSurface
        )
        super.draw(canvas)
    }
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

            override fun StateHolderWithNode<Node, List<Node>>.argChildren() = content()
        }
    }
}
