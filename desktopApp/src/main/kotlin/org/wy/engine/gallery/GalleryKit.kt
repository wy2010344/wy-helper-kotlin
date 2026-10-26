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
// 组件库展示页（shadcn 风格）
//
// 这里把 helper 里的交互基类（ButtonBase / SwitchBase / SliderBase /
// EditableTextNode / DropdownBase / DialogBase / Toast）按 shadcn 的
// 变体（variant）+ 尺寸（size）模型重新组织成一套可复用组件，
// 页面本身就是这套组件的"活文档"：每个卡片下方的控件都能真点。
// ════════════════════════════════════════════════════

// ── 展示区 token（主题感知 getter，随 demoDark 切换）──
internal val G_MUTED: ColorInt get() = if (demoDark.value) rgba(28, 33, 44) else rgba(248, 250, 252)
internal val G_SUBTLE: ColorInt get() = GRID
internal val G_RING: ColorInt get() = ACCENT
internal val G_ON_SOLID: ColorInt get() = rgba(255, 255, 255)

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
internal fun StateHolder<Node, List<Node>>.gRow(
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
internal fun StateHolder<Node, List<Node>>.gCol(
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
internal fun StateHolder<Node, List<Node>>.gCard(
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
        gText(title, 13f, { TEXT }, 650, wrap = true)
        gText(desc, 11f, { TEXT2 }, wrap = true)
        content()
    }
}

/**
 * 展示用文本。[wrap] = true 时宽度从父级拉伸并自动折行（卡片内文案）；
 * 默认 autoWidth（宽度=文本宽，用于行内标签 / 徽标内文字等包裹场景。
 * 注意：放在「包裹行」（justify=grow 且无 grow 子节点）里的文本必须保持
 * autoWidth——否则文本宽度要从行分配，而行宽正在量文本，构成重复进入。
 * [color] 用 lambda 活读主题色（argChildren 只构建一次，传值会冻结旧色板）。
 */
internal fun StateHolder<Node, List<Node>>.gText(
    label: String,
    size: Float = 12f,
    color: () -> ColorInt = { TEXT },
    weight: Int = 400,
    wrap: Boolean = false
): WrappedTextNode = object : WrappedTextNode(this) {
    override val autoWidth: Boolean get() = !wrap
    override val text: String get() = label
    override val fontSize: Float get() = size
    override val color: ColorInt get() = color()
    override val fontWeight: Int get() = weight
}

