package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
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
// 组件库展示页（shadcn 风格）
//
// 这里把 helper 里的交互基类（ButtonBase / SwitchBase / SliderBase /
// EditableTextNode / DropdownBase / DialogBase / Toast）按 shadcn 的
// 变体（variant）+ 尺寸（size）模型重新组织成一套可复用组件，
// 页面本身就是这套组件的"活文档"：每个卡片下方的控件都能真点。
// ════════════════════════════════════════════════════

// ── 展示区 token（主题感知 getter，随 demoDark 切换）──
private val G_MUTED: ColorInt get() = if (demoDark.value) rgba(28, 33, 44) else rgba(248, 250, 252)
private val G_SUBTLE: ColorInt get() = GRID
private val G_RING: ColorInt get() = ACCENT
private val G_ON_SOLID: ColorInt get() = rgba(255, 255, 255)

// ════════════════════════════════════════════════════
// 布局辅助
// ════════════════════════════════════════════════════
/**
 * 行容器（主轴 x）。
 *
 * 尺寸契约（与引擎 Flex 布局的既有约定一致，任何一项不满足都会在
 * 父容器尺寸缓存首算时触发「重复进入」死循环）：
 * - 宽度：默认 **包裹内容**（justify=grow，等价 inline-flex）——行内嵌行时，
 *   内行的宽度若改为"从父级拉伸"会去读父行的主轴缓存，而父行缓存正在测量
 *   内行，构成死循环。需要填满剩余宽度时给子节点 `grow = true`（argGrow），
 *   走引擎的 grow 通道（grow 子节点不参与测量，安全）；
 * - 高度：[height] 有定值时子节点纵向拉伸到该高度（alignFix=true）；
 *   无定值时高度包裹最高的子节点（alignFix=false）——此时候选项必须自带定高。
 * - [justify] 显式传 between / end / center 时，行会改为"从父级拉伸"宽度，
 *   此时行的父级必须是纵向容器（卡片 / 列 / 滚动内容），不能是另一行。
 */
private fun StateHolder<Node, List<Node>>.gRow(
    gap: Float = 8f,
    align: AlignItem = AlignItem.center,
    justify: DirectionJustify = DirectionJustify.grow,
    height: Float? = null,
    grow: Boolean = false,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit
) {
    object : RectNode(this), FlexParam, GrowChild {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.x
        override val directionJustify: DirectionJustify get() = justify
        override val alignItem: AlignItem get() = align
        // 有定高：子节点拉伸到该高度；无定高：包裹内容（子节点须自带定高）
        override val alignFix: Boolean get() = height != null
        override val gap: Float get() = gap
        override val argHeight: LayoutSize
            get() = height?.let { LayoutSize(it, false) } ?: super.argHeight

        override fun argGrow(direction: Direction): Float =
            if (grow && direction == Direction.x) 1f else 0f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            content()
        }
    }
}

/**
 * 列容器（主轴 y）。
 *
 * 尺寸契约：
 * - 宽度：始终横向拉伸到父级可用宽（alignFix=true，锚定父级定宽，安全）；
 * - 高度：[height] 有定值时子节点从顶端顺序堆放（justify=start）；
 *   无定值时高度包裹全部子节点（justify=grow，等价 wrap-content）。
 *
 * 注意：不要放在行容器（gRow）里——列的宽度会去读行的主轴缓存，
 * 行的尺寸缓存首算时会造成「重复进入」死循环。需要横向排列的复合布局时，
 * 用带定高的行，或给内容加定宽。
 */
private fun StateHolder<Node, List<Node>>.gCol(
    gap: Float = 8f,
    align: AlignItem = AlignItem.stretch,
    height: Float? = null,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit
) {
    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.y
        // 无定高 → grow 模式：容器高度 = 子节点高度之和（包裹内容）
        override val directionJustify: DirectionJustify
            get() = if (height != null) DirectionJustify.start else DirectionJustify.grow
        override val alignItem: AlignItem get() = align
        override val alignFix: Boolean get() = true
        override val gap: Float get() = gap
        override val argHeight: LayoutSize
            get() = height?.let { LayoutSize(it, false) } ?: super.argHeight

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            content()
        }
    }
}

/**
 * 展示卡片：标题 + 说明 + 一块活的控件区。
 *
 * 高度 **包裹内容**（directionJustify=grow 且无 grow 子节点 → 引擎走
 * 「尺寸从子节点算」通道）：卡片不再写死高度，窄窗口下说明文字换行、
 * 控件多一行都不会被裁掉。宽度由外层卡片行的 grow 通道平分。
 *
 * 注意：因此**行内卡片高度不保证相等**——引擎不支持「量内容高 + 渲染拉伸」
 * 双 Pass（卡片一旦 justify=grow 包裹，父级 stretch 必被 allowSizeFromChildren
 * 短路），这是布局模型的既定边界，接受即可。
 */
private fun StateHolder<Node, List<Node>>.gCard(
    title: String,
    desc: String,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit
): RectNode = object : RectNode(this), FlexParam, GrowChild {
    override fun argGrow(direction: Direction): Float = if (direction == Direction.x) 1f else 0f
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.y
    // 高度包裹内容：子节点（标题/说明/控件区）顺序堆放，总高即卡片高
    override val directionJustify: DirectionJustify get() = DirectionJustify.grow
    override val alignItem: AlignItem get() = AlignItem.stretch
    override val alignFix: Boolean get() = true
    override val gap: Float get() = 8f

    override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
        if (direction == Direction.x) 16f else 14f

    override fun draw(canvas: PlatformCanvas) {
        fillOuterRoundRect(canvas, 12f, CARD)
        strokeOuterRoundRect(canvas, 12f, BORDER, 1f)
        super.draw(canvas)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        // 标题 / 说明换行（autoWidth=false → 从父级拉伸并按容器宽折行）：
        // 卡片是 stretch 列，文本宽度 = 卡片内宽，引擎按该宽排版、高度随之包裹
        gText(title, 13f, TEXT, 650, wrap = true)
        gText(desc, 11f, TEXT2, wrap = true)
        content()
    }
}

/**
 * 展示用文本。[wrap] = true 时宽度从父级拉伸并自动折行（卡片内文案）；
 * 默认 autoWidth（宽度=文本宽，用于行内标签 / 徽标内文字等包裹场景。
 * 注意：放在「包裹行」（justify=grow 且无 grow 子节点）里的文本必须保持
 * autoWidth——否则文本宽度要从行分配，而行宽正在量文本，构成重复进入。
 */
private fun StateHolder<Node, List<Node>>.gText(
    label: String,
    size: Float = 12f,
    color: ColorInt = TEXT,
    weight: Int = 400,
    wrap: Boolean = false
): WrappedTextNode = object : WrappedTextNode(this) {
    override val autoWidth: Boolean get() = !wrap
    override val text: String get() = label
    override val fontSize: Float get() = size
    override val color: ColorInt get() = color
    override val fontWeight: Int get() = weight
}

// ════════════════════════════════════════════════════
// Button：5 个变体 + 2 个尺寸
// ════════════════════════════════════════════════════
internal enum class BtnKind { Default, Secondary, Outline, Ghost, Destructive }

private fun StateHolder<Node, List<Node>>.gButton(
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
                        pressed -> rgba(226, 232, 240)
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
private enum class BadgeKind { Default, Secondary, Outline, Success, Warning, Danger }

private fun StateHolder<Node, List<Node>>.gBadge(label: String, kind: BadgeKind): RectNode =
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

private fun StateHolder<Node, List<Node>>.gAvatar(
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
private fun StateHolder<Node, List<Node>>.gInput(
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

private fun StateHolder<Node, List<Node>>.gTextarea(
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

// ════════════════════════════════════════════════════
// 选择控件
// ════════════════════════════════════════════════════
private fun StateHolder<Node, List<Node>>.gCheckbox(
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

private fun StateHolder<Node, List<Node>>.gRadio(
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

private fun StateHolder<Node, List<Node>>.gSwitch(
    checked: () -> Boolean,
    onChanged: (Boolean) -> Unit,
    focusOrder: Int? = null,
) = switch(checked, onChanged, focusOrder = focusOrder, width = 38f, height = 21f)

/** 一行「控件 + 文案」的表单项。 */
private fun StateHolder<Node, List<Node>>.gField(
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
private fun StateHolder<Node, List<Node>>.gProgress(
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
private fun StateHolder<Node, List<Node>>.gSeparator() {
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
private fun StateHolder<Node, List<Node>>.gSkeleton(
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
private fun StateHolder<Node, List<Node>>.gTableCell(
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

// ════════════════════════════════════════════════════
// 页面
// ════════════════════════════════════════════════════
/** 卡片行断点：窗口内宽小于此值时两张卡片拆成上下两行（各自拉满宽度）。 */
private const val G_CARD_ROW_BREAKPOINT = 880f

/**
 * 一对卡片的排放：宽窗口同行（grow 平分宽度），窄窗口拆两行。
 *
 * 两行各只有一个 grow 子节点（卡片），remaing = 整行内宽 → 卡片拉满。
 */
private fun StateHolderWithNode<Node, List<Node>>.gCardRow(
    narrow: Boolean,
    first: StateHolderWithNode<Node, List<Node>>.() -> Unit,
    second: StateHolderWithNode<Node, List<Node>>.() -> Unit
) {
    if (!narrow) {
        gRow(gap = 14f, align = AlignItem.start) {
            first()
            second()
        }
    } else {
        gRow(gap = 14f, align = AlignItem.start) { first() }
        gRow(gap = 14f, align = AlignItem.start) { second() }
    }
}

internal fun StateHolder<Node, List<Node>>.dGalleryPage(
    active: StoreRef<DemoTab>,
    searchSeed: String,
    windowWidth: () -> Float,
): RectNode = object : RectNode(this), FlexParam, GrowChild {
    override val hide: Boolean get() = active.value != DemoTab.COMPONENTS
    override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f

    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.y
    override val directionJustify: DirectionJustify get() = DirectionJustify.start
    override val alignItem: AlignItem get() = AlignItem.stretch
    override val alignFix: Boolean get() = true

    // ── 交互状态提升到页面级 ──
    // 内容子树会在窗口宽度变化时整体重建（galleryContent 里读了宽度信号），
    // createSignal 若放在子树里就会随重建丢失（输入框文字 / 勾选态归零）。
    // 提升到这里：节点只构造一次，信号跨重建存活。
    private val textValue = createSignal(searchSeed)
    private val noteValue = createSignal("")
    private val checkedA = createSignal(true)
    private val checkedB = createSignal(false)
    private val radio = createSignal(1)
    private val notif = createSignal(true)
    private val marketing = createSignal(false)
    private val level = createSignal(0.62f)
    private val plan = createSignal("专业版")
    private val tab = createSignal(0)

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        // 页面自己滚动：正好用来体验浮层滚动条
        object : SimpleScrollNode(this) {
            override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f
            override val contentGap: Float get() = 14f

            override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
                galleryContent(
                    narrow = windowWidth() < G_CARD_ROW_BREAKPOINT,
                    textValue = textValue,
                    noteValue = noteValue,
                    checkedA = checkedA,
                    checkedB = checkedB,
                    radio = radio,
                    notif = notif,
                    marketing = marketing,
                    level = level,
                    plan = plan,
                    tab = tab,
                )
            }
        }
    }
}

private fun StateHolderWithNode<Node, List<Node>>.galleryContent(
    narrow: Boolean,
    textValue: StoreRef<String>,
    noteValue: StoreRef<String>,
    checkedA: StoreRef<Boolean>,
    checkedB: StoreRef<Boolean>,
    radio: StoreRef<Int>,
    notif: StoreRef<Boolean>,
    marketing: StoreRef<Boolean>,
    level: StoreRef<Float>,
    plan: StoreRef<String>,
    tab: StoreRef<Int>,
) {
    // 页头
    gCol(gap = 4f) {
        gRow(gap = 8f) {
            gText("组件库", 19f, TEXT, 700)
            gBadge("shadcn/ui 风格", BadgeKind.Secondary)
            gBadge("27 个组件", BadgeKind.Outline)
        }
        gText(
            "交互基类在 skia-engine，样式与变体在 demo 层——按下 / 悬停 / 聚焦 / 禁用四态齐全，Tab 可逐项遍历。",
            11.5f, TEXT2, wrap = true
        )
    }

    // ── 第一行：按钮 / 徽标 ──────────────────────────
    gCardRow(narrow,
        {
            gCard(
                "Button 按钮",
                "5 个变体 × 2 个尺寸，宽度随内容自适应"
            ) {
                gRow(gap = 8f) {
                    gButton("主要按钮", BtnKind.Default, focusOrder = 101)
                    gButton("次要", BtnKind.Secondary, focusOrder = 102)
                    gButton("描边", BtnKind.Outline, focusOrder = 103)
                }
                gRow(gap = 8f) {
                    gButton("幽灵", BtnKind.Ghost, focusOrder = 104)
                    gButton("危险操作", BtnKind.Destructive, focusOrder = 105)
                    gButton("不可用", BtnKind.Default, enabled = false)
                }
                gRow(gap = 8f) {
                    gButton("小尺寸", BtnKind.Secondary, small = true, focusOrder = 106)
                    gButton("间距一致", BtnKind.Outline, small = true, focusOrder = 107)
                    gText("hover / 按下 / 焦点环", 10.5f, PLACEHOLDER)
                }
            }
        },
        {
            gCard(
                "Badge & Avatar 徽标与头像",
                "徽标宽度由文本撑起，头像支持叠状态点"
            ) {
                gRow(gap = 6f) {
                    gBadge("默认", BadgeKind.Default)
                    gBadge("次要", BadgeKind.Secondary)
                    gBadge("描边", BadgeKind.Outline)
                }
                gRow(gap = 6f) {
                    gBadge("已完成", BadgeKind.Success)
                    gBadge("待处理", BadgeKind.Warning)
                    gBadge("已逾期", BadgeKind.Danger)
                }
                gRow(gap = 8f) {
                    gAvatar("WY", ACCENT)
                    gAvatar("KT", GREEN)
                    gAvatar("Z", AMBER, withDot = true)
                    gText("30px · 圆形裁切", 10.5f, PLACEHOLDER)
                }
            }
        }
    )

    // ── 第二行：输入 / 选择 ──────────────────────────
    gCardRow(narrow,
        {
            gCard(
                "Input & Textarea 输入",
                "单行不折行、多行自动折行；聚焦时外扩焦点环"
            ) {
                gRow(gap = 8f) {
                    gInput(textValue, "请输入关键词…", focusOrder = 111)
                    gButton("搜索", BtnKind.Default, small = true, focusOrder = 112)
                }
                gTextarea(noteValue, "写点什么…（多行，可换行）", focusOrder = 113, height = 66f)
            }
        },
        {
            gCard(
                "Checkbox & Radio & Switch 选择",
                "三个基类共用 ButtonBase 的键盘与焦点语义"
            ) {
                gField("接收站内通知") {
                    gCheckbox({ checkedA.value }, { checkedA.value = it }, focusOrder = 121)
                }
                gField("订阅产品更新") {
                    gCheckbox({ checkedB.value }, { checkedB.value = it }, focusOrder = 122)
                }
                gRow(gap = 14f, height = 22f) {
                    gField("月度") { gRadio({ radio.value == 0 }, { radio.value = 0 }) }
                    gField("季度") { gRadio({ radio.value == 1 }, { radio.value = 1 }) }
                    gField("年度") { gRadio({ radio.value == 2 }, { radio.value = 2 }) }
                }
                gField("通知推送") {
                    gSwitch({ notif.value }, { notif.value = it }, focusOrder = 123)
                }
                gField("营销邮件") {
                    gSwitch({ marketing.value }, { marketing.value = it }, focusOrder = 124)
                }
            }
        }
    )

    // ── 第三行：滑杆 / 进度 / 下拉 ───────────────────
    gCardRow(narrow,
        {
            gCard(
                "Slider & Progress 滑杆与进度",
                "方向键可微调 5%，拖拽按像素换算"
            ) {
                gRow(gap = 10f) {
                    gText("专注强度", 12f, TEXT, 500)
                    slider({ level.value }, { level.value = it }, focusOrder = 131, grow = true)
                    gText("${(level.value * 100).toInt()}%", 11.5f, TEXT2)
                }
                gRow(gap = 8f, justify = DirectionJustify.between) {
                    gText("本周目标", 11.5f, TEXT2)
                    gText("${(level.value * 40).toInt()} / 40h", 11.5f, TEXT2, 550)
                }
                gProgress({ level.value })
                gProgress({ level.value * 0.6f }, color = GREEN)
                gProgress({ level.value * 0.3f }, color = AMBER)
            }
        },
        {
            gCard(
                "Select 下拉选择",
                "面板锚定触发器，点击外部 / Esc 关闭"
            ) {
                gRow(gap = 8f) {
                    gText("当前套餐", 12f, TEXT2)
                    gSelect(plan, listOf("免费版", "专业版", "团队版", "企业版"), focusOrder = 141)
                }
                gRow(gap = 6f) {
                    gBadge("按 Esc 关闭", BadgeKind.Outline)
                    gBadge("点击外部关闭", BadgeKind.Outline)
                }
                gText("面板内可 Tab 遍历，关闭后焦点回到触发器。", 10.5f, PLACEHOLDER, wrap = true)
            }
        }
    )

    // ── 第四行：弹层 / 标签页 ────────────────────────
    gCardRow(narrow,
        {
            gCard(
                "Dialog & Toast 弹层",
                "模态圈定焦点，轻提示整层不拦截命中"
            ) {
                gRow(gap = 8f) {
                    gButton("打开对话框", BtnKind.Outline, small = true, focusOrder = 151) {
                        val g = consume(engineGlobalContext)!!
                        g.appendPop { pop ->
                            dialog(onClose = { g.removePop(pop) }, width = 330f) {
                                gText("确认删除？", 15f, TEXT, 700)
                                gText("删除后不可恢复，该操作会同时移除关联的 12 条记录。", 12f, TEXT2, wrap = true)
                                gRow(gap = 8f, justify = DirectionJustify.end) {
                                    gButton("取消", BtnKind.Secondary, small = true) {
                                        g.removePop(pop)
                                    }
                                    gButton("确认删除", BtnKind.Destructive, small = true) {
                                        g.removePop(pop)
                                    }
                                }
                            }
                        }
                    }
                    gButton("显示 Toast", BtnKind.Secondary, small = true, focusOrder = 152) {
                        toast(durationMs = 2200L) {
                            gText("已保存到本地 · 2 秒后自动消失", 12.5f, rgba(255, 255, 255))
                        }
                    }
                }
                gText("Esc / 点遮罩关闭；对话框打开期间 Tab 不会跑出面板。", 10.5f, PLACEHOLDER, wrap = true)
            }
        },
        {
            gCard(
                "Tabs 标签页",
                "下划线指示器随选中项移动"
            ) {
                gTabs(
                    selected = { tab.value },
                    onSelect = { tab.value = it },
                    items = listOf("概览", "成员", "计费"),
                    focusOrderOffset = 161
                )
                gText(
                    when (tab.value) {
                        0 -> "概览：项目进度 68%，本周新增 12 条记录。"
                        1 -> "成员：3 人协作中，2 个待处理邀请。"
                        else -> "计费：专业版 · 下次扣费 11 月 12 日。"
                    },
                    11.5f, TEXT2, wrap = true
                )
            }
        }
    )

    // ── 第五行：表格 / 分隔线与骨架屏 ──────────────────
    gCardRow(narrow,
        {
            gCard(
                "Table 表格",
                "定宽单元格右对齐数值，分隔线划分表头与行"
            ) {
                // 表头
                gRow(gap = 8f, height = 24f, align = AlignItem.stretch) {
                    gTableCell("项目", 96f, TEXT2, 600)
                    gTableCell("状态", 56f, TEXT2, 600)
                    gTableCell("数值", 68f, TEXT2, 600, TextAlign.END)
                }
                gSeparator()
                // 数据行
                gRow(gap = 8f, height = 26f, align = AlignItem.stretch) {
                    gTableCell("项目 A", 96f)
                    gTableCell("已上线", 56f, GREEN, 500)
                    gTableCell("12", 68f, TEXT, 550, TextAlign.END)
                }
                gSeparator()
                gRow(gap = 8f, height = 26f, align = AlignItem.stretch) {
                    gTableCell("项目 B", 96f)
                    gTableCell("灰度中", 56f, AMBER, 500)
                    gTableCell("48", 68f, TEXT, 550, TextAlign.END)
                }
                gSeparator()
                gRow(gap = 8f, height = 26f, align = AlignItem.stretch) {
                    gTableCell("项目 C", 96f)
                    gTableCell("规划中", 56f, TEXT2, 500)
                    gTableCell("7", 68f, TEXT, 550, TextAlign.END)
                }
            }
        },
        {
            gCard(
                "Separator & Skeleton 分隔线与骨架屏",
                "分隔线横向拉伸；骨架屏模拟加载占位"
            ) {
                gText("账户设置", 12f, TEXT, 600)
                gSeparator()
                gText("资料与安全", 11.5f, TEXT2)
                gSeparator()
                // 骨架屏：头像 + 标题 + 多行文本占位
                gRow(gap = 10f, height = 34f) {
                    gSkeleton(34f, 34f, 17f)
                    gSkeleton(12f, 110f)
                }
                gSkeleton(11f)
                gSkeleton(11f)
                gSkeleton(11f, 180f)
            }
        }
    )
}

// ════════════════════════════════════════════════════
// Select（复用 DropdownBase）
// ════════════════════════════════════════════════════
private fun StateHolder<Node, List<Node>>.gSelect(
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
private fun StateHolder<Node, List<Node>>.gTabs(
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
