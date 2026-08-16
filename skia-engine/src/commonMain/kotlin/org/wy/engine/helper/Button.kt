package org.wy.engine.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.getValue
import org.wy.signal.memo

/**
 * 标准交互按钮基类：交互行为内置，样式完全外置。
 *
 * 交互（内置，符合标准）：
 * - 点击（[onPointerClick]）与键盘 Enter / Space 统一触发 [onClick]；
 * - disabled 时不聚焦（不进 Tab 序）、不响应点击与键盘；
 * - 触摸 / 笔设备不产生 hover（避免桌面式 sticky hover），pressed 按下反馈仍保留；
 * - 聚焦时绘制焦点环（画法由 [drawFocusRing] 决定，组件只保证状态可感知）。
 *
 * 样式（全部可自定义）：override [drawContent] / [drawFocusRing] / [argChildren] / padding /
 * [argWidth] / [argHeight] 等。默认样式见 [button]。
 */
open class ButtonBase(
    context: StateHolder<Node, List<Node>>,
    protected val enabled: Boolean = true,
) : RectNode(context), FlexParam {
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.x
    override val directionJustify: DirectionJustify get() = DirectionJustify.center
    override val alignItem: AlignItem get() = AlignItem.center
    override val alignFix: Boolean get() = true

    override val focusable: Boolean get() = enabled
    override val focusOrder: Int? get() = null

    protected val g = engineGlobal

    /** 鼠标悬停状态（触摸 / 笔不置位；disabled 无 hover）。 */
    protected val hovered by memo {
        enabled && g.moveHitTest?.include(this) == true && g.lastPointerDevice != PointerDevice.Touch
    }

    /** 按下状态：鼠标按下且命中本按钮（含触摸按下反馈；disabled 无 pressed）。 */
    protected val pressed by memo {
        enabled && g.pressed != null && g.moveHitTest?.include(this) == true
    }

    /** 点击回调：点击 / Enter / Space 统一入口，子类 override。 */
    open fun onClick() {}

    init {
        context.addDestroy(g.registerKeyPress { e ->
            if (enabled && isFocused && (e.code == KeyCode.Enter || e.key == ' ')) onClick()
        })
    }

    override fun onPointerClick(e: PointerEvent) {
        if (!enabled) return
        onClick()
    }

    /** 不可用按钮显示禁止光标；可用时显示手型（可点击）。 */
    override fun cursorAt(x: Float, y: Float): CursorType =
        if (enabled) CursorType.POINTER else CursorType.NOT_ALLOWED

    /** 绘制背景 / 边框（不含焦点环），默认空。 */
    open fun drawContent(canvas: PlatformCanvas) {}

    /** 聚焦时绘制焦点环，默认空（如 [LayoutNode.strokeOuterRing]）。 */
    open fun drawFocusRing(canvas: PlatformCanvas) {}

    override fun draw(canvas: PlatformCanvas) {
        drawContent(canvas)
        // 不可用时即使被外部强制聚焦也不画焦点环
        if (enabled && isFocused) drawFocusRing(canvas)
        super.draw(canvas)
    }
}

/** 按钮风格变体：主按钮（填充主色）与次按钮（surface + 边框）。 */
enum class ButtonVariant {
    Primary, Secondary
}

/**
 * 默认样式按钮工厂。
 *
 * 样式值全部来自 [Theme.current]，业务可整体替换主题换肤；
 * 需要更自由的视觉时可改用 `object : ButtonBase(this) { ... }` 直接覆盖视觉方法。
 */
fun StateHolder<Node, List<Node>>.button(
    label: () -> String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    variant: ButtonVariant = ButtonVariant.Primary,
    width: Float = 90f,
    height: Float = 32f,
    focusOrder: Int? = null,
): ButtonBase = object : ButtonBase(this, enabled) {
    override val argWidth: LayoutSize get() = LayoutSize(width, false)
    override val argHeight: LayoutSize get() = LayoutSize(height, false)
    override val focusOrder: Int? get() = focusOrder

    override fun onClick() = onClick()

    override fun drawContent(canvas: PlatformCanvas) {
        val c = Theme.current.colors
        val radius = Theme.current.radius.button
        when (variant) {
            ButtonVariant.Primary -> {
                val bg = when {
                    !enabled -> c.primaryDisabled
                    pressed -> c.primaryPressed
                    hovered -> c.primaryHover
                    else -> c.primary
                }
                fillOuterRoundRect(canvas, radius, bg)
            }

            ButtonVariant.Secondary -> {
                val bg = when {
                    !enabled -> c.surfaceDisabled
                    pressed -> c.border
                    hovered -> c.surfaceHover
                    else -> c.surface
                }
                fillOuterRoundRect(canvas, radius, bg)
                strokeOuterRoundRect(canvas, radius, c.border, 1f)
            }
        }
    }

    override fun drawFocusRing(canvas: PlatformCanvas) {
        strokeOuterRing(
            canvas, 2f, Theme.current.radius.button + 2f, Theme.current.colors.focus, 2f
        )
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        val c = Theme.current.colors
        text(
            label,
            Theme.current.textSize.label,
            if (enabled) {
                if (variant == ButtonVariant.Primary) c.onPrimary else c.text
            } else {
                c.textDisabled
            },
            600
        )
    }
}
