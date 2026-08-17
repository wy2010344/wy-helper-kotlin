package org.wy.engine.helper

import com.wy.mve.StateHolder
import org.wy.engine.Direction
import org.wy.engine.EditableTextNode
import org.wy.engine.LayoutSize
import org.wy.engine.Node
import org.wy.engine.PlatformCanvas
import org.wy.engine.PointerDevice
import org.wy.engine.StartEnd
import org.wy.engine.fillOuterRoundRect
import org.wy.engine.include
import org.wy.engine.isFocused
import org.wy.engine.strokeOuterRoundRect
import org.wy.signal.getValue
import org.wy.signal.memo

/**
 * 单行输入框工厂：基于 [EditableTextNode]，交互（光标 / 选区 / IME / 键盘）已内置，
 * 这里只提供默认视觉（背景 + 边框，聚焦主色描边、悬停描边、其余常规描边）与尺寸。
 *
 * 值通过 [value] 读取、[onChange] 写回，采用闭包而非 StoreRef，便于对接任意状态容器。
 */
fun StateHolder<Node, List<Node>>.textField(
    value: () -> String,
    onChange: (String) -> Unit,
    focusOrder: Int? = null,
    width: Float = 240f,
    height: Float = 34f,
): EditableTextNode = object : EditableTextNode(this) {
    override var text: String
        get() = value()
        set(v) {
            onChange(v)
        }

    override val singleLine: Boolean = true
    override val fontSize: Float = Theme.current.textSize.label
    override val focusOrder: Int? = focusOrder
    override val argWidth: LayoutSize = LayoutSize(width, false)
    override val argHeight: LayoutSize = LayoutSize(height, false)

    private val g = engineGlobal
    private val hovered by memo {
        g.moveHitTest?.include(this) == true && g.lastPointerDevice != PointerDevice.Touch
    }

    override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
        if (direction == Direction.x) 10f else 6f

    override fun draw(canvas: PlatformCanvas) {
        val c = Theme.current.colors
        val r = Theme.current.radius.control
        fillOuterRoundRect(canvas, r, c.surface)
        strokeOuterRoundRect(
            canvas, r,
            when {
                isFocused -> c.focus
                hovered -> c.borderHover
                else -> c.border
            },
            if (isFocused) 2f else 1f
        )
        super.draw(canvas)
    }
}
