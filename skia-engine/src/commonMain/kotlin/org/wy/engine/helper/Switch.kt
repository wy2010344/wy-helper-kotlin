package org.wy.engine.helper

import com.wy.mve.StateHolder
import org.wy.engine.LayoutSize
import org.wy.engine.Node
import org.wy.engine.PlatformCanvas
import org.wy.engine.fillOuterRoundRect
import org.wy.engine.strokeOuterRing

/**
 * 标准开关交互基类：复用 [ButtonBase] 的点击 / Enter / Space / disabled / hover / pressed
 * 交互与焦点环时机，这里只增加"开关状态 + 切换"语义。
 *
 * 样式全部可自定义：override [drawContent] / [drawFocusRing]；默认样式见 [switch]。
 */
open class SwitchBase(
    context: StateHolder<Node, List<Node>>,
    enabled: Boolean = true,
) : ButtonBase(context, enabled) {
    /** 当前是否开启，子类从业务状态派生。 */
    open fun isChecked(): Boolean = false

    /** 开启回调：点击 / Enter / Space 统一触发。 */
    open fun setChecked(checked: Boolean) {}

    override fun onClick() = setChecked(!isChecked())
}

/**
 * 默认样式开关工厂。
 *
 * 样式值全部来自 [Theme.current]；需要更自由的视觉时可改用
 * `object : SwitchBase(this) { ... }` 直接覆盖视觉方法。
 */
fun StateHolder<Node, List<Node>>.switch(
    checked: () -> Boolean,
    onChanged: (Boolean) -> Unit,
    enabled: Boolean = true,
    focusOrder: Int? = null,
    width: Float = 40f,
    height: Float = 22f,
): SwitchBase = object : SwitchBase(this, enabled) {
    override val argWidth: LayoutSize get() = LayoutSize(width, false)
    override val argHeight: LayoutSize get() = LayoutSize(height, false)
    override val focusOrder: Int? get() = focusOrder

    override fun isChecked(): Boolean = checked()
    override fun setChecked(v: Boolean) = onChanged(v)

    override fun drawContent(canvas: PlatformCanvas) {
        val c = Theme.current.colors
        val on = isChecked()
        val track = when {
            !enabled -> if (on) c.primaryDisabled else c.surfaceDisabled
            on -> c.primary
            else -> c.track
        }
        fillOuterRoundRect(canvas, height / 2f, track)
        val dx = if (on) width - height else 0f
        canvas.fillOval(dx + 2f, 2f, height - 4f, height - 4f, c.thumb)
    }

    override fun drawFocusRing(canvas: PlatformCanvas) {
        strokeOuterRing(canvas, 2f, height / 2f + 2f, Theme.current.colors.focus, 2f)
    }
}
