package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.helper.drag
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.GrowChild
import org.wy.engine.layout.LayoutDirection
import org.wy.lib.StoreRef
import org.wy.lib.getValue
import org.wy.signal.getValue
import org.wy.signal.memo
import org.wy.signal.setValue

// ════════════════════════════════════════════════════
// 交互控件
// ════════════════════════════════════════════════════
internal fun <T> StateHolder<Node>.dNavItem(
    iconColor: ColorInt,
    label: String,
    badge: Int?,
    active: StoreRef<T>,
    tab: T,
    focusOrder: Int,
    onSelect: () -> Unit
): RectNode = object : RectNode(this), FlexParam {
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.x
    override val directionJustify: DirectionJustify get() = DirectionJustify.between
    override val alignItem: AlignItem get() = AlignItem.center
    override val alignFix: Boolean get() = true
    override val argHeight: LayoutSize get() = LayoutSize(40f, false)
    override val focusable: Boolean get() = true
    override val focusOrder: Int? get() = focusOrder

    private val isActive get() = active.value == tab
    private val g = context!!.consume(engineGlobalContext)!!
    private val hovered by memo { g.moveHitest?.include(this) ?: false }

    init {
        val d2 = g.registerKeyPress { e ->
            if (isFocused && (e.code == KeyCode.Enter || e.key == ' ')) onSelect()
        }
        context!!.addDestroy { d2() }
    }

    override fun mouseClick(e: MouseEvent) {
        onSelect()
    }

    override fun draw(canvas: PlatformCanvas) {
        val bg = when {
            isActive -> ACCENT_LIGHT
            hovered -> rgba(241, 245, 249)
            else -> rgba(0, 0, 0, 0)
        }
        fillOuterRoundRect(canvas, 8f, bg)
        if (isActive) {
            canvas.fillRoundRect(0f, 10f, 3f, outerHeight - 20f, 1.5f, ACCENT)
        }
        if (isFocused) {
            strokeOuterRing(canvas, -1f, 8f, ACCENT, 1f)
        }
        super.draw(canvas)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        object : RectNode(this), FlexParam {
            override val layout: LayoutDirection = FlexObject(this)
            override val direction: Direction get() = Direction.x
            override val alignItem: AlignItem get() = AlignItem.center
            override val gap: Float get() = 10f

            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                object : RectNode(this) {
                    override val argWidth: LayoutSize get() = LayoutSize(8f, false)
                    override val argHeight: LayoutSize get() = LayoutSize(8f, false)

                    override fun draw(canvas: PlatformCanvas) {
                        fillOuterOval(canvas, iconColor)
                    }
                }
                dLabel({ label }, 13f, if (isActive) ACCENT else TEXT, if (isActive) 600 else 400)
            }
        }
        if (badge != null) {
            object : WrappedTextNode(this) {
                override val autoWidth: Boolean get() = true
                override val text: String get() = badge.toString()
                override val fontSize: Float get() = 11f
                override val color: ColorInt get() = rgba(255, 255, 255)
                override val fontWeight: Int get() = 600

                override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                    if (direction == Direction.x) 6f else 2f

                override fun draw(canvas: PlatformCanvas) {
                    fillOuterRoundRect(canvas, 9f, ACCENT)
                    super.draw(canvas)
                }
            }
        }
    }
}

internal fun StateHolder<Node>.dToggleRow(
    label: String,
    checked: StoreRef<Boolean>,
    focusOrder: Int? = null
): RectNode = object : RectNode(this), FlexParam {
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.x
    override val directionJustify: DirectionJustify get() = DirectionJustify.between
    override val alignItem: AlignItem get() = AlignItem.center
    override val alignFix: Boolean get() = true
    override val argHeight: LayoutSize get() = LayoutSize(22f, false)

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        dLabel({ label }, 13f, TEXT, 500)
        object : RectNode(this), FlexParam {
            override val layout: LayoutDirection = FlexObject(this)
            override val direction: Direction get() = Direction.x
            override val alignItem: AlignItem get() = AlignItem.center
            override val argWidth: LayoutSize get() = LayoutSize(40f, false)
            override val argHeight: LayoutSize get() = LayoutSize(22f, false)
            override val focusable: Boolean get() = true
            override val focusOrder: Int? get() = focusOrder

            private val g = context!!.consume(engineGlobalContext)!!
            private val hovered by memo { g.moveHitest?.include(this) ?: false }

            init {
                val d = g.registerKeyPress { e ->
                    if (isFocused && (e.code == KeyCode.Enter || e.key == ' ')) checked.value = !checked.value
                }
                context!!.addDestroy { d() }
            }

            override fun mouseClick(e: MouseEvent) {
                checked.value = !checked.value
            }

            override fun draw(canvas: PlatformCanvas) {
                val on = checked.value
                fillOuterRoundRect(canvas, 11f, if (on) ACCENT else rgba(203, 213, 225))
                canvas.fillOval(if (on) 40f - 20f else 2f, 2f, 18f, 18f, rgba(255, 255, 255))
                if (isFocused) {
                    strokeOuterRing(canvas, 2f, 13f, ACCENT, 2f)
                } else if (hovered) {
                    strokeOuterRing(canvas, 1f, 12f, BAR, 1f)
                }
            }
        }
    }
}

internal fun StateHolder<Node>.dSlider(
    label: String,
    value: StoreRef<Float>,
    focusOrder: Int? = null,
    width: Float = 220f
): RectNode = object : RectNode(this), FlexParam {
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.x
    override val alignItem: AlignItem get() = AlignItem.center
    override val alignFix: Boolean get() = true
    override val argHeight: LayoutSize get() = LayoutSize(20f, false)
    override val gap: Float get() = 12f

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        dLabel({ label }, 13f, TEXT, 500, width = 90f)
        object : RectNode(this), FlexParam {
            override val layout: LayoutDirection = FlexObject(this)
            override val direction: Direction get() = Direction.x
            override val alignItem: AlignItem get() = AlignItem.center
            override val alignFix: Boolean get() = true
            override val argWidth: LayoutSize get() = LayoutSize(width, false)
            override val argHeight: LayoutSize get() = LayoutSize(20f, false)
            override val focusable: Boolean get() = true
            override val focusOrder: Int? get() = focusOrder

            private val g = context!!.consume(engineGlobalContext)!!
            private val hovered by memo { g.moveHitest?.include(this) ?: false }

            init {
                val d = g.registerKeyPress { e ->
                    if (isFocused) {
                        when (e.code) {
                            KeyCode.Left -> value.value = (value.value - 0.05f).coerceIn(0f, 1f)
                            KeyCode.Right -> value.value = (value.value + 0.05f).coerceIn(0f, 1f)
                            else -> {}
                        }
                    }
                }
                context!!.addDestroy { d() }
            }

            override fun mouseDown(e: MouseEvent) {
                value.value = (e.x / width).coerceIn(0f, 1f)
                context!!.drag { me ->
                    value.value = ((me.x - absoluteX) / width).coerceIn(0f, 1f)
                }
            }

            override fun draw(canvas: PlatformCanvas) {
                val v = value.value
                canvas.fillRoundRect(0f, 9f, width, 4f, 2f, rgba(203, 213, 225))
                if (v > 0f) canvas.fillRoundRect(0f, 9f, width * v, 4f, 2f, ACCENT)
                canvas.fillOval(width * v - 6f, 2f, 16f, 16f, ACCENT)
                if (isFocused) {
                    canvas.strokeRoundRect(0f, 0f, width, 20f, 10f, ACCENT, 1f)
                } else if (hovered) {
                    canvas.strokeRoundRect(0f, 0f, width, 20f, 10f, BAR, 1f)
                }
            }
        }
        dLabel({ "${(value.value * 100).toInt()}" }, 12f, TEXT2)
    }
}

internal fun <T> StateHolder<Node>.dSegTabs(active: StoreRef<T>, items: List<Pair<T, String>>): RectNode =
    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.x
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val alignFix: Boolean get() = true
        override val argHeight: LayoutSize get() = LayoutSize(36f, false)
        override val gap: Float get() = 6f

        override fun draw(canvas: PlatformCanvas) {
            fillOuterRoundRect(canvas, 10f, rgba(241, 245, 249))
            super.draw(canvas)
        }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            items.forEachIndexed { i, (tab, name) ->
                object : RectNode(this), FlexParam, GrowChild {
                    override fun argGrow(direction: Direction): Float = 1f
                    override val layout: LayoutDirection = FlexObject(this)
                    override val direction: Direction get() = Direction.x
                    override val directionJustify: DirectionJustify get() = DirectionJustify.center
                    override val alignItem: AlignItem get() = AlignItem.center
                    override val alignFix: Boolean get() = true
                    override val focusable: Boolean get() = true
                    override val focusOrder: Int? get() = 10 + i

                    private val isOn get() = active.value == tab
                    private val g = context!!.consume(engineGlobalContext)!!
                    private val hovered by memo { g.moveHitest?.include(this) ?: false }

                    init {
                        val d = g.registerKeyPress { e ->
                            if (isFocused && (e.code == KeyCode.Enter || e.key == ' ')) active.value = tab
                        }
                        context!!.addDestroy { d() }
                    }

                    override fun mouseClick(e: MouseEvent) {
                        active.value = tab
                    }

                    override fun draw(canvas: PlatformCanvas) {
                        if (isOn) {
                            canvas.fillRoundRect(0f, 2f, outerWidth, outerHeight - 4f, 8f, CARD)
                            canvas.strokeRoundRect(0f, 2f, outerWidth, outerHeight - 4f, 8f, BORDER, 1f)
                        } else if (hovered) {
                            canvas.fillRoundRect(0f, 2f, outerWidth, outerHeight - 4f, 8f, rgba(255, 255, 255))
                        }
                        if (isFocused) {
                            canvas.strokeRoundRect(1f, 3f, outerWidth - 2f, outerHeight - 6f, 8f, ACCENT, 2f)
                        }
                        super.draw(canvas)
                    }

                    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                        dLabel({ name }, 13f, if (isOn) ACCENT else TEXT2, if (isOn) 600 else 400)
                    }
                }
            }
        }
    }
