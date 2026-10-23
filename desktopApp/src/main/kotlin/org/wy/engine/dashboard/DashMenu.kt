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

/** 深色下拉浮层（盘口 ⋯ 菜单 / 精度选择）。 */
internal fun StateHolder<Node, List<Node>>.dMenu(
    anchor: LayoutNode,
    width: Float = 190f,
    onClose: () -> Unit,
    content: StateHolderWithNode<Node, List<Node>>.() -> Unit
): DropdownBase = object : DropdownBase(this, anchor) {
    override fun onDismiss() = onClose()
    override val panelWidth: Float get() = width

    override fun StateHolderWithNode<Node, List<Node>>.panel() {
        // panelWidth 在外层（DropdownBase 对象体）先读出来：内层面板对象里
        // 没有指向外层匿名对象的标签可用，直接传标量最稳
        val pw = panelWidth
        val ox = absoluteX
        val oy = absoluteY
        object : RectNode(this), FlexParam {
            override val layout: LayoutDirection = FlexObject(this)
            override val direction: Direction get() = Direction.y
            override val directionJustify: DirectionJustify get() = DirectionJustify.grow
            override val alignItem: AlignItem get() = AlignItem.stretch
            override val alignFix: Boolean get() = true
            override val gap: Float get() = 2f
            override val argWidth: LayoutSize get() = LayoutSize(pw, false)

            override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                if (direction == Direction.x) 6f else 5f

            override fun argPosition(direction: Direction): Float {
                val a = anchor
                return when (direction) {
                    Direction.x -> a.absoluteX - ox
                    Direction.y -> a.absoluteY + a.outerHeight + 4f - oy
                }
            }

            override fun onPointerClick(e: PointerEvent) {
                e.stopPropagation()
            }

            override fun draw(canvas: PlatformCanvas) {
                // 阴影先画（被不透明底板盖住内圈），再铺底板 + 描边
                canvas.drawShadow(2f, 2f, outerWidth - 4f, outerHeight - 4f, 6f, 12f, rgba(0, 0, 0, 120))
                fillOuterRoundRect(canvas, 6f, D_PANE)
                strokeOuterRoundRect(canvas, 6f, D_LINE, 1f)
                super.draw(canvas)
            }

            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                content()
            }
        }
    }

    override fun StateHolderWithNode<Node, List<Node>>.contentChildren() = content()
}

/** 菜单普通项。 */
internal fun StateHolder<Node, List<Node>>.dMenuItem(
    label: () -> String,
    trailing: () -> String = { "" },
    onClick: () -> Unit
): ButtonBase = object : ButtonBase(this) {
    override val debugName: String get() = "菜单项"
    override val directionJustify: DirectionJustify get() = DirectionJustify.start
    override val alignItem: AlignItem get() = AlignItem.center
    override val alignFix: Boolean get() = true
    override val argHeight: LayoutSize get() = LayoutSize(24f, false)

    override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
        if (direction == Direction.x) 8f else 0f

    override fun onClick() = onClick()

    override fun drawContent(canvas: PlatformCanvas) {
        if (hovered || pressed) fillOuterRoundRect(canvas, 3f, D_HOVER)
    }

    override fun drawFocusRing(canvas: PlatformCanvas) {
        strokeOuterRing(canvas, 1f, 4f, D_ACCENT, 1.5f)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        dText(label, 10.5f, { D_TEXT })
        dSpacer()
        // trailing 走 renderForEach：结构 if 读 trailing() 是启动快照，
        // 菜单勾选态（✓）翻转后标记既出不来也消不掉
        renderForEach({ callback ->
            if (trailing().isNotEmpty()) callback(0, Unit)
        }) { _, _ ->
            dText({ trailing() }, 10f, { D_TEXT2 })
        }
    }
}

/** 菜单复选项（✓ 前缀）。 */
internal fun StateHolder<Node, List<Node>>.dMenuCheckItem(
    label: () -> String,
    checked: () -> Boolean,
    onToggle: () -> Unit
): ButtonBase = object : ButtonBase(this) {
    override val directionJustify: DirectionJustify get() = DirectionJustify.start
    override val alignItem: AlignItem get() = AlignItem.center
    override val alignFix: Boolean get() = true
    override val argHeight: LayoutSize get() = LayoutSize(24f, false)

    override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
        if (direction == Direction.x) 8f else 0f

    override fun onClick() = onToggle()

    override fun drawContent(canvas: PlatformCanvas) {
        if (hovered || pressed) fillOuterRoundRect(canvas, 3f, D_HOVER)
    }

    override fun drawFocusRing(canvas: PlatformCanvas) {
        strokeOuterRing(canvas, 1f, 4f, D_ACCENT, 1.5f)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        dText({ if (checked()) "✓" else "" }, 10.5f, { D_ACCENT }, 700, width = 14f, align = TextAlign.CENTER)
        dText(label, 10.5f, { D_TEXT })
    }
}

/** 菜单分组标签。 */
internal fun StateHolder<Node, List<Node>>.dMenuLabel(text: String) {
    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.x
        override val alignItem: AlignItem get() = AlignItem.center
        override val alignFix: Boolean get() = true
        override val argHeight: LayoutSize get() = LayoutSize(20f, false)

        override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
            if (direction == Direction.x) 8f else 0f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            dText({ text }, 9f, { D_TEXT3 }, 600)
        }
    }
}

/** 菜单分隔线。 */
internal fun StateHolder<Node, List<Node>>.dMenuSeparator() {
    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.x
        override val alignItem: AlignItem get() = AlignItem.center
        override val alignFix: Boolean get() = true
        override val argHeight: LayoutSize get() = LayoutSize(7f, false)

        override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
            if (direction == Direction.x) 8f else 3f

        override fun draw(canvas: PlatformCanvas) {
            canvas.drawLine(0f, outerHeight / 2f, outerWidth, outerHeight / 2f, D_LINE, 1f)
        }
    }
}

