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
import org.wy.engine.RectNode
import org.wy.engine.StartEnd
import org.wy.engine.Toast
import org.wy.engine.innerSize
import org.wy.engine.outerSize
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue

/**
 * Toast 容器：铺满窗口但整层不拦截命中（点击穿透到主界面），
 * 内部使用 flex 列表排列多个 toast，超出可视区域时可滚动。
 *
 * 业务可通过继承此类自定义容器样式。
 */
open class ToastContainer(
    context: StateHolder<Node, List<Node>>,
    private val toastList: () -> List<Toast>,
) : RectNode(context), FlexParam {

    override val notInLayout: Boolean get() = true

    override fun argSize(direction: Direction): LayoutSize =
        LayoutSize(layoutParent?.innerSize(direction) ?: 0f, false)

    override fun argPadding(direction: Direction, startEnd: StartEnd): Float = 0f
    override fun argPosition(direction: Direction): Float {
        return 0f
    }

    /** 整层不拦截命中：点击穿透到主界面。 */
    override fun acceptHit(x: Float, y: Float): Boolean = false

    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.y
    override val directionJustify: DirectionJustify get() = DirectionJustify.start
    override val alignItem: AlignItem get() = AlignItem.stretch
    override val alignFix: Boolean get() = true

    /** 滚动偏移（信号），超出可视区域时由内部滚轮事件驱动。 */
    private var scrollOffset by createSignal(0f)

    /** 滚动容器可视高度（信号）。 */
    private var viewportHeight by createSignal(0f)

    /** 内容总高度（信号），由 argChildren 中的回调累加计算。 */
    private var contentHeight by createSignal(0f)

    override fun onPointerWheel(e: org.wy.engine.PointerEvent) {
        val maxScroll = (contentHeight - viewportHeight).coerceAtLeast(0f)
        scrollOffset = (scrollOffset + e.wheelDelta).coerceIn(0f, maxScroll)
        e.stopPropagation()
    }

    override fun draw(canvas: PlatformCanvas) {
        val vh = innerSize(Direction.y)
        if (vh > 0f && viewportHeight != vh) viewportHeight = vh

        // 累加子节点高度得到内容总高度
        var total = 0f
        for (child in children) {
            if (child is LayoutNode) {
                total += child.outerSize(Direction.y)
            }
        }
        if (contentHeight != total) contentHeight = total

        // 裁剪到可视区域并应用滚动偏移
        val vw = outerSize(Direction.x)
        canvas.save()
        canvas.clipRect(0f, 0f, vw, vh)
        canvas.translate(0f, -scrollOffset)
        super.draw(canvas)
        canvas.restore()
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        renderForEach({ callback ->
            toastList().forEach { toast ->
                callback(toast, toast)
            }
        }) { toast, _ ->
            toast.render(this)
        }
    }
}
