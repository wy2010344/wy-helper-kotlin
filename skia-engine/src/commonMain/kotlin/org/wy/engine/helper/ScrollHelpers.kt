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

    /** 容器 gap：内容区与滚动条之间的间距。 */
    override val gap: Float get() = containerGap
    open val containerGap: Float = 0f

    /** 内容区内部 gap。 */
    open val contentGap: Float = 0f

    /** 容器在父布局中的 grow 系数，默认不增长，子类可 override。 */
    override fun argGrow(direction: Direction): Float = 0f

    /** 内容区自定义绘制（如边框），在子节点绘制前执行。 */
    open fun contentDraw(canvas: PlatformCanvas) {}

    /** 内容区：在这里声明滚动内容。 */
    open fun StateHolderWithNode<Node, List<Node>>.contentChildren() {}

    val innerScroll = Scroll(this, direction, context)

    override fun onPointerWheel(e: PointerEvent) {
        // 同向嵌套：内层纵向先消费，滚到底（返回 0）再放行给外层
        innerScroll.scroll(e.wheelDelta)
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
            override val alignItem: AlignItem get() = AlignItem.stretch
            override val gap: Float get() = contentGap
            override fun argGrow(direction: Direction): Float = 1f

            override fun draw(canvas: PlatformCanvas) {
                contentDraw(canvas)
                super.draw(canvas)
            }

            /**
             * 绘制裁剪：不在可视区域内的子节点跳过绘制（仍参与布局计算，保证滚动尺寸正确）。
             * 这是"惰性绘制"——节点全量创建（EachValue 照常），但只画可见的。
             */
            override fun drawChild(child: Node, canvas: PlatformCanvas) {
                if (child is LayoutNode) {
                    val scrollDir = this@SimpleScrollNode.scrollDirection
                    val pos = child.position(scrollDir)
                    val size = child.outerSize(scrollDir)
                    val scrollVal = innerScroll.value
                    val viewport = this@SimpleScrollNode.innerSize(scrollDir)
                    // 可视区域（内容坐标）：[scrollVal, scrollVal + viewport]
                    if (pos + size < scrollVal || pos > scrollVal + viewport) return
                }
                super.drawChild(child, canvas)
            }

            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                contentChildren()
            }
        }

        object : SimpleScrollBar(this) {
            override val scroll: Scroll get() = innerScroll
        }
    }
}
