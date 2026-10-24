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
// Button：5 个变体 + 2 个尺寸
// ════════════════════════════════════════════════════
internal enum class BtnKind { Default, Secondary, Outline, Ghost, Destructive }

internal fun StateHolder<Node, List<Node>>.gButton(
    label: String,
    kind: BtnKind = BtnKind.Default,
    enabled: Boolean = true,
    small: Boolean = false,
    focusOrder: Int? = null,
    onClick: () -> Unit = {}
): ButtonBase = object : ButtonBase(this) {
    override val enabled: Boolean get() = enabled
    override val focusOrder: Int? get() = focusOrder

    // 宽度由内容撑起（shadcn 按钮没有固定宽度）：主轴 grow 模式，
    // 不覆写 argWidth —— 由 allowSizeFromChildren 走「尺寸从子节点算」通道。
    // 注意不能写 LayoutSize(0f, true)：那表示"内尺寸为 0"，外层只剩 padding，文字会溢出。
    override val directionJustify: DirectionJustify get() = DirectionJustify.grow
    override val argHeight: LayoutSize get() = LayoutSize(if (small) 30f else 34f, false)

    override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
        if (direction == Direction.x) (if (small) 10f else 14f) else 0f

    override fun onClick() = onClick()

    override fun drawContent(canvas: PlatformCanvas) {
        val radius = 8f
        when (kind) {
            BtnKind.Default -> fillOuterRoundRect(
                canvas, radius,
                when {
                    !enabled -> ACCENT_LIGHT
                    pressed -> ACCENT_DARK
                    hovered -> ACCENT_HOVER
                    else -> ACCENT
                }
            )

            BtnKind.Secondary -> {
                fillOuterRoundRect(
                    canvas, radius,
                    when {
                        !enabled -> G_SUBTLE
                        // 按下底色随昼夜切换：夜间用深灰，否则浅底压浅字不可见
                        pressed -> if (demoDark.value) rgba(52, 58, 74) else rgba(226, 232, 240)
                        hovered -> G_SUBTLE
                        else -> CARD
                    }
                )
                strokeOuterRoundRect(canvas, radius, if (enabled) BORDER else G_SUBTLE, 1f)
            }

            BtnKind.Outline -> {
                fillOuterRoundRect(canvas, radius, if (hovered) G_MUTED else CARD)
                strokeOuterRoundRect(canvas, radius, if (hovered) BAR else BORDER, 1f)
            }

            BtnKind.Ghost -> {
                if (hovered || pressed) {
                    fillOuterRoundRect(canvas, radius, if (pressed) G_SUBTLE else G_MUTED)
                }
            }

            BtnKind.Destructive -> fillOuterRoundRect(
                canvas, radius,
                when {
                    !enabled -> rgba(254, 202, 202)
                    pressed -> rgba(185, 28, 28)
                    hovered -> rgba(220, 38, 38)
                    else -> RED
                }
            )
        }
    }

    override fun drawFocusRing(canvas: PlatformCanvas) {
        strokeOuterRing(canvas, 2f, 10f, G_RING.withAlpha(0.55f), 2f)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        val fg = when {
            !enabled -> PLACEHOLDER
            kind == BtnKind.Default -> G_ON_SOLID
            kind == BtnKind.Destructive -> G_ON_SOLID
            else -> TEXT
        }
        gText(label, if (small) 12f else 13f, fg, 550)
    }
}

// ════════════════════════════════════════════════════
// Badge / Avatar
// ════════════════════════════════════════════════════
internal enum class BadgeKind { Default, Secondary, Outline, Success, Warning, Danger }

internal fun StateHolder<Node, List<Node>>.gBadge(label: String, kind: BadgeKind): RectNode =
    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.x
        override val directionJustify: DirectionJustify get() = DirectionJustify.grow
        override val alignItem: AlignItem get() = AlignItem.center
        override val alignFix: Boolean get() = true
        // 宽度 = 文本宽 + 左右 padding：grow 模式下不覆写 argWidth，
        // 由「尺寸从子节点算」通道给出（文本 autoWidth 是确定值）
        override val argHeight: LayoutSize get() = LayoutSize(20f, false)

        override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
            if (direction == Direction.x) 8f else 0f

        override fun draw(canvas: PlatformCanvas) {
            val bg = when (kind) {
                BadgeKind.Default -> ACCENT
                BadgeKind.Secondary -> G_SUBTLE
                BadgeKind.Outline -> CARD
                BadgeKind.Success -> rgba(220, 252, 231)
                BadgeKind.Warning -> rgba(254, 243, 199)
                BadgeKind.Danger -> rgba(254, 226, 226)
            }
            fillOuterRoundRect(canvas, 10f, bg)
            if (kind == BadgeKind.Outline) strokeOuterRoundRect(canvas, 10f, BORDER, 1f)
            super.draw(canvas)
        }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            val fg = when (kind) {
                BadgeKind.Default -> G_ON_SOLID
                BadgeKind.Secondary -> TEXT2
                BadgeKind.Outline -> TEXT
                BadgeKind.Success -> rgba(21, 128, 61)
                BadgeKind.Warning -> rgba(180, 83, 9)
                BadgeKind.Danger -> rgba(185, 28, 28)
            }
            gText(label, 10.5f, fg, 600)
        }
    }

internal fun StateHolder<Node, List<Node>>.gAvatar(
    initials: String,
    color: ColorInt,
    withDot: Boolean = false,
): RectNode =
    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.x
        override val directionJustify: DirectionJustify get() = DirectionJustify.center
        override val alignItem: AlignItem get() = AlignItem.center
        override val alignFix: Boolean get() = true
        override val argWidth: LayoutSize get() = LayoutSize(30f, false)
        override val argHeight: LayoutSize get() = LayoutSize(30f, false)

        override fun draw(canvas: PlatformCanvas) {
            fillOuterOval(canvas, color)
            super.draw(canvas)
            if (withDot) {
                // 状态点：直接画在头像右下角，用底色描一圈"挖空"效果
                canvas.fillOval(20f, 20f, 12f, 12f, CARD)
                canvas.fillOval(22f, 22f, 8f, 8f, GREEN)
            }
        }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            gText(initials, 11f, G_ON_SOLID, 650)
        }
    }

// ════════════════════════════════════════════════════
// 输入类
// ════════════════════════════════════════════════════
internal fun StateHolder<Node, List<Node>>.gInput(
    value: StoreRef<String>,
    placeholderText: String,
    focusOrder: Int? = null,
    enabled: Boolean = true,
    height: Float = 34f,
): EditableTextNode = object : EditableTextNode(this), GrowChild {
    override var text: String
        get() = value.value
        set(v) {
            value.value = v
        }

    override val fontSize: Float = 12.5f
    override val focusOrder: Int? get() = focusOrder
    override val singleLine: Boolean = true
    override val focusable: Boolean get() = enabled
    override val placeholderColor: ColorInt = PLACEHOLDER
    override val argHeight: LayoutSize get() = LayoutSize(height, false)

    // 宽度走 grow 通道：行内其他子节点（按钮 / 标签）先按内容占位，
    // 输入框拿剩余宽度——不写死宽度，窄窗口自动收缩
    override fun argGrow(direction: Direction): Float =
        if (direction == Direction.x) 1f else 0f

    init {
        placeholder = placeholderText
    }

    private val g = engineGlobal
    private val hovered by memo { g.moveHitTest?.include(this) ?: false }

    override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
        if (direction == Direction.x) 10f else 6f

    override fun draw(canvas: PlatformCanvas) {
        val on = isFocused
        fillOuterRoundRect(canvas, 7f, if (enabled) CARD else G_MUTED)
        if (on) {
            // shadcn 的 focus-visible ring：外扩一圈淡主色 + 主色描边
            strokeOuterRing(canvas, 2f, 9f, ACCENT.withAlpha(0.28f), 2f)
            strokeOuterRoundRect(canvas, 7f, ACCENT, 1f)
        } else {
            strokeOuterRoundRect(canvas, 7f, if (hovered && enabled) BAR else BORDER, 1f)
        }
        super.draw(canvas)
    }
}

internal fun StateHolder<Node, List<Node>>.gTextarea(
    value: StoreRef<String>,
    placeholderText: String,
    focusOrder: Int? = null,
    height: Float = 74f,
): EditableTextNode = object : EditableTextNode(this) {
    override var text: String
        get() = value.value
        set(v) {
            value.value = v
        }

    override val fontSize: Float = 12.5f
    override val focusOrder: Int? get() = focusOrder
    override val singleLine: Boolean = false
    override val placeholderColor: ColorInt = PLACEHOLDER
    override val argHeight: LayoutSize get() = LayoutSize(height, false)

    init {
        placeholder = placeholderText
    }

    private val g = engineGlobal
    private val hovered by memo { g.moveHitTest?.include(this) ?: false }

    override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
        if (direction == Direction.x) 10f else 8f

    override fun draw(canvas: PlatformCanvas) {
        fillOuterRoundRect(canvas, 7f, CARD)
        if (isFocused) {
            strokeOuterRing(canvas, 2f, 9f, ACCENT.withAlpha(0.28f), 2f)
            strokeOuterRoundRect(canvas, 7f, ACCENT, 1f)
        } else {
            strokeOuterRoundRect(canvas, 7f, if (hovered) BAR else BORDER, 1f)
        }
        super.draw(canvas)
    }
}

