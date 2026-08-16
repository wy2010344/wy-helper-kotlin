package org.wy.engine

import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.helper.ButtonBase
import org.wy.engine.helper.textField
import org.wy.lib.StoreRef
import org.wy.signal.getValue
import org.wy.signal.memo

// ════════════════════════════════════════════════════
// 基础控件
// ════════════════════════════════════════════════════
internal fun StateHolder<Node,List<Node>>.dLabel(
    text: () -> String,
    size: Float = 13f,
    color: ColorInt = TEXT,
    weight: Int = 400,
    width: Float? = null
): WrappedTextNode = object : WrappedTextNode(this) {
    override val autoWidth: Boolean get() = true
    override val text: String get() = text()
    override val fontSize: Float get() = size
    override val color: ColorInt get() = color
    override val fontWeight: Int get() = weight
    override val argWidth: LayoutSize get() = width?.let { LayoutSize(it, false) } ?: super.argWidth
}

internal fun StateHolder<Node,List<Node>>.dButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean = true,
    width: Float = 90f,
    height: Float = 32f,
    focusOrder: Int? = null
): ButtonBase = object : ButtonBase(this) {
    override val argWidth: LayoutSize get() = LayoutSize(width, false)
    override val argHeight: LayoutSize get() = LayoutSize(height, false)
    override val focusOrder: Int? get() = focusOrder

    override fun onClick() = onClick()

    override fun drawContent(canvas: PlatformCanvas) {
        val bg = when {
            pressed -> if (primary) ACCENT_DARK else GRID
            hovered -> if (primary) ACCENT_HOVER else rgba(241, 245, 249)
            else -> if (primary) ACCENT else CARD
        }
        fillOuterRoundRect(canvas, 8f, bg)
        strokeOuterRoundRect(canvas, 8f, if (primary) bg else BORDER, 1f)
    }

    override fun drawFocusRing(canvas: PlatformCanvas) {
        strokeOuterRing(canvas, 2f, 10f, ACCENT, 2f)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        dLabel({ label }, 13f, if (primary) rgba(255, 255, 255) else TEXT, 600)
    }
}

internal fun StateHolder<Node,List<Node>>.dTextField(value: StoreRef<String>, focusOrder: Int): EditableTextNode =
    textField({ value.value }, { value.value = it }, focusOrder = focusOrder)
