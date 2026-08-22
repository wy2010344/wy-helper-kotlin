package org.wy.engine

import com.wy.mve.StateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * D 组输入框基础特性验证：
 * - placeholder：空文本时用占位文本构建段落，点击/选区全部塌缩到 0；
 * - obscureText：每个字素簇显示为一个圆点，编辑与撤销仍作用于逻辑文本。
 */
class InputFieldFeatureTest {

    private class TestEditable(
        context: StateHolder<Node, List<Node>>
    ) : EditableTextNode(context) {
        override val autoWidth: Boolean get() = true

        // 供测试访问受保护的域转换钩子
        fun d2l(displayPos: Int): Int = displayToLogicIndex(displayPos)
        fun l2d(logicPos: Int): Int = logicToDisplayIndex(logicPos)
    }

    private fun newEditor(
        text: String = "",
        placeholder: String = "",
        obscure: Boolean = false
    ): TestEditable {
        val stateHolder = TestStateHolder<Node, List<Node>>()
        val engineGlobal = TestEngineGlobal()
        stateHolder.provide(engineGlobalContext, engineGlobal)
        stateHolder.provide(selectionManagerContext, engineGlobal.selectionManager)
        return TestEditable(stateHolder).also {
            it.text = text
            it.placeholder = placeholder
            it.obscureText = obscure
        }
    }

    // ---------- placeholder ----------

    @Test
    fun placeholderBuildsParagraphWhenEmpty() {
        val bare = newEditor()
        assertTrue(bare.wordRangeAt(0) == null, "无占位且无文本时应无段落")

        val ed = newEditor(placeholder = "请输入")
        assertNotEquals(null, ed.wordRangeAt(0), "有占位时空文本也应构建段落")
    }

    @Test
    fun placeholderCollapsesAllPositionsToZero() {
        val ed = newEditor(placeholder = "用户名")
        assertEquals(0 to 0, ed.wordRangeAt(0), "占位模式下词选择应塌缩")
        ed.moveCursorTo(99)
        ed.selectRange(0, 99)
        ed.insertText("ab")          // 占位下任何定位都应作用在空文本上
        assertEquals("ab", ed.text)
    }

    @Test
    fun typingReplacesPlaceholderDisplay() {
        val ed = newEditor(placeholder = "用户名")
        assertTrue(ed.displayText == "用户名")
        ed.insertText("hi")
        assertEquals("hi", ed.text)
        assertEquals("hi", ed.displayText, "非空后显示真实文本")
    }

    // ---------- obscureText ----------

    @Test
    fun obscureReplacesEachGraphemeWithBullet() {
        val ed = newEditor(text = "ab😊", obscure = true)
        assertEquals("•••", ed.displayText)      // 😊 是一个字素簇 → 一个点
        assertEquals("ab😊", ed.text, "逻辑文本不变")
    }

    @Test
    fun obscureKeepsPlaceholderWhenEmpty() {
        val ed = newEditor(placeholder = "密码", obscure = true)
        assertEquals("密码", ed.displayText, "空文本时显示占位而非圆点")
    }

    @Test
    fun obscureIndexMappingRoundTrip() {
        val ed = newEditor(text = "ab😊c", obscure = true)  // 长度 5；簇起点 0,1,2,4；圆点 4 个
        // 逻辑 → 显示（簇计数，落在簇内部时归到该簇左边界）
        assertEquals(0, ed.l2d(0))
        assertEquals(1, ed.l2d(1))
        assertEquals(2, ed.l2d(3))   // 😊 中间 → 它自己的圆点左缘
        assertEquals(3, ed.l2d(4))   // c 的圆点左缘
        assertEquals(4, ed.l2d(5))   // 文档尾 → 4 个点
        // 显示 → 逻辑（第 k 个点对应第 k 簇起点）
        assertEquals(0, ed.d2l(0))
        assertEquals(1, ed.d2l(1))
        assertEquals(2, ed.d2l(2))   // 第 2 个点 → 😊 起点
        assertEquals(5, ed.d2l(4))   // 尾部光标 → 文档尾
    }

    @Test
    fun editingUnderObscureOperatesOnLogicText() {
        val ed = newEditor(text = "ab😊", obscure = true)
        ed.moveCursorTo(ed.text.length)
        ed.keyDelete()
        assertEquals("ab", ed.text, "删除按逻辑文本工作")
        ed.selectAll()
        ed.insertText("xy")
        assertEquals("xy", ed.text)
        // 撤销逐步恢复（无合并策略）：先撤替换，再撤退格
        ed.undo()
        assertEquals("ab", ed.text)
        ed.undo()
        assertEquals("ab😊", ed.text, "撤销栈存的是逻辑文本")
    }

    private fun EditableTextNode.keyDelete() {
        handleKey(KeyEvent('\u0000', KeyCode.Backspace, false, false, false, false))
    }
}
