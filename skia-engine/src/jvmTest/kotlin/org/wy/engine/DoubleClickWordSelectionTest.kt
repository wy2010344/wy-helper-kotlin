package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.signal.batchSignalEnd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 双击选词：词边界统一走 Words（与 Ctrl+←/→ 词跳同源），
 * 选区物化复用编程式 select（编辑器聚焦时自动分流为其内部选区）。
 */
class DoubleClickWordSelectionTest {

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

    @org.junit.After
    fun drainSignalBatch() {
        Thread.sleep(50)
        batchSignalEnd()
    }

    @Test
    fun wordBoundaryFromRealParagraph() {
        val (sh, _) = createEnv()
        val node = TestText(sh, "Hello world")

        // 点击 "world" 中间（偏移 8）→ 词边界 [6, 11)
        assertEquals(6 to 11, node.wordRangeAt(8))
        // 点击 "Hello" 开头 → [0, 5)
        assertEquals(0 to 5, node.wordRangeAt(2))
    }

    @Test
    fun doubleClickEquivalentSelectCoversWord() {
        val (sh, g) = createEnv()
        val node = TestText(sh, "Hello world")
        g.mount(node) // headless 无渲染树，显式挂进选区派生集合
        val manager = g.selectionManager

        // 模拟双击的物化路径：点击处偏移 → 词边界 → 编程式 select
        val off = node.positionForPoint(0f, 0f)
        val word = assertNotNull(node.wordRangeAt(off), "有段落时应能分词")
        assertTrue(manager.select(node, word.first, node, word.second))
        assertEquals("Hello world".substring(word.first, word.second), manager.selectedText)
    }
}
