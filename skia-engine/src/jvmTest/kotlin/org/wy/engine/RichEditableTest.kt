package org.wy.engine

import com.wy.mve.StateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * E 组富文本编辑器验证：
 * - 内容为样式分段列表，打字 / 删除 / 撤销自动维持样式一致性；
 * - 光标导航复用纯文本逻辑（作用于拼接后的全文）；
 * - 占位显示等父类特性继续可用。
 */
class RichEditableTest {

    // 与节点 baseStyle 一致：lineHeightMultiplier 由排版属性解析为 1.4f
    private val base = RichTextStyle(lineHeightMultiplier = 1.4f)
    private val bold = RichTextStyle(fontWeight = 700)

    private class TestRich(
        context: StateHolder<Node, List<Node>>
    ) : RichEditableTextNode(context) {
        override val autoWidth: Boolean get() = true
    }

    private fun newEditor(text: String = "", placeholder: String = ""): TestRich {
        val stateHolder = TestStateHolder<Node, List<Node>>()
        val engineGlobal = TestEngineGlobal()
        stateHolder.provide(engineGlobalContext, engineGlobal)
        stateHolder.provide(selectionManagerContext, engineGlobal.selectionManager)
        return TestRich(stateHolder).also {
            it.text = text
            it.placeholder = placeholder
        }
    }

    @Test
    fun richContentDefaultsToSingleSegment() {
        val ed = newEditor("abc")
        assertEquals(listOf(RichTextSpan("abc", base)), ed.spans.toList())
    }

    @Test
    fun styleRangeSplitsSegments() {
        val ed = newEditor("abcdef")
        ed.styleRange(1, 4, bold)                 // bcd 加粗
        assertEquals(
            listOf(RichTextSpan("a", base), RichTextSpan("bcd", bold), RichTextSpan("ef", base)),
            ed.spans.toList()
        )
    }

    @Test
    fun adjacentSameStyleSegmentsMerge() {
        val ed = newEditor("abcdef")
        ed.styleRange(0, 3, bold)
        ed.styleRange(3, 6, bold)                 // 两段同款 → 合并
        assertEquals(listOf(RichTextSpan("abcdef", bold)), ed.spans.toList())
    }

    @Test
    fun revertToBaseDropsSegments() {
        val ed = newEditor("abc")
        ed.styleRange(0, 3, bold)
        ed.styleRange(0, 3, null)                 // 恢复基础样式
        assertEquals(listOf(RichTextSpan("abc", base)), ed.spans.toList())
    }

    @Test
    fun typingInheritsPrecedingCharStyle() {
        val ed = newEditor("ab")                  // a 加粗
        ed.styleRange(0, 1, bold)
        ed.moveCursorTo(ed.text.length)
        ed.insertText("cd")
        assertEquals("abcd", ed.text)
        // 插入点左侧是普通字符 b → cd 为普通样式
        assertEquals(listOf(RichTextSpan("a", bold), RichTextSpan("bcd", base)), ed.spans.toList())

        ed.moveCursorTo(1)                        // 光标在加粗 a 之后
        ed.insertText("X")
        assertEquals("aXbcd", ed.text)
        assertEquals(
            listOf(RichTextSpan("aX", bold), RichTextSpan("bcd", base)),
            ed.spans.toList()
        )
    }

    @Test
    fun deletionKeepsStylesAligned() {
        val ed = newEditor("abcd")
        ed.styleRange(0, 2, bold)                 // [ab|cd]
        ed.moveCursorTo(3)                        // c 之后
        ed.keyBackspace()                         // 删 c
        assertEquals("abd", ed.text)
        assertEquals(listOf(RichTextSpan("ab", bold), RichTextSpan("d", base)), ed.spans.toList())

        ed.selectAll()
        ed.insertText("Z")                        // 选区替换：插入样式取自文档首字符（无左邻时取右邻）
        assertEquals("Z", ed.text)
        assertEquals(listOf(RichTextSpan("Z", bold)), ed.spans.toList())
    }

    @Test
    fun undoRestoresTextAndStyles() {
        val ed = newEditor("hello")
        ed.moveCursorTo(ed.text.length)
        ed.insertText(" world")
        ed.styleRange(0, 5, bold)                 // 样式操作不入文本撤销栈（v1 约定）
        assertEquals(
            listOf(RichTextSpan("hello", bold), RichTextSpan(" world", base)),
            ed.spans.toList()
        )
        ed.undo()                                 // 撤销插入：文本回缩，样式段自动对齐无越界
        assertEquals("hello", ed.text)
        assertEquals(listOf(RichTextSpan("hello", bold)), ed.spans.toList())
    }

    @Test
    fun navigationAndSelectionUseJoinedText() {
        val ed = newEditor("one\ntwo")
        ed.styleRange(0, 3, bold)
        ed.moveCursorTo(ed.text.length)
        ed.key(KeyCode.End)
        ed.key(KeyCode.Home)                      // 行首（作用于第二行而非文档首）
        ed.key(KeyCode.End, shift = true)         // 选中第二行 two
        ed.insertText("!")                        // 用替换验证选区范围
        assertEquals("one\n!", ed.text)
    }

    @Test
    fun placeholderStillWorksInRichEditor() {
        val ed = newEditor(placeholder = "说点什么")
        assertEquals("说点什么", ed.displayText)
        ed.insertText("hi")
        assertEquals("hi", ed.displayText)
    }

    /** 触发按键。 */
    private fun EditableTextNode.key(
        code: KeyCode,
        ctrl: Boolean = false,
        shift: Boolean = false
    ) {
        handleKey(KeyEvent('\u0000', code, ctrl, shift, false, false))
    }

    private fun EditableTextNode.keyBackspace() {
        handleKey(KeyEvent('\u0000', KeyCode.Backspace, false, false, false, false))
    }
}
