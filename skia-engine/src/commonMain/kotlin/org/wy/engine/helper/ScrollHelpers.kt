package org.wy.engine.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.GrowChild
import org.wy.engine.layout.LayoutDirection

/**
 * 标准滚动容器：封装了 Scroll 创建/注册、ScrollContent 和滚动条。
 *
 * 用法：
 * ```kotlin
 * object : SimpleScrollNode(this) {
 *     override val argHeight: LayoutSize get() = LayoutSize(300f, false)
 *     override val contentGap: Float get() = 8f
 *     override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
 *         // 内容节点
 *     }
 * }
 * ```
 *
 * - [direction]：滚动方向（Direction.y 纵向 / Direction.x 横向），容器布局方向自动取反（内容 + 滚动条排布方向）。
 * - 同向嵌套时内层滚到底会自动放行给外层（通过 Scroll 的 parentScroll 机制）。
 * - 滚动条默认是 [OverlayScrollBar]：浮在内容之上、不占布局空间、停止滚动后自动淡出
 *   （macOS 系统浮层滚动条行为）。要换成常驻轨道样式，override [scrollBarEnabled] = false 后自行追加。
 */
open class SimpleScrollNode(
    context: StateHolder<Node, List<Node>>,
    direction: Direction = Direction.y
) : RectNode(context), FlexParam, GrowChild {
    /** 原始滚动方向（不受容器布局方向反转影响） */
    val scrollDirection: Direction = direction
    final override val direction: Direction = direction.opposite
    final override val layout: LayoutDirection = FlexObject(this)

    final override val alignFix: Boolean get() = true
    final override val alignItem: AlignItem get() = AlignItem.stretch
    final override val directionJustify: DirectionJustify = DirectionJustify.start

    /** 内容区内部 gap。 */
    open val contentGap: Float = 0f

    open val contentAcceptHit = true
    open val contentAlignItem: AlignItem = AlignItem.stretch

    /** 容器在父布局中的 grow 系数，默认不增长，子类可 override。 */
    override fun argGrow(direction: Direction): Float = 0f

    /** 内容区自定义绘制（如边框），在子节点绘制前执行。 */
    open fun contentDraw(canvas: PlatformCanvas, content: ScrollContent) {}

    /** 内容区：在这里声明滚动内容。 */
    open fun StateHolderWithNode<Node, List<Node>>.contentChildren() {}

    /** 是否绘制滚动条。 */
    open val scrollBarEnabled: Boolean = true

    /** 滚动条视觉参数；换肤时整体替换即可。 */
    open val scrollBarStyle: ScrollBarStyle = ScrollBarStyle()

    /** 滚动条实例（用于截图 / 演示时强制显示等场景）。 */
    var scrollBar: OverlayScrollBar? = null
        private set

    val innerScroll = Scroll(this, direction, context)

    override fun onPointerWheel(e: PointerEvent) {
        // 同向嵌套：内层先消费，滚到底（返回 0）再放行给外层。
        //
        // 陷阱：这里必须取**滚动方向** `scrollDirection`，绝不能取 `direction`。
        // `direction` 是容器布局方向，恰好是 `scrollDirection.opposite`
        // （纵向滚动时内容与滚动条横排，故 direction = x）。用 `direction` 判断会把
        // 纵向滚轮错读成 `wheelDeltaX`——桌面端横向增量恒为 0，于是「只能横向滚、
        // 不能纵向滚」（之前正是这个 bug）。等价写法：`innerScroll.direction`。
        //
        // 滚轮增量按滚动方向取对应轴：纵向取 wheelDeltaY，横向取 wheelDeltaX，
        // 与 TS 侧 Scroll 的 `scroll.direction === 'y' ? wheelDeltaY : wheelDeltaX` 同义。
        val delta = if (scrollDirection == Direction.y) e.wheelDeltaY else e.wheelDeltaX
        // 本轴没有分量就放行：不要把交叉轴滚轮吞掉（否则横向父容器收不到 Shift+滚轮），
        // 与 Flutter Scrollable「只消费自身滚动轴的 delta」及 TS `scrollWheel` 的
        // `if (delta === 0) return` 一致。同向嵌套仍在 Scroll.scroll 内由 parentScroll 接续。
        if (delta == 0f) return
        innerScroll.scroll(delta)
        e.stopPropagation()
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        registerScroll(innerScroll)
        object : ScrollContent(this), FlexParam, GrowChild {
            // ScrollContent 内容布局方向与滚动方向一致：纵向滚动时内容纵向排列，横向滚动时内容横向排列
            override val direction: Direction = this@SimpleScrollNode.scrollDirection
            override val y: Float get() = if (direction == Direction.y) -innerScroll.value else 0f
            override val x: Float get() = if (direction == Direction.x) -innerScroll.value else 0f
            override val layout: LayoutDirection = FlexObject(this)
            override val alignFix: Boolean get() = true
            override val alignItem: AlignItem get() = contentAlignItem
            override val gap: Float get() = contentGap
            override fun argGrow(direction: Direction): Float = 1f

            override fun acceptHit(x: Float, y: Float): Boolean {
                if (contentAcceptHit) {
                    return super.acceptHit(x, y)
                }
                return false
            }

            override fun draw(canvas: PlatformCanvas) {
                contentDraw(canvas, this)
                super.draw(canvas)
            }

            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                contentChildren()
            }
        }

        if (scrollBarEnabled) {
            scrollBar = OverlayScrollBar(this, innerScroll, scrollBarStyle)
        }
    }
}
