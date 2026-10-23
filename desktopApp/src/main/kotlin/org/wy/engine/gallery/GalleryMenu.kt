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
// Select（复用 DropdownBase）
// ════════════════════════════════════════════════════
internal fun StateHolder<Node, List<Node>>.gSelect(
    value: StoreRef<String>,
    options: List<String>,
    focusOrder: Int? = null,
): ButtonBase = object : ButtonBase(this) {
    override val focusOrder: Int? get() = focusOrder
    // 宽度包裹内容：grow 模式 + 不覆写 argWidth（子节点为文本 + 定宽箭头）
    override val directionJustify: DirectionJustify get() = DirectionJustify.grow
    override val argHeight: LayoutSize get() = LayoutSize(32f, false)

    override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
        if (direction == Direction.x) 10f else 0f

    override fun onClick() {
        val anchor = this
        engineGlobal.appendPop { pop ->
            object : DropdownBase(this, anchor) {
                override val panelWidth: Float get() = 150f

                override fun onDismiss() {
                    engineGlobal.removePop(pop)
                }

                override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
                    options.forEach { option ->
                        object : ButtonBase(this) {
                            override val argHeight: LayoutSize get() = LayoutSize(30f, false)
                            override val focusOrder: Int? get() = null

                            override fun onClick() {
                                value.value = option
                                engineGlobal.removePop(pop)
                            }

                            override fun drawContent(canvas: PlatformCanvas) {
                                if (option == value.value || hovered) {
                                    fillOuterRoundRect(canvas, 6f, G_SUBTLE)
                                }
                            }

                            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                                gText(option, 12f, TEXT, if (option == value.value) 600 else 400)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun drawContent(canvas: PlatformCanvas) {
        fillOuterRoundRect(canvas, 7f, CARD)
        strokeOuterRoundRect(canvas, 7f, if (hovered) BAR else BORDER, 1f)
    }

    override fun drawFocusRing(canvas: PlatformCanvas) {
        strokeOuterRing(canvas, 2f, 9f, ACCENT.withAlpha(0.45f), 2f)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        gText(value.value, 12f, TEXT, 500)
        // 下拉箭头（两条斜线）
        object : RectNode(this) {
            override val argWidth: LayoutSize get() = LayoutSize(9f, false)
            override val argHeight: LayoutSize get() = LayoutSize(6f, false)

            override fun draw(canvas: PlatformCanvas) {
                canvas.drawLine(0f, 2f, 4.5f, 6f, TEXT2, 1.4f)
                canvas.drawLine(4.5f, 6f, 9f, 2f, TEXT2, 1.4f)
            }
        }
    }
}

// ════════════════════════════════════════════════════
// Tabs（下划线指示器）
// ════════════════════════════════════════════════════
internal fun StateHolder<Node, List<Node>>.gTabs(
    selected: () -> Int,
    onSelect: (Int) -> Unit,
    items: List<String>,
    focusOrderOffset: Int? = 0
): RectNode = object : RectNode(this), FlexParam {
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.x
    override val directionJustify: DirectionJustify get() = DirectionJustify.start
    override val alignItem: AlignItem get() = AlignItem.stretch
    override val alignFix: Boolean get() = true
    override val argHeight: LayoutSize get() = LayoutSize(36f, false)
    override val gap: Float get() = 4f

    override fun draw(canvas: PlatformCanvas) {
        // 底部 1px 分隔线
        canvas.drawLine(0f, outerHeight - 1f, outerWidth, outerHeight - 1f, BORDER, 1f)
        super.draw(canvas)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        items.forEachIndexed { i, name ->
            object : ButtonBase(this) {
                override val focusOrder: Int? get() = focusOrderOffset?.let { it + i }
                // 标签宽度包裹文本：grow 模式 + 不覆写 argWidth
                override val directionJustify: DirectionJustify get() = DirectionJustify.grow

                private val on get() = selected() == i

                override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                    if (direction == Direction.x) 12f else 0f

                override fun onClick() = onSelect(i)

                override fun drawContent(canvas: PlatformCanvas) {
                    if (!on && hovered) {
                        canvas.fillRoundRect(0f, outerHeight - 34f, outerWidth, 32f, 7f, G_MUTED)
                    }
                    if (on) {
                        canvas.fillRoundRect(
                            0f, outerHeight - 2f, outerWidth, 2f, 1f, TEXT
                        )
                    }
                }

                override fun drawFocusRing(canvas: PlatformCanvas) {
                    strokeOuterRing(canvas, 2f, 9f, ACCENT.withAlpha(0.45f), 2f)
                }

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    gText(name, 12.5f, if (on) TEXT else TEXT2, if (on) 600 else 450)
                }
            }
        }
    }
}
