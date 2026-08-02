package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.helper.SimpleScrollBar
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
// 动态面板
// ════════════════════════════════════════════════════
internal fun StateHolder<Node,List<Node>>.dActivityPanel(
    activities: StoreRef<List<Activity>>,
    onAdd: () -> Unit
): RectNode = object : RectNode(this), FlexParam, GrowChild {
    override fun argGrow(direction: Direction): Float = if (direction == Direction.x) 1f else 0f
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.y
    override val directionJustify: DirectionJustify get() = DirectionJustify.start
    override val alignItem: AlignItem get() = AlignItem.stretch
    override val alignFix: Boolean get() = true
    override val gap: Float get() = 10f

    override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
        if (direction == Direction.x) 16f else 12f

    override fun draw(canvas: PlatformCanvas) {
        fillOuterRoundRect(canvas, 10f, CARD)
        strokeOuterRoundRect(canvas, 10f, BORDER, 1f)
        super.draw(canvas)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        dLabel({ "最近动态" }, 14f, TEXT, 600)

        object : RectNode(this), FlexParam, GrowChild {
            override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f
            override val layout: LayoutDirection = FlexObject(this)
            override val direction: Direction get() = Direction.x
            override val directionJustify: DirectionJustify get() = DirectionJustify.start
            override val alignItem: AlignItem get() = AlignItem.stretch
            override val alignFix: Boolean get() = true
            override val gap: Float get() = 6f

            val scrollY = Scroll(this).also { registerScroll(it) }

            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                object : ScrollContent(this), FlexParam, GrowChild {
                    override val y: Float get() = -scrollY.value

                    override fun argGrow(direction: Direction): Float = 1f

                    override val alignFix: Boolean get() = true
                    override val alignItem: AlignItem get() = AlignItem.stretch
                    override val gap: Float get() = 6f
                    override val layout: LayoutDirection = FlexObject(this)

                    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                        renderForEach({ callback ->
                            activities.value.forEach { callback(it.id, it) }
                        }) { key, it ->
                            object : RectNode(this), FlexParam {
                                override val layout: LayoutDirection = FlexObject(this)
                                override val direction: Direction = Direction.x
                                override val directionJustify: DirectionJustify = DirectionJustify.start
                                override val alignItem: AlignItem = AlignItem.center
                                override val alignFix: Boolean = true
                                override val argHeight: LayoutSize = LayoutSize(38f, false)
                                override val gap: Float = 8f

                                private val g = context!!.consume(engineGlobalContext)!!
                                private val hovered by memo { g.moveHitest?.include(this) ?: false }

                                override fun draw(canvas: PlatformCanvas) {
                                    if (hovered) {
                                        fillOuterRoundRect(canvas, 6f, GRID)
                                    }
                                    super.draw(canvas)
                                }

                                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                                    object : RectNode(this), FlexParam {
                                        override val layout: LayoutDirection = FlexObject(this)
                                        override val direction: Direction = Direction.x
                                        override val alignItem: AlignItem = AlignItem.center
                                        override val argWidth: LayoutSize = LayoutSize(20f, false)
                                        override val argHeight: LayoutSize = LayoutSize(20f, false)
                                        override val focusable: Boolean = true
                                        override val focusOrder: Int? = it.index

                                        init {
                                            val g = context!!.consume(engineGlobalContext)!!
                                            val d = g.registerKeyPress { e ->
                                                if (isFocused && (e.code == KeyCode.Enter || e.key == ' ')) {
                                                    toggleDone()
                                                }
                                            }
                                            context!!.addDestroy { d() }
                                        }

                                        private fun toggleDone() {
                                            activities.value = activities.value.map { a ->
                                                if (a.id == key) a.copy(done = !a.done) else a
                                            }
                                        }

                                        override fun mouseClick(e: MouseEvent) {
                                            toggleDone()
                                        }

                                        override fun draw(canvas: PlatformCanvas) {
                                            val on = it.value.done
                                            strokeOuterRing(canvas, -1f, 5f, if (on) GREEN else BORDER, 2f)
                                            if (on) {
                                                canvas.drawLine(
                                                    4f, 11f, 8f, 15f, GREEN, 2f
                                                )
                                                canvas.drawLine(
                                                    8f, 15f, 16f, 5f, GREEN, 2f
                                                )
                                            }
                                            if (isFocused) {
                                                strokeOuterRing(canvas, 2f, 7f, ACCENT, 2f)
                                            }
                                        }
                                    }

                                    object : RectNode(this), FlexParam, GrowChild {
                                        override fun argGrow(direction: Direction): Float =
                                            if (direction == Direction.x) 1f else 0f
                                        override val layout: LayoutDirection = FlexObject(this)
                                        override val direction: Direction = Direction.x
                                        override val directionJustify: DirectionJustify =
                                            DirectionJustify.start
                                        override val alignItem: AlignItem = AlignItem.center
                                        override val gap: Float = 8f

                                        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                                            object : RectNode(this) {
                                                override val argWidth: LayoutSize = LayoutSize(7f, false)
                                                override val argHeight: LayoutSize = LayoutSize(7f, false)

                                                override fun draw(canvas: PlatformCanvas) {
                                                    fillOuterOval(canvas, if (it.value.done) GREEN else AMBER)
                                                }
                                            }
                                            dLabel(
                                                { it.value.title },
                                                13f,
                                                if (it.value.done) TEXT2 else TEXT,
                                                if (it.value.done) 400 else 500
                                            )
                                        }
                                    }

                                    dLabel({ it.value.time }, 11f, TEXT2)
                                }
                            }
                        }
                    }
                }

                object : SimpleScrollBar(this) {
                    override val scroll: Scroll get() = scrollY
                }
            }
        }

        dButton("＋ 新增动态", onAdd, primary = false, width = 110f, height = 30f)
    }
}
