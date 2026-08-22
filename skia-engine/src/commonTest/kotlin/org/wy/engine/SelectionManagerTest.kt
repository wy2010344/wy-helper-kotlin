package org.wy.engine

import com.wy.mve.StateHolderWithNode
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SelectionManager 纯派生单元测试：
 * - 唯一可写的原始事实是 pointerSelect（按下/释放）与 moveHitTest；
 * - anchor/focus/rangeOf/selectedText 全部由原始信号即时推导，无命令式入口；
 * - "定格"即 PointerSelect.release 非空——同一信号槽的两种形态。
 *
 * 环境与生产一致：真 Renderer 渲染树（文档序真实可比）+ 自定义 MockText
 * （positionForPoint 用可编程换算，不依赖段落布局，可在纯 JVM 下运行）。
 */
class SelectionManagerTest {

    /** 可编程换算坐标的文本节点 mock（挂渲染树下成为兄弟，文档序真实可比）。 */
    private class MockText(
        state: StateHolderWithNode<Node, List<Node>>,
        var text: String = ""
    ) : Node(state), Selectable {
        /** (globalX, globalY) → 文本偏移，默认恒为 0。 */
        var posResolver: (Float, Float) -> Int = { _, _ -> 0 }

        /** 模拟"从树上摘除"：置 true 后由 children 的 purifyList 过滤（信号化保证派生链感知）。 */
        var hidden: Boolean by createSignal(false)
        override val hide: Boolean get() = hidden

        override val textLength: Int get() = text.length

        override fun positionForPoint(globalX: Float, globalY: Float): Int =
            posResolver(globalX, globalY).coerceIn(0, text.length)

        override fun textInRange(start: Int, end: Int): String =
            if (end > start) text.substring(start, end.coerceAtMost(text.length)) else ""

        override fun selectionRect(): RectF? = null
    }

    /** 三节点环境：命中点的全局 X 直接换算为该节点的文本偏移。 */
    private class Env {
        lateinit var renderer: Renderer
            private set
        lateinit var a: MockText
            private set
        lateinit var b: MockText
            private set
        lateinit var c: MockText
            private set

        val g: EngineGlobal get() = renderer.engineGlobal
        val m: SelectionManager get() = renderer.engineGlobal.selectionManager

        fun build() {
            renderer = object : Renderer(null) {
                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    a = make("Hello world")
                    b = make("Kotlin engine")
                    c = make("Third block")
                }
            }
            renderer.children
        }

        private fun StateHolderWithNode<Node, List<Node>>.make(t: String): MockText =
            MockText(this, t).also {
                it.posResolver = { x, _ -> x.toInt() }
            }

        /** 构造命中链依次经过 [nodes]（最后一个为最深节点）的 HitestResult。 */
        fun hitOf(vararg nodes: Pair<MockText, Float>): HitestResult =
            HitestResult(
                chain = nodes.map { NodeWithPosition(it.first, it.second, 0f) },
                time = 0L,
                x = nodes.lastOrNull()?.second ?: 0f,
                y = 0f
            )

        /** 空白命中（无可选节点），用于模拟按下/释放在空白处。 */
        fun blankHit(): HitestResult = HitestResult(emptyList(), 0L, 0f, 0f)
    }

    private fun env(): Env = Env().also { it.build() }

    @Test
    fun pointerDragDerivesRangeFromSignals() {
        val e = env()
        // 无任何输入：无选区
        assertNull(e.m.selectedText)

        // 按下 a@2 并拖到 a@7：范围由信号即时推导（release == null 即拖拽中）
        e.g.pointerSelect = PointerSelect(e.hitOf(e.a to 2f), null)
        assertNull(e.m.selectedText, "按下未移动时为零宽选区")
        e.g.moveHitTest = e.hitOf(e.a to 7f)
        assertEquals("llo w", e.m.selectedText)
        assertEquals(2 to 7, e.m.rangeOf(e.a))
    }

    @Test
    fun backwardDragSwapsEndpoints() {
        val e = env()
        // 反向拖拽（从 b@3 拖回 a@6）：按文档序交换端点，b 为锚在前
        e.g.pointerSelect = PointerSelect(e.hitOf(e.b to 3f), null)
        e.g.moveHitTest = e.hitOf(e.a to 6f)

        assertEquals(6 to 11, e.m.rangeOf(e.a))
        assertEquals(0 to 3, e.m.rangeOf(e.b))
        assertEquals("worldKot", e.m.selectedText)
    }

    @Test
    fun pressingOnEmptyAreaClearsWholeSelection() {
        val e = env()
        // 先建立定格选区 b[2..9]
        e.g.pointerSelect = PointerSelect(e.hitOf(e.b to 2f), e.hitOf(e.b to 9f))
        assertTrue(e.m.hasSelection)

        // 按下落在无可选节点处（空命中链）：整个选区立即清除（浏览器语义）
        e.g.pointerSelect = PointerSelect(e.blankHit(), null)

        assertFalse(e.m.hasSelection, "按下空白应清除全表选区")
        assertNull(e.m.selectedText)
        assertNull(e.m.rangeOf(e.b))
    }

    @Test
    fun shiftClickExtendsFromLastAnchor() {
        val e = env()
        // 先建立定格选区 a[4..7] = "o w"
        e.g.pointerSelect = PointerSelect(e.hitOf(e.a to 4f), e.hitOf(e.a to 7f))
        assertEquals("o w", e.m.selectedText)

        // Shift+点击 b@2：锚点恢复为上次会话锚点 a@4，焦点延伸到 b@2
        // （生产 mouseDown 在 shift 时复用 prev.press 作为新会话的按下端，此处模拟同一结果）
        e.g.pointerSelect = PointerSelect(e.hitOf(e.a to 4f), null)
        e.g.moveHitTest = e.hitOf(e.b to 2f)
        assertEquals(SelPoint(e.a, 4), e.m.anchorSel, "Shift 扩展应恢复上次锚点")
        assertEquals(SelPoint(e.b, 2), e.m.focusSel)
        assertEquals("o worldKo", e.m.selectedText)

        // 提交后锚点保持不变（浏览器语义：shift+click 只移动焦点）
        e.g.pointerSelect = PointerSelect(e.hitOf(e.a to 4f), e.hitOf(e.b to 2f))
        assertEquals(SelPoint(e.a, 4), e.m.anchorSel, "提交后锚点应保持不变")
        assertEquals("o worldKo", e.m.selectedText)
    }

    @Test
    fun frozenSessionIgnoresHover() {
        val e = env()
        // 拖拽中 hover 实时影响选区
        e.g.pointerSelect = PointerSelect(e.hitOf(e.a to 2f), null)
        e.g.moveHitTest = e.hitOf(e.a to 5f)
        assertEquals(2 to 5, e.m.rangeOf(e.a))

        // 松手定格：release 非空，hover 不再改变选区
        e.g.pointerSelect = PointerSelect(e.hitOf(e.a to 2f), e.hitOf(e.a to 5f))
        e.g.moveHitTest = e.hitOf(e.c to 4f)
        assertEquals(2 to 5, e.m.rangeOf(e.a), "定格后 hover 不改变选区")

        // 新的空白按下清除定格选区
        e.g.pointerSelect = PointerSelect(e.blankHit(), null)
        assertFalse(e.m.hasSelection)
    }

    @Test
    fun programmaticSelectSetsArbitraryRange() {
        val e = env()
        // 单节点任意区间
        assertTrue(e.m.select(e.a, 2, 7))
        assertEquals("llo w", e.m.selectedText)
        assertEquals(2 to 7, e.m.rangeOf(e.a))

        // 跨节点区间（a@4 → b@2）
        assertTrue(e.m.select(e.a, 4, e.b, 2))
        assertEquals("o worldKo", e.m.selectedText)

        // 指针交互自动让位（无需失效命令）
        e.g.pointerSelect = PointerSelect(e.hitOf(e.c to 0f), null)
        e.g.moveHitTest = e.hitOf(e.c to 5f)
        assertEquals("Third", e.m.selectedText)
    }

    @Test
    fun selectAllSpansRegistry() {
        val e = env()
        e.m.selectAll()

        assertEquals(0 to 11, e.m.rangeOf(e.a))
        assertEquals(0 to 13, e.m.rangeOf(e.b))
        assertEquals(0 to 11, e.m.rangeOf(e.c))
        assertEquals("Hello worldKotlin engineThird block", e.m.selectedText)

        // 隐藏中间节点后重新全选：只剩首尾
        e.b.hidden = true
        e.m.selectAll()
        assertEquals("Hello worldThird block", e.m.selectedText)
    }

    @Test
    fun clearResetsProgrammaticSession() {
        val e = env()
        e.m.selectAll()
        assertTrue(e.m.hasSelection)

        e.m.clear()
        assertFalse(e.m.hasSelection, "clear 后应回到无选区")
        assertNull(e.m.rangeOf(e.c))
    }

    @Test
    fun focusNodeLeavingCollapsesSelectionToAnchor() {
        val e = env()
        e.g.pointerSelect = PointerSelect(e.hitOf(e.a to 0f), null)
        e.g.moveHitTest = e.hitOf(e.c to 4f)
        assertTrue(e.m.hasSelection)

        // 会话进行中焦点端节点从树上消失（隐藏）：端点作废，选区塌缩回锚点，
        // 绝不对离树节点做几何换算（否则触发过期布局坐标越界）
        e.c.hidden = true
        assertEquals(SelPoint(e.a, 0), e.m.anchorSel)
        assertEquals(SelPoint(e.a, 0), e.m.focusSel, "焦点端出局后应塌缩回锚点")
        assertNull(e.m.selectedText)
    }
}
