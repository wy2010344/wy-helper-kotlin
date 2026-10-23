package org.wy.engine.dashboard

import com.wy.layout.AlignItem
import org.wy.engine.*
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.animation.DefaultFrameSource
import org.wy.engine.animation.FrameSubscription
import org.wy.engine.helper.ButtonBase
import org.wy.engine.helper.DialogBase
import org.wy.engine.helper.DropdownBase
import org.wy.engine.helper.OverlayBase
import org.wy.engine.helper.ScrollBarStyle
import org.wy.engine.helper.SimpleScrollNode
import org.wy.engine.helper.dialog
import org.wy.engine.helper.drag
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.GrowChild
import org.wy.engine.layout.LayoutDirection
import org.wy.lib.StoreRef
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.memo
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sign
import kotlin.math.sqrt

internal fun StateHolder<Node, List<Node>>.dText(
    text: () -> String,
    size: Float = 11f,
    color: () -> ColorInt = { D_TEXT },
    weight: Int = 400,
    wrap: Boolean = false,
    width: Float? = null,
    align: TextAlign = TextAlign.START,
    grow: Boolean = false
): WrappedTextNode = object : WrappedTextNode(this), GrowChild {
    override val autoWidth: Boolean get() = !wrap && width == null && !grow
    override val text: String get() = text()
    override val fontSize: Float get() = size
    override val color: ColorInt get() = color()
    override val fontWeight: Int get() = weight
    override val textAlign: TextAlign get() = align
    // 定宽列 / grow 列都按单行裁剪，避免长数字折行撑高行
    override val noWrap: Boolean get() = (width != null && !wrap) || grow
    override val argWidth: LayoutSize get() = width?.let { LayoutSize(it, false) } ?: super.argWidth
    override fun argGrow(direction: Direction): Float =
        if (grow && direction == Direction.x) 1f else 0f
}

/**
 * 行容器（主轴 x）。契约与组件库页 gRow 一致：
 * 宽度默认包裹内容（justify=grow）；[grow] = true 走 grow 通道填满剩余；
 * [height] 有定值时子节点纵向拉伸（alignFix=true），无定值时包裹最高子节点。
 */
internal fun StateHolder<Node, List<Node>>.dRow(
    gap: Float = 8f,
    align: AlignItem = AlignItem.center,
    justify: DirectionJustify = DirectionJustify.grow,
    height: Float? = null,
    padX: Float = 0f,
    grow: Boolean = false,
    growY: Boolean = false,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit
) {
    object : RectNode(this), FlexParam, GrowChild {
        override val debugName: String get() = "看板行"
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.x
        override val directionJustify: DirectionJustify get() = justify
        override val alignItem: AlignItem get() = align
        // growY = true 时行的高度由父级拉伸而来，交叉轴尺寸必须取父级给定值，
        // 否则 size memo 会回读正在计算的自身 size（子列 stretch 时 childSize = 行 size）构成死循环。
        override val alignFix: Boolean get() = height != null || growY
        override val gap: Float get() = gap
        override val argHeight: LayoutSize
            get() = height?.let { LayoutSize(it, false) } ?: super.argHeight

        override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
            if (direction == Direction.x) padX else 0f

        override fun argGrow(direction: Direction): Float = when (direction) {
            Direction.x -> if (grow) 1f else 0f
            Direction.y -> if (growY) 1f else 0f
        }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            content()
        }
    }
}

/**
 * 列容器（主轴 y）。宽度始终横向拉伸（alignFix=true）。
 * 高度：[height] 有定值时子节点顺序堆放（justify=start），否则包裹内容。
 *
 * ⚠️ 放进行容器（dRow）时必须传 grow = true（1fr）：否则列的宽度回读行的
 * 主轴缓存，构成重复进入死循环。
 */
internal fun StateHolder<Node, List<Node>>.dCol(
    gap: Float = 0f,
    align: AlignItem = AlignItem.stretch,
    height: Float? = null,
    grow: Boolean = false,
    growY: Boolean = false,
    padX: Float = 0f,
    width: Float? = null,
    // 动态宽度通道：argChildren 只执行一次（StateHolderI.create 契约），
    // width = signal.value 是启动时刻快照；走 lambda 让 argWidth getter
    // 每次布局读取信号，被 size memo 追踪后拖拽才会重算布局。优先于 width。
    widthOf: (() -> Float)? = null,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit
) {
    object : RectNode(this), FlexParam, GrowChild {
        override val debugName: String get() = "看板列"
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.y
        override val directionJustify: DirectionJustify
            get() = if (height != null) DirectionJustify.start else DirectionJustify.grow
        override val alignItem: AlignItem get() = align
        override val alignFix: Boolean get() = true
        override val gap: Float get() = gap
        override val argHeight: LayoutSize
            get() = height?.let { LayoutSize(it, false) } ?: super.argHeight
        override val argWidth: LayoutSize
            get() = (widthOf?.invoke() ?: width)?.let { LayoutSize(it, false) } ?: super.argWidth

        override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
            if (direction == Direction.x) padX else 0f

        override fun argGrow(direction: Direction): Float = when (direction) {
            Direction.x -> if (grow) 1f else 0f
            Direction.y -> if (growY) 1f else 0f
        }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            content()
        }
    }
}

/** 弹性占位（把后续内容推到右侧）。 */
internal fun StateHolder<Node, List<Node>>.dSpacer(direction: Direction = Direction.x) {
    object : RectNode(this), GrowChild {
        override fun argGrow(d: Direction): Float = if (d == direction) 1f else 0f
        override val argHeight: LayoutSize get() = LayoutSize(0f, false)
    }
}

/**
 * 看板窗格：36px 标题栏（标题 + 提示 + tools 槽）+ 裁剪内容区。
 * [height] 有定值时堆叠（justify=start）；[grow] = true 时在列里吃剩余高度。
 */
internal fun StateHolder<Node, List<Node>>.dPane(
    title: String,
    hint: String = "",
    // 动态副标题通道：argChildren 只执行一次，hint = signal.value 是启动快照；
    // lambda 让文本每次求值读活信号（如 Chart 副标题跟随 interval）。优先于 hint。
    hintOf: (() -> String)? = null,
    height: Float? = null,
    // 动态高度通道：argChildren 一次性执行，height = signal.value 是快照；
    // lambda 让 argHeight getter 每次布局读信号（拖拽分隔条时重算）。优先于 height。
    heightOf: (() -> Float)? = null,
    grow: Boolean = false,
    tools: (StateHolderWithNode<Node, List<Node>>.() -> Unit)? = null,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit
): RectNode = object : RectNode(this), FlexParam, GrowChild {
    override val debugName: String get() = "窗格[$title]"
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.y
    override val directionJustify: DirectionJustify get() = DirectionJustify.start
    override val alignItem: AlignItem get() = AlignItem.stretch
    override val alignFix: Boolean get() = true
    override val gap: Float get() = 0f

    override fun argGrow(direction: Direction): Float = if (grow && direction == Direction.y) 1f else 0f
    override val argHeight: LayoutSize
        get() = (heightOf?.invoke() ?: height)?.let { LayoutSize(it, false) } ?: super.argHeight

    override fun draw(canvas: PlatformCanvas) {
        fillOuterRoundRect(canvas, 6f, D_PANE)
        // 标题栏：裁到圆角内再铺浅一档底色 + 底线
        canvas.save()
        canvas.clipRRect(0f, 0f, outerWidth, outerHeight, 6f)
        canvas.fillRect(0f, 0f, outerWidth, PANE_HEAD_H, D_PANE_HEAD)
        canvas.drawLine(0f, PANE_HEAD_H, outerWidth, PANE_HEAD_H, D_LINE, 1f)
        canvas.restore()
        strokeOuterRoundRect(canvas, 6f, D_LINE, 1f)
        super.draw(canvas)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        dRow(gap = 8f, height = PANE_HEAD_H, padX = 10f) {
            dText({ title }, 10f, { D_TEXT2 }, 600)
            if (hintOf != null || hint.isNotEmpty()) {
                dText({ hintOf?.invoke() ?: hint }, 9.5f, { D_TEXT3 })
            }
            dSpacer()
            tools?.invoke(this)
        }
        // 内容区：标准 SimpleScrollNode（grow 在 y 方向）填满剩余高度。
        // 用引擎内置的 grow-grow 范式（SimpleScrollNode 内部 ScrollContent 也是 grow 子节点），
        // 父级 flex 走 grow 分支、不回读子节点尺寸，避免布局重入。
        object : SimpleScrollNode(this) {
            override fun argGrow(direction: Direction): Float =
                if (direction == Direction.y) 1f else 0f

            override val scrollBarStyle: ScrollBarStyle get() = DARK_SCROLL_BAR

            override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
                content()
            }
        }
    }
}

internal const val PANE_HEAD_H = 36f

/** 竖向分隔条：左右拖拽改列宽。sign = -1 表示鼠标右移使列变窄。 */
internal fun StateHolder<Node, List<Node>>.dSplitV(
    current: () -> Float,
    min: Float,
    max: Float,
    sign: Float,
    onChange: (Float) -> Unit
): RectNode {
    val holder = this
    return object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.x
        override val directionJustify: DirectionJustify get() = DirectionJustify.center
        override val alignItem: AlignItem get() = AlignItem.center
        override val alignFix: Boolean get() = true
        override val argWidth: LayoutSize get() = LayoutSize(SPLIT_W, false)

        private val g = engineGlobal
        private val hovered by memo { g.moveHitTest?.include(this) == true }

        override fun cursorAt(x: Float, y: Float) = CursorType.RESIZE_EW

        override fun onPointerDown(e: PointerEvent) {
            val startRoot = e.rootX
            val startValue = current()
            holder.drag(e) { me ->
                onChange((startValue + (me.rootX - startRoot) * sign).coerceIn(min, max))
            }
            e.stopPropagation()
        }

        override fun draw(canvas: PlatformCanvas) {
            canvas.fillRect(SPLIT_W / 2f - 0.5f, 0f, 1f, outerHeight, if (hovered) D_ACCENT else D_LINE)
            super.draw(canvas)
        }
    }
}

/** 横向分隔条：上下拖拽改窗格高。sign = -1 表示鼠标下移使窗格变矮。 */
internal fun StateHolder<Node, List<Node>>.dSplitH(
    current: () -> Float,
    min: Float,
    max: Float,
    sign: Float,
    onChange: (Float) -> Unit
): RectNode {
    val holder = this
    return object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.x
        override val directionJustify: DirectionJustify get() = DirectionJustify.center
        override val alignItem: AlignItem get() = AlignItem.center
        override val alignFix: Boolean get() = true
        override val argHeight: LayoutSize get() = LayoutSize(SPLIT_W, false)

        private val g = engineGlobal
        private val hovered by memo { g.moveHitTest?.include(this) == true }

        override fun cursorAt(x: Float, y: Float) = CursorType.RESIZE_NS

        override fun onPointerDown(e: PointerEvent) {
            val startRoot = e.rootY
            val startValue = current()
            holder.drag(e) { me ->
                onChange((startValue + (me.rootY - startRoot) * sign).coerceIn(min, max))
            }
            e.stopPropagation()
        }

        override fun draw(canvas: PlatformCanvas) {
            canvas.fillRect(0f, SPLIT_W / 2f - 0.5f, outerWidth, 1f, if (hovered) D_ACCENT else D_LINE)
            super.draw(canvas)
        }
    }
}

internal const val SPLIT_W = 6f

/**
 * header 小 tab（交易对 / 周期 / 活动页签）：选中态主色淡底 + 主色描边。
 * 宽度包裹内容（directionJustify=grow）。
 */
internal fun StateHolder<Node, List<Node>>.dTabButton(
    label: () -> String,
    active: () -> Boolean,
    height: Float = 22f,
    padX: Float = 8f,
    chip: ColorInt? = null,
    // 动态 chip 通道：argChildren 只执行一次，chip = cond 是启动快照；
    // lambda 让底色/文字色每次求值读活信号（如买卖 tab 切换）。优先于 chip。
    chipOf: (() -> ColorInt?)? = null,
    grow: Boolean = false,
    onClick: () -> Unit
): ButtonBase = object : ButtonBase(this), GrowChild {
    override val debugName: String get() = "标签按钮"
    override val directionJustify: DirectionJustify get() = DirectionJustify.grow
    override val argHeight: LayoutSize get() = LayoutSize(height, false)

    // 文本标签按钮必须有确定（非父派生）宽度：否则被 between / grow(无 grow 子) 行回读
    // outerSize 时父级 childSize 正在计算 → 重入崩溃。按标签内容测量（与 dText autoWidth 同机制）；
    // grow 子节点则走 sizeFromParent，接收父级分配的拉伸宽度（与 dText grow 同机制）。
    private val tabTextWidth by memo {
        buildParagraph(
            listOf(RichTextSpan(label(), RichTextStyle(fontSize = 10f, fontWeight = 500))),
            Float.MAX_VALUE
        ).width
    }
    override val argWidth: LayoutSize
        get() = if (grow) sizeFromParent(Direction.x) else LayoutSize(tabTextWidth, true)

    override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
        if (direction == Direction.x) padX else 0f

    override fun argGrow(direction: Direction): Float =
        if (grow && direction == Direction.x) 1f else 0f

    override fun onClick() = onClick()

    override fun drawContent(canvas: PlatformCanvas) {
        val c = chipOf?.invoke() ?: chip
        val on = c != null || active()
        val tone = c ?: D_ACCENT
        if (on) {
            fillOuterRoundRect(canvas, 3f, tone.withAlpha(0.14f))
            strokeOuterRoundRect(canvas, 3f, tone.withAlpha(0.45f), 1f)
        } else if (hovered) {
            fillOuterRoundRect(canvas, 3f, D_HOVER)
            strokeOuterRoundRect(canvas, 3f, D_LINE, 1f)
        } else {
            strokeOuterRoundRect(canvas, 3f, D_LINE_SOFT, 1f)
        }
    }

    override fun drawFocusRing(canvas: PlatformCanvas) {
        strokeOuterRing(canvas, 2f, 4f, D_ACCENT, 1.5f)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        // 选中色在文本求值 lambda 内现算：argChildren 一次性，外面捕获是启动快照
        dText(label, 10f, {
            val c = chipOf?.invoke() ?: chip
            if (c != null || active()) (c ?: D_TEXT) else D_TEXT2
        }, 500)
    }
}

/** 定尺寸小按钮（图标 / 文字皆可），悬停浅底。 */
internal fun StateHolder<Node, List<Node>>.dIconButton(
    size: Float = 20f,
    radius: Float = 3f,
    onClick: () -> Unit,
    drawIcon: (PlatformCanvas, Float, Float) -> Unit
): ButtonBase = object : ButtonBase(this) {
    override val directionJustify: DirectionJustify get() = DirectionJustify.center
    override val argWidth: LayoutSize get() = LayoutSize(size, false)
    override val argHeight: LayoutSize get() = LayoutSize(size, false)

    override fun onClick() = onClick()

    override fun drawContent(canvas: PlatformCanvas) {
        if (hovered || pressed) fillOuterRoundRect(canvas, radius, D_HOVER)
        drawIcon(canvas, outerWidth, outerHeight)
    }

    override fun drawFocusRing(canvas: PlatformCanvas) {
        strokeOuterRing(canvas, 2f, radius + 1f, D_ACCENT, 1.5f)
    }
}

/**
 * 可点击行（自选 / 盘口点价）：hover 浅底、选中主色淡底。
 * 内容是行内子节点（1fr 列 + 定宽数字列）。
 */
internal fun StateHolder<Node, List<Node>>.dClickRow(
    height: Float,
    active: Boolean = false,
    padX: Float = 10f,
    onClick: () -> Unit,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit
): ButtonBase = object : ButtonBase(this) {
    override val directionJustify: DirectionJustify get() = DirectionJustify.start
    override val alignItem: AlignItem get() = AlignItem.center
    override val alignFix: Boolean get() = true
    override val argHeight: LayoutSize get() = LayoutSize(height, false)

    override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
        if (direction == Direction.x) padX else 0f

    override fun onClick() = onClick()

    override fun drawContent(canvas: PlatformCanvas) {
        when {
            active -> fillOuterRect(canvas, D_ACTIVE_BG)
            pressed -> fillOuterRect(canvas, D_HOVER)
            hovered -> fillOuterRect(canvas, D_HOVER)
        }
    }

    override fun drawFocusRing(canvas: PlatformCanvas) {
        strokeOuterRing(canvas, 1f, 3f, D_ACCENT, 1.5f)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        content()
    }
}

/** 深色滚动条（自选 / 成交流 / 概览共用）：白滑块低 alpha。 */
internal val DARK_SCROLL_BAR = ScrollBarStyle(
    thumb = rgba(255, 255, 255),
    idleAlpha = 0.22f,
    hoverAlpha = 0.38f,
    dragAlpha = 0.52f
)

// ════════════════════════════════════════════════════
// 八、自选列表（复刻 watchlist.ooc）
//
