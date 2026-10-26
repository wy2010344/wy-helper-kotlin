package org.wy.engine.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.ColorInt
import org.wy.engine.Direction
import org.wy.engine.LayoutSize
import org.wy.engine.Node
import org.wy.engine.PlatformCanvas
import org.wy.engine.RectNode
import org.wy.engine.StartEnd
import org.wy.engine.WrappedTextNode
import org.wy.engine.fillOuterOval
import org.wy.engine.fillOuterRoundRect
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.engine.outerHeight
import org.wy.engine.rgba
import org.wy.engine.strokeOuterRing

/**
 * 侧栏导航项工厂：复用 [ButtonBase] 交互（点击 / Enter / Space / disabled / hover），
 * 视觉包含激活态高亮（primarySoft 背景 + 左侧主色竖条）、可选图标圆点与角标。
 * 样式值全部来自 [Theme.current]。
 *
 * @param label 文案
 * @param active 是否激活（闭包读取业务状态）
 * @param onClick 选中回调
 * @param iconColor 图标圆点颜色，null 则不显示圆点
 * @param badge 角标数字，null 则不显示
 */
fun StateHolder<Node, List<Node>>.navItem(
    label: () -> String,
    active: () -> Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    focusOrder: Int? = null,
    iconColor: ColorInt? = null,
    badge: () -> Int? = { null },
): ButtonBase = object : ButtonBase(this) {
    override val enabled: Boolean get() = enabled
    override val argHeight: LayoutSize get() = LayoutSize(40f, false)
    override val focusOrder: Int? get() = focusOrder
    override val directionJustify: DirectionJustify get() = DirectionJustify.between

    override fun onClick() = onClick()

    override fun drawContent(canvas: PlatformCanvas) {
        val c = Theme.current.colors
        val bg = when {
            active() -> c.primarySoft
            hovered -> c.surfaceHover
            else -> rgba(0, 0, 0, 0)
        }
        fillOuterRoundRect(canvas, 8f, bg)
        if (active()) {
            canvas.fillRoundRect(0f, 10f, 3f, outerHeight - 20f, 1.5f, c.primary)
        }
    }

    override fun drawFocusRing(canvas: PlatformCanvas) {
        strokeOuterRing(canvas, -1f, 8f, Theme.current.colors.focus, 1f)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        val c = Theme.current.colors
        object : RectNode(this), FlexParam {
            override val layout: LayoutDirection = FlexObject(this)
            override val direction: Direction get() = Direction.x
            override val alignItem: AlignItem get() = AlignItem.center
            override val gap: Float get() = 10f

            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                if (iconColor != null) {
                    object : RectNode(this) {
                        override val argWidth: LayoutSize get() = LayoutSize(8f, false)
                        override val argHeight: LayoutSize get() = LayoutSize(8f, false)

                        override fun draw(canvas: PlatformCanvas) {
                            fillOuterOval(canvas, iconColor)
                        }
                    }
                }
                text(
                    label,
                    Theme.current.textSize.label,
                    if (active()) c.primary else c.text,
                    if (active()) 600 else 400
                )
            }
        }

        val b = badge()
        if (b != null) {
            object : WrappedTextNode(this) {
                override val autoWidth: Boolean get() = true
                override val text: String get() = b.toString()
                override val fontSize: Float get() = Theme.current.textSize.caption
                override val color: ColorInt get() = c.onPrimary
                override val fontWeight: Int get() = 600

                override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                    if (direction == Direction.x) 6f else 2f

                override fun draw(canvas: PlatformCanvas) {
                    fillOuterRoundRect(canvas, 9f, c.primary)
                    super.draw(canvas)
                }
            }
        }
    }
}
