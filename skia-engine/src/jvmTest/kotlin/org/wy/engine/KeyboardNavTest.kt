package org.wy.engine

import com.wy.mve.StateHolder
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A 组键盘命令行为验证（真实段落布局）：
 * - Shift+Home/End 选到行首尾；Ctrl+Home/End 文档首尾；
 * - Ctrl+←/→ 词跳、Ctrl+Shift+←/→ 词扩选；
 * - Ctrl+Backspace/Delete 删词；
 * - PageUp/PageDown 垂直跳动。
 */
class KeyboardNavTest {

    private class TestEditable(
        context: StateHolder<Node, List<Node>>
    ) : EditableTextNode(context) {
        override val autoWidth: Boolean get() = true
    }

    private fun newEditor(text: String): EditableTextNode {
        val stateHolder = TestStateHolder<Node, List<Node>>()
        val engineGlobal = TestEngineGlobal()
        stateHolder.provide(engineGlobalContext, engineGlobal)
        stateHolder.provide(selectionManagerContext, engineGlobal.selectionManager)
        return TestEditable(stateHolder).also { it.text = text }
    }

    /** 触发按键。 */
    private fun EditableTextNode.key(
        code: KeyCode,
        ctrl: Boolean = false,
        shift: Boolean = false
    ) {
        handleKey(KeyEvent('\u0000', code, ctrl, shift, false, false))
    }

    @Test
    fun shiftHomeSelectsToLineStart() {
        val ed = newEditor("abc\ndef")
        ed.moveCursorTo(ed.text.length)       // f 之后（第二行尾）
        ed.key(KeyCode.Home, shift = true)
        ed.insertText("X")                    // 替换选区 [4, 7)="def"
        assertEquals("abc\nX", ed.text)
    }

    @Test
    fun homeMovesToLineStartNotDocStart() {
        val ed = newEditor("abc\ndef")
        ed.moveCursorTo(ed.text.length)
        ed.key(KeyCode.Home)
        ed.insertText("|")
        assertEquals("abc\n|def", ed.text)
    }

    @Test
    fun ctrlHomeGoesToDocStart() {
        val ed = newEditor("abc\ndef")
        ed.moveCursorTo(ed.text.length)
        ed.key(KeyCode.Home, ctrl = true)
        ed.insertText("|")
        assertEquals("|abc\ndef", ed.text)
    }

    @Test
    fun endMovesToLineEndAndCtrlEndToDocEnd() {
        val ed = newEditor("abc\ndef")
        ed.moveCursorTo(4)                    // d 前（第二行行首）
        ed.key(KeyCode.End)
        ed.insertText("|")
        assertEquals("abc\ndef|", ed.text)    // 行尾（7）

        ed.moveCursorTo(1)
        ed.key(KeyCode.End, ctrl = true)
        ed.insertText("|")
        assertEquals("abc\ndef||", ed.text)   // 文档尾
    }

    @Test
    fun ctrlLeftRightWordJumps() {
        val ed = newEditor("hello world foo")
        ed.moveCursorTo(ed.text.length)
        ed.key(KeyCode.Left, ctrl = true)     // → foo 首（12）
        ed.key(KeyCode.Left, ctrl = true)     // → world 首（6）
        ed.insertText("[")
        assertEquals("hello [world foo", ed.text)

        ed.key(KeyCode.Right, ctrl = true)    // → world 尾（12）
        ed.insertText("]")
        assertEquals("hello [world] foo", ed.text)
    }

    @Test
    fun ctrlShiftLeftExtendsByWord() {
        val ed = newEditor("hello world")
        ed.moveCursorTo(11)
        ed.key(KeyCode.Left, ctrl = true, shift = true)   // 选 "world"
        ed.insertText("W")
        assertEquals("hello W", ed.text)
    }

    @Test
    fun ctrlBackspaceDeletesWord() {
        val ed = newEditor("hello world foo")
        ed.moveCursorTo(ed.text.length)
        ed.key(KeyCode.Backspace, ctrl = true)
        assertEquals("hello world ", ed.text)   // 删 "foo"
        ed.key(KeyCode.Backspace, ctrl = true)
        assertEquals("hello ", ed.text)         // 跳过空格删 "world"

        // 撤销逐步恢复（无合并策略，每步删除独立入栈）
        ed.undo()
        assertEquals("hello world ", ed.text)   // 回退第二次删除
        ed.undo()
        assertEquals("hello world foo", ed.text) // 回退第一次删除
    }

    @Test
    fun ctrlForwardDeleteDeletesWord() {
        val ed = newEditor("hello world")
        ed.moveCursorTo(0)
        ed.key(KeyCode.Delete, ctrl = true)
        assertEquals(" world", ed.text)         // 删到词尾，不含尾随空格（VS Code 语义）
    }
}
