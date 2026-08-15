package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
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
): RectNode = object : RectNode(this), FlexParam {
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.x
    override val directionJustify: DirectionJustify get() = DirectionJustify.center
    override val alignItem: AlignItem get() = AlignItem.center
    override val alignFix: Boolean get() = true
    override val argWidth: LayoutSize get() = LayoutSize(width, false)
    override val argHeight: LayoutSize get() = LayoutSize(height, false)
    override val focusable: Boolean get() = true
    override val focusOrder: Int? get() = focusOrder

    private val g = engineGlobal
    private val hovered by memo { g.moveHitTest?.include(this) ?: false }
    private val pressed by memo { g.pressed != null && (g.moveHitTest?.include(this) == true) }

    init {
        val d2 = g.registerKeyPress { e ->
            if (isFocused && (e.code == KeyCode.Enter || e.key == ' ')) onClick()
        }
        addDestroy { d2() }
    }

    override fun onPointerClick(e: PointerEvent) {
        onClick()
    }

    override fun draw(canvas: PlatformCanvas) {
        val bg = when {
            pressed -> if (primary) ACCENT_DARK else GRID
            hovered -> if (primary) ACCENT_HOVER else rgba(241, 245, 249)
            else -> if (primary) ACCENT else CARD
        }
        fillOuterRoundRect(canvas, 8f, bg)
        strokeOuterRoundRect(canvas, 8f, if (primary) bg else BORDER, 1f)
        if (isFocused) {
            strokeOuterRing(canvas, 2f, 10f, ACCENT, 2f)
        }
        super.draw(canvas)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        dLabel({ label }, 13f, if (primary) rgba(255, 255, 255) else TEXT, 600)
    }
}

internal fun StateHolder<Node,List<Node>>.dTextField(value: StoreRef<String>, focusOrder: Int): EditableTextNode =
    object : EditableTextNode(this) {
        override var text: String
            get() = value.value
            set(v) {
                value.value = v
            }

        override val fontSize: Float = 13f
        override val focusOrder: Int? = focusOrder
        override val argWidth: LayoutSize = LayoutSize(240f, false)
        override val argHeight: LayoutSize = LayoutSize(34f, false)

        private val g = engineGlobal
        private val hovered by memo { g.moveHitTest?.include(this) ?: false }

        override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
            if (direction == Direction.x) 10f else 6f

        override fun draw(canvas: PlatformCanvas) {
            fillOuterRoundRect(canvas, 8f, rgba(244, 246, 251))
            strokeOuterRoundRect(
                canvas, 8f,
                when {
                    isFocused -> ACCENT
                    hovered -> BAR
                    else -> BORDER
                }, 1f
            )
            super.draw(canvas)
        }
    }
