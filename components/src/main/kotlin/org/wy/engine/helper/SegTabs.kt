package org.wy.engine.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.Direction
import org.wy.engine.LayoutSize
import org.wy.engine.Node
import org.wy.engine.PlatformCanvas
import org.wy.engine.RectNode
import org.wy.engine.fillOuterRoundRect
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.GrowChild
import org.wy.engine.layout.LayoutDirection
import org.wy.engine.outerHeight
import org.wy.engine.outerWidth

/**
 * 分段控件（Segmented Tabs）工厂：轨道背景 + 若干等宽分段。
 *
 * 每个分段复用 [ButtonBase] 交互（点击 / Enter / Space / disabled），
 * 选中段显示 surface 卡片 + 主色文字，其余段次要文字。
 * 样式值全部来自 [Theme.current]。
 *
 * @param active 当前选中项（闭包读取业务状态）
 * @param onSelect 选中回调
 * @param items 分段列表（值 to 文案）
 * @param focusOrderOffset 分段 focusOrder = offset + 下标；传 null 则不设（按文档序）
 */
fun <T> StateHolder<Node, List<Node>>.segTabs(
    active: () -> T,
    onSelect: (T) -> Unit,
    items: List<Pair<T, String>>,
    enabled: Boolean = true,
    focusOrderOffset: Int? = 10,
    height: Float = 36f,
): RectNode = object : RectNode(this), FlexParam {
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.x
    override val directionJustify: DirectionJustify get() = DirectionJustify.start
    override val alignItem: AlignItem get() = AlignItem.stretch
    override val alignFix: Boolean get() = true
    override val argHeight: LayoutSize get() = LayoutSize(height, false)
    override val gap: Float get() = 6f

    override fun draw(canvas: PlatformCanvas) {
        fillOuterRoundRect(canvas, Theme.current.radius.card, Theme.current.colors.surfaceHover)
        super.draw(canvas)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        val btnEnabled = enabled
        items.forEachIndexed { i, (tab, name) ->
            object : ButtonBase(this), GrowChild {
                override val enabled: Boolean get() = btnEnabled
                override fun argGrow(direction: Direction): Float = 1f
                override val focusOrder: Int? get() = focusOrderOffset?.let { it + i }

                private val isOn get() = active() == tab

                override fun onClick() = onSelect(tab)

                override fun drawContent(canvas: PlatformCanvas) {
                    val c = Theme.current.colors
                    if (isOn) {
                        canvas.fillRoundRect(0f, 2f, outerWidth, outerHeight - 4f, 8f, c.surface)
                        canvas.strokeRoundRect(0f, 2f, outerWidth, outerHeight - 4f, 8f, c.border, 1f)
                    } else if (hovered) {
                        canvas.fillRoundRect(0f, 2f, outerWidth, outerHeight - 4f, 8f, c.surface)
                    }
                }

                override fun drawFocusRing(canvas: PlatformCanvas) {
                    canvas.strokeRoundRect(1f, 3f, outerWidth - 2f, outerHeight - 6f, 8f, Theme.current.colors.focus, 2f)
                }

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    text(
                        { name },
                        Theme.current.textSize.label,
                        if (isOn) Theme.current.colors.primary else Theme.current.colors.textSecondary,
                        if (isOn) 600 else 400
                    )
                }
            }
        }
    }
}
