package org.wy.engine.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolderWithNode
import org.jetbrains.skia.Surface
import org.wy.engine.Direction
import org.wy.engine.LayoutSize
import org.wy.engine.Node
import org.wy.engine.PlatformCanvas
import org.wy.engine.PointerEvent
import org.wy.engine.PointerType
import org.wy.engine.RectNode
import org.wy.engine.Renderer
import org.wy.engine.SkiaTestBase
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.engine.maxScroll
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 滚轮轴向回归测试。
 *
 * 锁死一类缺陷：`SimpleScrollNode.onPointerWheel` 误用**容器布局方向** `direction`
 * （= `scrollDirection.opposite`）而非**滚动方向** `scrollDirection` 去挑滚轮增量轴。
 *
 * 症状：纵向滚动容器的 `direction` 恰好是 `x`，于是纵向滚轮被错读成
 * `wheelDeltaX`（桌面端恒为 0）→「只能横向滚、不能纵向滚」。本测试对纵/横两种
 * 容器分别验证：只消费与自身滚动方向同轴的增量，另一轴增量必须被忽略。
 *
 * 布局事实：
 * - 纵向：视口 200×100，内容 10 行 ×40 = 400 → maxScroll(y)=300；
 * - 横向：视口 200×100，内容 10 列 ×40 = 400 → maxScroll(x)=200。
 */
class ScrollWheelAxisTest : SkiaTestBase() {

    /** 纵向内容行：主轴（父内容方向 y）定高，交叉轴 grow。 */
    private class Row(context: StateHolderWithNode<Node, List<Node>>, private val h: Float) :
        RectNode(context), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.x
        override val directionJustify: DirectionJustify get() = DirectionJustify.grow
        override val alignItem: AlignItem get() = AlignItem.center
        override val alignFix: Boolean get() = true
        override val argWidth: LayoutSize get() = LayoutSize(0f, true)
        override val argHeight: LayoutSize get() = LayoutSize(h, false)
    }

    /** 横向内容列：主轴（父内容方向 x）定宽，交叉轴 grow。 */
    private class Col(context: StateHolderWithNode<Node, List<Node>>, private val w: Float) :
        RectNode(context), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.y
        override val directionJustify: DirectionJustify get() = DirectionJustify.grow
        override val alignItem: AlignItem get() = AlignItem.center
        override val alignFix: Boolean get() = true
        override val argWidth: LayoutSize get() = LayoutSize(w, false)
        override val argHeight: LayoutSize get() = LayoutSize(0f, true)
    }

    /**
     * 构造「根 240×120 → 滚动容器 200×100 → 10 段 40px 内容」的树，渲染一帧完成布局，
     * 返回渲染根与滚动容器（此时 `maxScroll` 才可读）。
     */
    private fun build(scrollDirection: Direction): Pair<Renderer, SimpleScrollNode> {
        lateinit var scroll: SimpleScrollNode
        val root = object : Renderer(null), FlexParam {
            override val layout: LayoutDirection = FlexObject(this)
            override val direction: Direction get() = Direction.y
            override val directionJustify: DirectionJustify get() = DirectionJustify.start
            override val alignItem: AlignItem get() = AlignItem.start
            override val alignFix: Boolean get() = true
            override val argWidth: LayoutSize get() = LayoutSize(240f, false)
            override val argHeight: LayoutSize get() = LayoutSize(120f, false)

            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                object : SimpleScrollNode(this, scrollDirection) {
                    override val argWidth: LayoutSize get() = LayoutSize(200f, false)
                    override val argHeight: LayoutSize get() = LayoutSize(100f, false)
                    override val scrollBarEnabled: Boolean get() = false

                    override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
                        if (scrollDirection == Direction.y) {
                            repeat(10) { Row(this, 40f) }
                        } else {
                            repeat(10) { Col(this, 40f) }
                        }
                    }
                }.also { scroll = it }
            }
        }
        root.children
        scroll.children
        val surface = Surface.makeRasterN32Premul(240, 120)
        root.render(PlatformCanvas(surface.canvas))
        return root to scroll
    }

    private fun wheel(deltaX: Float = 0f, deltaY: Float = 0f) =
        PointerEvent(type = PointerType.Wheel, x = 5f, y = 5f, wheelDeltaX = deltaX, wheelDeltaY = deltaY)

    @Test
    fun verticalScrollConsumesOnlyWheelDeltaY() {
        val (root, scroll) = build(Direction.y)
        try {
            assertEquals(300f, scroll.maxScroll(Direction.y), "纵向 maxScroll：内容 400 - 视口 100")
            scroll.onPointerWheel(wheel(deltaX = 50f))
            assertEquals(0f, scroll.innerScroll.value, "纵向容器必须忽略 wheelDeltaX")
            scroll.onPointerWheel(wheel(deltaY = 50f))
            assertEquals(50f, scroll.innerScroll.value, "纵向容器必须响应 wheelDeltaY")
        } finally {
            root.destroy()
        }
    }

    @Test
    fun horizontalScrollConsumesOnlyWheelDeltaX() {
        val (root, scroll) = build(Direction.x)
        try {
            assertEquals(200f, scroll.maxScroll(Direction.x), "横向 maxScroll：内容 400 - 视口 200")
            scroll.onPointerWheel(wheel(deltaY = 50f))
            assertEquals(0f, scroll.innerScroll.value, "横向容器必须忽略 wheelDeltaY")
            scroll.onPointerWheel(wheel(deltaX = 50f))
            assertEquals(50f, scroll.innerScroll.value, "横向容器必须响应 wheelDeltaX")
        } finally {
            root.destroy()
        }
    }
}
