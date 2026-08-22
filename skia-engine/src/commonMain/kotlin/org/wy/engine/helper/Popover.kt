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
import org.wy.engine.RectF
import org.wy.engine.RectNode
import org.wy.engine.StartEnd
import org.wy.engine.absoluteX
import org.wy.engine.absoluteY
import org.wy.engine.fillOuterRoundRect
import org.wy.engine.rgba
import org.wy.engine.strokeOuterRoundRect
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection

/**
 * 词典式浮层基类：面板定位在锚点区域 [anchorRect]（如文本选区）下方，
 * 点击外部 / Esc 关闭，点击面板不关闭；打开时聚焦面板内第一个可聚焦元素、关闭还原。
 *
 * 与 [DropdownBase] 同模式（继承 [OverlayBase]），差异在锚点：
 * Dropdown 锚定节点（实时取 absoluteX/Y），Popover 锚定静态矩形区域（如文本选区）。
 *
 * 样式可定制：override [panelWidth] / [offsetY] / [drawPanel] / [contentChildren]，
 * 或直接用 [popover] 工厂（默认样式来自 [Theme.current]）。
 */
open class PopoverBase(
    context: StateHolder<Node, List<Node>>,
    private val anchorRect: RectF,
) : OverlayBase(context) {

    /** 面板宽度（高度由内容撑起）。 */
    open val panelWidth: Float get() = 280f

    /** 面板与锚点底部的间距。 */
    open val offsetY: Float get() = 4f

    /** 面板内容（词条释义等），由子类 / 工厂提供。 */
    override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {}

    /** 面板背景绘制：默认阴影 + surface 卡片 + 边框（读 Theme）。 */
    protected open fun drawPanel(canvas: PlatformCanvas) {
        val c = Theme.current.colors
        val r = Theme.current.radius.card
        // 阴影：向下偏移的半透明圆角矩形
        canvas.save()
        canvas.translate(0f, 3f)
        fillOuterRoundRect(canvas, r, rgba(0, 0, 0, 30))
        canvas.restore()
        // 卡片
        fillOuterRoundRect(canvas, r, c.surface)
        strokeOuterRoundRect(canvas, r, c.border, 1f)
    }

    /** 面板：位置由锚点区域决定（相对本浮层根节点换算），点击不冒泡到浮层空白处。 */
    protected open fun StateHolderWithNode<Node, List<Node>>.panel() {
        object : RectNode(this), FlexParam {
            override val layout: LayoutDirection = FlexObject(this)
            override val direction: Direction get() = Direction.y
            override val directionJustify: DirectionJustify get() = DirectionJustify.grow
            override val alignItem: AlignItem get() = AlignItem.stretch
            override val alignFix: Boolean get() = true
            override val gap: Float get() = 6f
            override val argWidth: LayoutSize
                get() = LayoutSize(this@PopoverBase.panelWidth, false)

            override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                if (direction == Direction.x) 14f else 12f

            override fun argPosition(direction: Direction): Float {
                val overlay = this@PopoverBase
                return when (direction) {
                    // 锚点区域坐标减去浮层根节点绝对坐标，换算成面板相对根节点的偏移
                    Direction.x -> overlay.anchorRect.left - overlay.absoluteX
                    Direction.y -> overlay.anchorRect.bottom + overlay.offsetY - overlay.absoluteY
                }
            }

            // 拦截冒泡，避免点击面板传播到浮层空白处触发关闭
            override fun onPointerClick(e: PointerEvent) {
                e.stopPropagation()
            }

            override fun draw(canvas: PlatformCanvas) {
                this@PopoverBase.drawPanel(canvas)
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
 * 默认样式词典浮层工厂。
 *
 * @param anchorRect 锚点区域（面板贴其底部显示）
 * @param onClose 关闭回调（Esc / 点击外部）
 * @param width 面板宽度
 * @param content 面板内容
 */
fun StateHolder<Node, List<Node>>.popover(
    anchorRect: RectF,
    onClose: () -> Unit,
    width: Float = 280f,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit,
): PopoverBase = object : PopoverBase(this, anchorRect) {
    override fun onDismiss() = onClose()
    override val panelWidth: Float get() = width
    override fun StateHolderWithNode<Node, List<Node>>.contentChildren() = content()
}
