package org.wy.engine.gallery

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.helper.ButtonBase
import org.wy.engine.helper.DropdownBase
import org.wy.engine.helper.SimpleScrollNode
import org.wy.engine.helper.SwitchBase
import org.wy.engine.helper.dialog
import org.wy.engine.helper.slider
import org.wy.engine.helper.switch
import org.wy.engine.helper.textField
import org.wy.engine.helper.toast
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.GrowChild
import org.wy.engine.layout.LayoutDirection
import org.wy.lib.StoreRef
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.memo

// ════════════════════════════════════════════════════
// 选择控件
// ════════════════════════════════════════════════════
internal fun StateHolder<Node, List<Node>>.gCheckbox(
    checked: () -> Boolean,
    onChanged: (Boolean) -> Unit,
    enabled: Boolean = true,
    focusOrder: Int? = null,
): SwitchBase = object : SwitchBase(this) {
    override val enabled: Boolean get() = enabled
    override val focusOrder: Int? get() = focusOrder
    override val argWidth: LayoutSize get() = LayoutSize(17f, false)
    override val argHeight: LayoutSize get() = LayoutSize(17f, false)

    override fun isChecked(): Boolean = checked()
    override fun setChecked(v: Boolean) = onChanged(v)

    override fun drawContent(canvas: PlatformCanvas) {
        val on = isChecked()
        if (on) {
            fillOuterRoundRect(canvas, 4.5f, if (enabled) ACCENT else ACCENT_LIGHT)
        } else {
            fillOuterRoundRect(canvas, 4.5f, CARD)
            strokeOuterRoundRect(canvas, 4.5f, if (hovered && enabled) BAR else BORDER, 1f)
        }
        if (on) {
            canvas.drawLine(4.2f, 8.6f, 7.4f, 11.9f, G_ON_SOLID, 1.9f)
            canvas.drawLine(7.4f, 11.9f, 12.8f, 5.2f, G_ON_SOLID, 1.9f)
        }
    }

    override fun drawFocusRing(canvas: PlatformCanvas) {
        strokeOuterRing(canvas, 2f, 6.5f, ACCENT.withAlpha(0.45f), 2f)
    }
}

internal fun StateHolder<Node, List<Node>>.gRadio(
    selected: () -> Boolean,
    onSelect: () -> Unit,
    focusOrder: Int? = null,
): SwitchBase = object : SwitchBase(this) {
    override val focusOrder: Int? get() = focusOrder
    override val argWidth: LayoutSize get() = LayoutSize(17f, false)
    override val argHeight: LayoutSize get() = LayoutSize(17f, false)

    override fun isChecked(): Boolean = selected()
    override fun setChecked(v: Boolean) {
        if (v) onSelect()
    }

    // 单选：已选中的再点不取消
    override fun onClick() {
        if (!isChecked()) onSelect()
    }

    override fun drawContent(canvas: PlatformCanvas) {
        val on = isChecked()
        if (on) {
            fillOuterOval(canvas, ACCENT)
            canvas.fillOval(5.5f, 5.5f, 6f, 6f, G_ON_SOLID)
        } else {
            canvas.strokeOval(
                0.7f, 0.7f, 15.6f, 15.6f,
                if (hovered) BAR else BORDER, 1.4f
            )
        }
    }

    override fun drawFocusRing(canvas: PlatformCanvas) {
        strokeOuterRing(canvas, 2f, 6.5f, ACCENT.withAlpha(0.45f), 2f)
    }
}

internal fun StateHolder<Node, List<Node>>.gSwitch(
    checked: () -> Boolean,
    onChanged: (Boolean) -> Unit,
    focusOrder: Int? = null,
) = switch(checked, onChanged, focusOrder = focusOrder, width = 38f, height = 21f)

/** 一行「控件 + 文案」的表单项。 */
internal fun StateHolder<Node, List<Node>>.gField(
    label: String,
    hintText: String? = null,
    control: StateHolderWithNode<Node, List<Node>>.() -> Unit
) {
    gRow(gap = 8f) {
        control()
        gText(label, 12f, TEXT, 500)
        if (hintText != null) gText(hintText, 11f, TEXT2)
    }
}

// ════════════════════════════════════════════════════
// Progress
// ════════════════════════════════════════════════════
/**
 * 进度条：宽度 **从父级拉伸**（卡片内宽），高度定值 7。
 *
 * directionJustify 必须显式给 start/center/end——默认 grow 会让
 * allowSizeFromChildren=true，而无子节点时「尺寸从子节点算」得到 0。
 */
internal fun StateHolder<Node, List<Node>>.gProgress(
    value: () -> Float,
    color: ColorInt = ACCENT,
): RectNode = object : RectNode(this), FlexParam {
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.x
    override val directionJustify: DirectionJustify get() = DirectionJustify.start
    override val alignFix: Boolean get() = true
    override val argHeight: LayoutSize get() = LayoutSize(7f, false)

    override fun draw(canvas: PlatformCanvas) {
        val v = value().coerceIn(0f, 1f)
        fillOuterRoundRect(canvas, 3.5f, G_SUBTLE)
        if (v > 0f) {
            canvas.fillRoundRect(0f, 0f, outerWidth * v, outerHeight, 3.5f, color)
        }
    }
}

// ════════════════════════════════════════════════════
// Separator / Skeleton / Table
// ════════════════════════════════════════════════════
/**
 * 分隔线（水平）。固定 1px 高、横向拉伸到父级内宽（放在 gCol 等纵向拉伸容器里）。
 */
internal fun StateHolder<Node, List<Node>>.gSeparator() {
    object : RectNode(this) {
        override val argHeight: LayoutSize get() = LayoutSize(1f, false)
        override fun draw(canvas: PlatformCanvas) {
            canvas.fillRect(0f, 0f, outerWidth, outerHeight, BORDER)
        }
    }
}

/**
 * 骨架屏占位块。[width] 为 null 时横向拉伸到父级内宽（纵向容器里安全）；
 * 放在 gRow 里请给固定宽度，否则会去读行的主轴缓存。
 */
internal fun StateHolder<Node, List<Node>>.gSkeleton(
    height: Float,
    width: Float? = null,
    radius: Float = 6f,
) {
    object : RectNode(this) {
        override val argHeight: LayoutSize get() = LayoutSize(height, false)
        override val argWidth: LayoutSize
            get() = width?.let { LayoutSize(it, false) } ?: super.argWidth
        override fun draw(canvas: PlatformCanvas) {
            fillOuterRoundRect(canvas, radius, G_SUBTLE)
        }
    }
}

/**
 * 表格单元格：定宽、纵向居中；内部文本横向拉伸到单元格宽（纵向 alignFix 列，安全），
 * [align] 控制水平对齐（数值列用 END 右对齐）。
 */
internal fun StateHolder<Node, List<Node>>.gTableCell(
    label: String,
    width: Float,
    color: ColorInt = TEXT2,
    weight: Int = 500,
    align: TextAlign = TextAlign.START,
): RectNode = object : RectNode(this), FlexParam {
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.y
    override val directionJustify: DirectionJustify get() = DirectionJustify.center
    override val alignItem: AlignItem get() = AlignItem.stretch
    override val alignFix: Boolean get() = true
    override val argWidth: LayoutSize get() = LayoutSize(width, false)

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        object : WrappedTextNode(this) {
            override val autoWidth: Boolean get() = false // 宽度从单元格拉伸
            override val text: String get() = label
            override val fontSize: Float get() = 11.5f
            override val color: ColorInt get() = color
            override val fontWeight: Int get() = weight
            override val textAlign: TextAlign get() = align
        }
    }
}

