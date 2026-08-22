package org.wy.engine

import com.wy.mve.StateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * C 组富文本链接验证：
 * - RichTextSpan.url 标记链接片段；
 * - RichTextNode.linkAt 按累计偏移做命中测试，非链接区间返回 null。
 */
class LinkSpanTest {

    private class TestRich(
        context: StateHolder<Node, List<Node>>
    ) : RichTextNode(context) {
        override val spans: List<RichTextSpan> = listOf(
            RichTextSpan("参见 "),
            RichTextSpan("文档", url = "https://example.com/docs"),
            RichTextSpan(" 与 "),
            RichTextSpan("仓库", url = "https://git.example.com/x/y"),
            RichTextSpan("。")
        )
    }

    private fun newNode(): RichTextNode {
        val stateHolder = TestStateHolder<Node, List<Node>>()
        val engineGlobal = TestEngineGlobal()
        stateHolder.provide(engineGlobalContext, engineGlobal)
        stateHolder.provide(selectionManagerContext, engineGlobal.selectionManager)
        return TestRich(stateHolder)
    }

    // 布局："参见 "(0-3) "文档"(3-5) " 与 "(5-8) "仓库"(8-10) "。"(10)

    @Test
    fun linkHitTestByAccumulatedOffset() {
        val node = newNode()
        assertNull(node.linkAt(0), "普通文本不命中")
        assertNull(node.linkAt(2))
        assertEquals("https://example.com/docs", node.linkAt(3))
        assertEquals("https://example.com/docs", node.linkAt(4))
        assertNull(node.linkAt(6), "间隔普通文本不命中")
        assertEquals("https://git.example.com/x/y", node.linkAt(8))
        assertEquals("https://git.example.com/x/y", node.linkAt(9))
        assertNull(node.linkAt(10), "句尾不命中")
    }

    @Test
    fun noUrlSpansMeansNoLinks() {
        val stateHolder = TestStateHolder<Node, List<Node>>()
        val engineGlobal = TestEngineGlobal()
        stateHolder.provide(engineGlobalContext, engineGlobal)
        stateHolder.provide(selectionManagerContext, engineGlobal.selectionManager)
        val node = object : RichTextNode(stateHolder) {
            override val spans: List<RichTextSpan> = listOf(RichTextSpan("plain"))
        }
        assertNull(node.linkAt(0))
    }
}
