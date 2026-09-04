package org.wy.engine

import com.wy.mve.StateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F 组多击手势验证：
 * - 三击选段：paragraphRangeAt 按 '\n' 划分逻辑段落；
 * - 双击拖词扩展：expandWordSelection 以锚词为基准按词粒度扩展；
 * - 物化路径复用编程式 select（与双击选词同构）。
 */
class MultiClickSelectionTest : SkiaTestBase() {

    private class TestText(
        context: StateHolder<Node, List<Node>>,
        override val text: String
    ) : WrappedTextNode(context) {
        override val autoWidth: Boolean get() = true
    }

    private fun createEnv(): Pair<TestStateHolder<Node, List<Node>>, TestEngineGlobal> {
        val state = TestStateHolder<Node, List<Node>>()
        val g = TestEngineGlobal()
        state.provide(engineGlobalContext, g)
        state.provide(selectionManagerContext, g.selectionManager)
        return state to g
    }

    @Test
    fun paragraphRangeSplitsByNewline() {
        val (sh, _) = createEnv()
        val node = TestText(sh, "ab\ncd\nef")
        assertEquals(0 to 2, node.paragraphRangeAt(0))   // 首段
        assertEquals(0 to 2, node.paragraphRangeAt(1))
        assertEquals(0 to 2, node.paragraphRangeAt(2))   // 点在换行符上归前段
        assertEquals(3 to 5, node.paragraphRangeAt(3))   // 中段
        assertEquals(3 to 5, node.paragraphRangeAt(5))   // 点在换行符上归前段
        assertEquals(6 to 8, node.paragraphRangeAt(6))   // 末段到文末
        assertEquals(6 to 8, node.paragraphRangeAt(99))  // 越界钳制
    }

    @Test
    fun paragraphRangeSingleParagraphAndEmpty() {
        val (sh, _) = createEnv()
        assertEquals(0 to 11, TestText(sh, "Hello world").paragraphRangeAt(4))
        assertNull(TestText(sh, "").paragraphRangeAt(0), "空文本无段落")
    }

    @Test
    fun tripleClickEquivalentSelectCoversParagraph() {
        val (sh, g) = createEnv()
        val node = TestText(sh, "one\ntwo\nthree")
        g.mount(node) // headless 无渲染树，显式挂进选区派生集合
        val manager = g.selectionManager

        // 模拟三击的物化路径：点击处偏移 → 段落边界 → 编程式 select
        val off = node.positionForPoint(0f, 0f)
        val para = assertNotNull(node.paragraphRangeAt(off))
        assertTrue(manager.select(node, para.first, node, para.second))
        assertEquals("one", manager.selectedText)

        val mid = assertNotNull(node.paragraphRangeAt(4))
        manager.select(node, mid.first, node, mid.second)
        assertEquals("two", manager.selectedText)
    }

    // ---------- 双击拖词扩展 ----------

    private fun wordAt(text: String): (Int) -> Pair<Int, Int>? {
        val (sh, _) = createEnv()
        val node = TestText(sh, text)
        return { node.wordRangeAt(it) }
    }

    @Test
    fun expandRightReachesWholeWord() {
        val w = wordAt("Hello world foo")
        // 锚词 world[6,11)，向右拖到 foo 中间 → 吸附到词尾
        assertEquals(6 to 15, expandWordSelection(6, 11, 13, w))
    }

    @Test
    fun expandLeftReachesWholeWord() {
        val w = wordAt("Hello world")
        // 锚词 world[6,11)，向左拖到 Hello 中间 → 吸附到词首
        assertEquals(0 to 11, expandWordSelection(6, 11, 2, w))
    }

    @Test
    fun expandInsideAnchorUnchanged() {
        val w = wordAt("Hello world")
        assertEquals(6 to 11, expandWordSelection(6, 11, 8, w))
    }

    @Test
    fun expandWithoutTokenizerFallsBackToChar() {
        // 无分词能力：退化为字符粒度
        val none: (Int) -> Pair<Int, Int>? = { null }
        assertEquals(6 to 14, expandWordSelection(6, 11, 14, none))
        assertEquals(3 to 11, expandWordSelection(6, 11, 3, none))
    }
}
