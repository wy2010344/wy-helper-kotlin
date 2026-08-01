package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
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
// 概览页
// ════════════════════════════════════════════════════
internal fun StateHolder<Node>.dStatCard(
    label: String,
    valueText: () -> String,
    sub: String,
    progress: Float,
    accent: ColorInt,
    spark: List<Float>
): RectNode = object : RectNode(this), FlexParam, GrowChild {
    override fun argGrow(direction: Direction): Float = if (direction == Direction.x) 1f else 0f
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.y
    override val directionJustify: DirectionJustify get() = DirectionJustify.start
    override val alignItem: AlignItem get() = AlignItem.stretch
    override val alignFix: Boolean get() = true
    override val argHeight: LayoutSize get() = LayoutSize(112f, false)
    override val gap: Float get() = 5f

    override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
        if (direction == Direction.x) 14f else 8f

    private val g = context!!.consume(engineGlobalContext)!!
    private val hovered by memo { g.moveHitest?.include(this) ?: false }

    override fun draw(canvas: PlatformCanvas) {
        canvas.fillRoundRect(0f, 0f, outerWidth, outerHeight, 10f, CARD)
        canvas.strokeRoundRect(
            0f, 0f, outerWidth, outerHeight, 10f,
            if (hovered) accent else BORDER, 1f
        )
        val sw = 54f
        val sh = 20f
        val sx = outerWidth - sw - 14f
        val sy = 14f
        val max = (spark.maxOrNull() ?: 1f).coerceAtLeast(1f)
        for (i in 0 until spark.size - 1) {
            val x1 = sx + sw * i / (spark.size - 1)
            val y1 = sy + sh - sh * spark[i] / max
            val x2 = sx + sw * (i + 1) / (spark.size - 1)
            val y2 = sy + sh - sh * spark[i + 1] / max
            canvas.drawLine(x1, y1, x2, y2, accent, 2f)
        }
        super.draw(canvas)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        dLabel({ label }, 12f, TEXT2, 500)
        dLabel(valueText, 24f, TEXT, 700)
        dLabel({ sub }, 11f, GREEN, 500)
        object : RectNode(this) {
            override val argHeight: LayoutSize get() = LayoutSize(6f, false)

            override fun draw(canvas: PlatformCanvas) {
                canvas.fillRoundRect(0f, 0f, outerWidth, outerHeight, 3f, rgba(226, 232, 240))
                if (progress > 0f) {
                    canvas.fillRoundRect(0f, 0f, outerWidth * progress, outerHeight, 3f, accent)
                }
            }
        }
    }
}

internal fun StateHolder<Node>.dChartPanel(): RectNode {
    return object : RectNode(this), FlexParam, GrowChild {
        override fun argGrow(direction: Direction): Float = if (direction == Direction.x) 1.5f else 0f
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.y
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val alignFix: Boolean get() = true
        override val gap: Float get() = 10f

        override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
            if (direction == Direction.x) 16f else 12f

        override fun draw(canvas: PlatformCanvas) {
            canvas.fillRoundRect(0f, 0f, outerWidth, outerHeight, 10f, CARD)
            canvas.strokeRoundRect(0f, 0f, outerWidth, outerHeight, 10f, BORDER, 1f)
            super.draw(canvas)
        }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            object : RectNode(this), FlexParam {
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.x
                override val alignItem: AlignItem get() = AlignItem.center
                override val gap: Float get() = 8f

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    dLabel({ "每周专注时长" }, 14f, TEXT, 600)
                    dLabel({ "● 本周" }, 11f, ACCENT)
                }
            }

            object : RectNode(this), FlexParam, GrowChild {
                override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.x
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val alignFix: Boolean get() = true

                private val g = context!!.consume(engineGlobalContext)!!
                private val hovered by memo {
                    val hit = g.moveHitest
                    val n = CHART_DATA.size
                    if (hit != null && hit.include(this)) {
                        ((hit.x - absoluteX) / (innerWidth / n)).toInt().coerceIn(0, n - 1)
                    } else null
                }

                override fun draw(canvas: PlatformCanvas) {
                    val w = innerWidth
                    val h = innerHeight
                    val n = CHART_DATA.size
                    for (g in 1..3) {
                        val y = h * g / 4f
                        canvas.drawLine(0f, y, w, y, GRID, 1f)
                    }
                    val slot = w / n
                    val barW = slot * 0.5f
                    CHART_DATA.forEachIndexed { i, v ->
                        val bh = (v / 100f) * (h - 14f)
                        val x = slot * i + (slot - barW) / 2f
                        val y = h - bh
                        canvas.fillRoundRect(
                            x, y, barW, bh, barW / 3f,
                            if (hovered == i) ACCENT else BAR
                        )
                    }
                    super.draw(canvas)
                }

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    object : WrappedTextNode(this) {
                        override val hide: Boolean get() = hovered == null
                        override val autoWidth: Boolean get() = true
                        override val text: String
                            get() {
                                val hi = hovered ?: return ""
                                return "周${CHART_DAYS[hi]} ${CHART_DATA[hi].toInt()}h"
                            }
                        override val fontSize: Float get() = 11f
                        override val color: ColorInt get() = rgba(255, 255, 255)
                        override val fontWeight: Int get() = 600

                        override val x: Float
                            get() {
                                val hi = hovered ?: return 0f
                                val slot = innerWidth / CHART_DATA.size
                                val max = (innerWidth - outerWidth).coerceAtLeast(0f)
                                return (slot * hi + slot / 2f - outerWidth / 2f).coerceIn(0f, max)
                            }

                        override val y: Float
                            get() {
                                val hi = hovered ?: return 0f
                                val slot = innerWidth / CHART_DATA.size
                                val h = innerHeight
                                val bh = (CHART_DATA[hi] / 100f) * (h - 14f)
                                return (h - bh - outerHeight - 8f).coerceAtLeast(0f)
                            }

                        override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                            if (direction == Direction.x) 6f else 3f

                        override fun draw(canvas: PlatformCanvas) {
                            canvas.fillRoundRect(0f, 0f, outerWidth, outerHeight, 4f, rgba(30, 41, 59, 235))
                            super.draw(canvas)
                        }
                    }
                }
            }
        }
    }
}

internal fun StateHolder<Node>.dOverviewPage(
    active: StoreRef<DemoTab>,
    activities: StoreRef<List<Activity>>,
    onAdd: () -> Unit
): RectNode = object : RectNode(this), FlexParam, GrowChild {
    override val hide: Boolean get() = active.value != DemoTab.OVERVIEW

    override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f

    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.y
    override val directionJustify: DirectionJustify get() = DirectionJustify.start
    override val alignItem: AlignItem get() = AlignItem.stretch
    override val alignFix: Boolean get() = true
    override val gap: Float get() = 12f

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        object : RectNode(this), FlexParam {
            override val layout: LayoutDirection = FlexObject(this)
            override val direction: Direction get() = Direction.x
            override val directionJustify: DirectionJustify get() = DirectionJustify.start
            override val alignItem: AlignItem get() = AlignItem.stretch
            override val alignFix: Boolean get() = true
            override val argHeight: LayoutSize get() = LayoutSize(112f, false)
            override val gap: Float get() = 12f

            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                dStatCard("今日任务", { "12" }, "较昨日 +3", 0.66f, ACCENT, listOf(3f, 5f, 4f, 7f, 6f, 9f, 8f))
                dStatCard("已完成", { "8" }, "完成率 67%", 0.67f, GREEN, listOf(2f, 4f, 3f, 5f, 6f, 7f, 8f))
                dStatCard("专注时长", { "4.5h" }, "目标 6h", 0.75f, AMBER, listOf(1f, 2f, 4f, 3f, 5f, 4f, 6f))
            }
        }

        object : RectNode(this), FlexParam, GrowChild {
            override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f
            override val layout: LayoutDirection = FlexObject(this)
            override val direction: Direction get() = Direction.x
            override val directionJustify: DirectionJustify get() = DirectionJustify.start
            override val alignItem: AlignItem get() = AlignItem.stretch
            override val alignFix: Boolean get() = true
            override val gap: Float get() = 12f

            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                dChartPanel()
                dActivityPanel(activities, onAdd)
            }
        }
    }
}
