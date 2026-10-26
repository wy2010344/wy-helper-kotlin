package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolderWithNode
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.GrowChild
import org.wy.engine.layout.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 「y 向容器 + 交叉轴 stretch 子节点」的最小复现：
 *
 * 卡片（direction=y, alignItem=stretch, alignFix=true, 固定尺寸）
 *   └─ 绘图区（argGrow(y)=1f，宽度交给父级 stretch 分配）
 *
 * 概览页图表（dChartPanel）正是这个结构，实测其绘图区宽度曾为 0。
 * 本测试把该结构从 demo 里剥离出来，固化交叉轴 stretch 的分配契约。
 */
class CrossStretchTest : SkiaTestBase() {

    /** 固定尺寸的 y 向容器（对齐 dChartPanel 卡片）。 */
    private class Card(context: com.wy.mve.StateHolder<Node, List<Node>>) : RectNode(context), FlexParam, GrowChild {
        override val argWidth: LayoutSize get() = LayoutSize(521f, false)
        override val argHeight: LayoutSize get() = LayoutSize(450f, false)
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.y
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val alignFix: Boolean get() = true
        override val gap: Float get() = 10f
        override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
            if (direction == Direction.x) 16f else 12f

        lateinit var plot: RectNode

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            // 页头行（包裹内容）
            object : RectNode(this), FlexParam {
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.x
                override val alignItem: AlignItem get() = AlignItem.center
                override val gap: Float get() = 8f
                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    object : RectNode(this) {
                        override val argWidth: LayoutSize get() = LayoutSize(84f, false)
                        override val argHeight: LayoutSize get() = LayoutSize(20f, false)
                    }
                }
            }
            // 绘图区：高度 grow；宽度修复前默认走「从子节点算」
            // （x 向 + justify=grow 默认 + 无子节点 → 0），显式改回父级分配
            plot = object : RectNode(this), FlexParam, GrowChild {
                override val debugName get() = "绘图区"
                override fun argGrow(direction: Direction): Float =
                    if (direction == Direction.y) 1f else 0f
                override val argWidth: LayoutSize
                    get() = sizeFromParent(Direction.x)
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.x
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val alignFix: Boolean get() = true
            }
        }
    }

    private fun build(): Card {
        lateinit var card: Card
        val renderer = object : Renderer(null) {
            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                card = Card(this)
                // 卡片本身也需要父级？不——它有固定尺寸，直接挂渲染根即可
            }
        }
        renderer.children
        card.children // 触发 Card 自身 argChildren，创建 plot
        return card
    }

    @Test
    fun stretchChildFillsCrossAxis() {
        val card = build()
        val w = card.plot.outerWidth
        println("[repro] plot outerWidth = $w")
        assertEquals(
            521f - 32f, w,
            "y 向容器（alignItem=stretch + alignFix）应把交叉轴内宽分给子节点"
        )
    }
}
