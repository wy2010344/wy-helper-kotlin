package org.wy.engine.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolderWithNode
import org.jetbrains.skia.Image
import org.jetbrains.skia.Pixmap
import org.jetbrains.skia.Surface
import org.wy.engine.Direction
import org.wy.engine.LayoutSize
import org.wy.engine.Node
import org.wy.engine.PlatformCanvas
import org.wy.engine.RectNode
import org.wy.engine.Renderer
import org.wy.engine.SkiaTestBase
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.engine.rgba
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 浮层滚动条（macOS 风格自动隐藏）回归测试。
 *
 * [Renderer.render] 会把画布清成不透明白色，因此断言一律基于亮度：
 * 滑块（测试样式下纯黑）画出来即"有墨迹"，纯白即"无墨迹"。
 *
 * 锁死两类缺陷：
 * 1. **渲染期死循环**：滚动内容的 y 读 `scroll.value` → `maxScroll` → `contentSize`
 *    → 内容容器「尺寸从子节点算」缓存首算 → 逐个子节点量尺寸。任何从父级推导尺寸
 *    （非定值 / 非包裹 / 非 grow）的子节点都会重新进入同一缓存抛「重复进入」，
 *    且异常会污染 signal 全局状态、让后续渲染连锁失败。由于 [Renderer.render]
 *    内部 try/catch 会吞掉异常，只能靠「滑块到底有没有画出来」观测渲染是否完整跑完。
 * 2. **几何契约**：滑块贴在容器内容右缘（内缩 edgeGap）、不占布局空间、静止时完全透明。
 *
 * 布局事实（已从源码 + 离屏像素核实，勿凭感觉改采样点）：
 * - 滚动容器显式 argWidth=200（显式定值优先于父级 stretch，不会被拉满到根的 240）；
 * - 条带 x = 200 - hitWidth(12) = 188；滑块局部 x = 12 - 3 - 6 = 3，宽 6 → 绝对 191..196；
 * - 滑块 y = trackInset(4) + offset(0)，长 max(28, 92×100/400) = 28 → 绝对 4..31。
 */
class OverlayScrollBarTest : SkiaTestBase() {

    /** 固定高度的内容行（滚动内容的子节点主轴尺寸必须自带定值或包裹）。 */
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

    private fun render(root: Renderer, w: Int = 240, h: Int = 120): Image {
        val surface = Surface.makeRasterN32Premul(w, h)
        root.render(PlatformCanvas(surface.canvas))
        return surface.makeImageSnapshot()
    }

    /** 平均亮度低于 200 视为"有墨迹"（白底 255、黑滑块 0、抗锯齿边缘介于两者之间）。 */
    private fun inked(px: Pixmap, x: Int, y: Int): Boolean {
        val c = px.getColor(x, y)
        val r = c ushr 16 and 0xFF
        val g = c ushr 8 and 0xFF
        val b = c and 0xFF
        return (r + g + b) / 3 < 200
    }

    private fun assertThumbPainted(image: Image) {
        val px = image.peekPixels()!!
        try {
            assertTrue(
                inked(px, 193, 10),
                "渲染完整跑完时滑块应被绘制（中途抛异常会被 render 的 try/catch 吞掉，只剩白底）"
            )
        } finally {
            px.close()
            image.close()
        }
    }

    /**
     * 滚动容器 + 10 行内容（400 高 / 100 视口 → maxScroll=300）。
     *
     * children 是惰性构建：先访问根的 children 拿到滚动容器实例，
     * 再访问容器自身的 children——ScrollContent 与 OverlayScrollBar 在后者里创建，
     * 此时 [SimpleScrollNode.scrollBar] 才非空（构造期读取会 NPE）。
     */
    private fun scrollTree(reveal: Boolean): Renderer {
        lateinit var scroll: SimpleScrollNode
        val root = object : Renderer(null), FlexParam {
            override val layout: LayoutDirection = FlexObject(this)
            override val direction: Direction get() = Direction.y
            override val directionJustify: DirectionJustify get() = DirectionJustify.start
            override val alignItem: AlignItem get() = AlignItem.stretch
            override val alignFix: Boolean get() = true
            override val argWidth: LayoutSize get() = LayoutSize(240f, false)
            override val argHeight: LayoutSize get() = LayoutSize(120f, false)

            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                object : SimpleScrollNode(this) {
                    override val argWidth: LayoutSize get() = LayoutSize(200f, false)
                    override val argHeight: LayoutSize get() = LayoutSize(100f, false)
                    override val scrollBarStyle = ScrollBarStyle(
                        thumb = rgba(0, 0, 0),
                        idleAlpha = 1f,
                        width = 6f,
                    )

                    override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
                        repeat(10) { Row(this, 40f) }
                    }
                }.also { scroll = it }
            }
        }
        root.children
        scroll.children
        if (reveal) scroll.scrollBar!!.revealNow()
        return root
    }

    @Test
    fun scrollContentWithOverlayBarRendersWithoutReentrancy() {
        // 连渲两帧：旧 bug 不仅首帧抛「重复进入」，异常还会污染 signal 全局状态，
        // 让紧随其后的渲染连锁失败。异常被 render 吞掉，故以滑块是否绘制来验证。
        val root = scrollTree(reveal = true)
        assertThumbPainted(render(root))
        assertThumbPainted(render(root))
    }

    @Test
    fun revealedThumbPaintsAtContentRightEdge() {
        val root = scrollTree(reveal = true)
        val image = render(root)
        val px = image.peekPixels()!!
        try {
            assertTrue(inked(px, 193, 10), "reveal 后滑块位置应有墨迹")
            assertTrue(!inked(px, 199, 10), "滑块右缘应内缩 edgeGap，不顶到容器边缘")
            assertTrue(!inked(px, 185, 10), "滑块不应伸进左侧内容区")
            assertTrue(!inked(px, 193, 90), "滑块之外（y=90）不应有墨迹")
            // 整行扫描：这一行的墨迹必须全部落在滑块区间 [191, 196]
            for (x in 0 until 240) {
                if (inked(px, x, 10)) {
                    assertTrue(x in 191..196, "墨迹越出滑块区间: x=$x")
                }
            }
        } finally {
            px.close()
            image.close()
        }
    }

    @Test
    fun hiddenBarLeavesRightEdgeClean() {
        val root = scrollTree(reveal = false)
        val image = render(root)
        val px = image.peekPixels()!!
        try {
            // 静止态：整张画布除白底外不应有任何墨迹——
            // 滑块完全透明，内容行本身也不绘制
            for (y in 0 until 120) {
                for (x in 0 until 240) {
                    assertTrue(!inked(px, x, y), "静止态不应有墨迹: ($x,$y)")
                }
            }
        } finally {
            px.close()
            image.close()
        }
    }

    @Test
    fun scrollActivityNotifiesListener() {
        // OverlayScrollBar 的淡入淡出由 Scroll 的活动通知驱动；这里锁定订阅契约。
        // 滚轮 / 拖拽 / 程序写值都收束到 value setter，直接写值即可覆盖该收束点。
        val (holder, _) = createHelperEnv()
        val node = SimpleScrollNode(holder)
        var fired = 0
        val off = node.innerScroll.addActivityListener { fired++ }
        node.innerScroll.value = 50f
        assertEquals(1, fired, "滚动位置变化应通知监听者")
        off()
        node.innerScroll.value = 10f
        assertEquals(1, fired, "退订后不应再收到通知")
    }
}
