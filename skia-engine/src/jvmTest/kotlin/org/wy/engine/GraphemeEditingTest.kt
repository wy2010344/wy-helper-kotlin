package org.wy.engine

import com.wy.mve.StateHolder
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 编辑器按字素簇（grapheme cluster）移动与删除：
 * emoji（代理对 / ZWJ）、组合字符、CRLF 在退格、Delete、左右键、扩选时不可拆半。
 */
class GraphemeEditingTest {

    /** 构造独立环境中的纯文本编辑器。 */
    private fun newEditor(): EditableTextNode {
        val stateHolder = TestStateHolder<Node, List<Node>>()
        val engineGlobal = TestEngineGlobal()
        stateHolder.provide(engineGlobalContext, engineGlobal)
        stateHolder.provide(selectionManagerContext, engineGlobal.selectionManager)
        return EditableTextNode(stateHolder)
    }

    @Test
    fun backspaceRemovesWholeEmoji() {
        val ed = newEditor()
        ed.text = "a\uD83D\uDE00b"          // a + 😀(2单元) + b
        ed.moveCursorTo(ed.text.length)
        ed.backspace()                       // 删 b
        assertEquals("a\uD83D\uDE00", ed.text)
        ed.backspace()                       // 一次删除整个 😀，不留半个代理对
        assertEquals("a", ed.text)
    }

    @Test
    fun moveLeftJumpsOverEmoji() {
        val ed = newEditor()
        ed.text = "a\uD83D\uDE00b"
        ed.moveCursorTo(3)                   // b 之前（😀 之后）
        ed.moveLeft()                        // 应跨过整个 emoji 落到 a 后（索引 1），而非代理对中间（索引 2）
        ed.insertText("X")
        assertEquals("aX\uD83D\uDE00b", ed.text)
    }

    @Test
    fun forwardDeleteRemovesWholeEmoji() {
        val ed = newEditor()
        ed.text = "a\uD83D\uDE00b"
        ed.moveCursorTo(1)
        ed.delete()
        assertEquals("ab", ed.text)
    }

    @Test
    fun shiftRightExtendsByCluster() {
        val ed = newEditor()
        ed.text = "\uD83D\uDE00x"            // 😀 + x
        ed.selectRange(0, 0)
        ed.selectRight()                     // focus 扩到整簇边界 2，而非代理对中间 1
        ed.insertText("Y")                   // 选区被替换
        assertEquals("Yx", ed.text)
    }

    @Test
    fun combiningAccentDeletedAsUnit() {
        val ed = newEditor()
        ed.text = "e\u0301a"                 // é(e+U+0301) + a
        ed.moveCursorTo(ed.text.length)
        ed.backspace()                       // 删 a
        assertEquals("e\u0301", ed.text)
        ed.backspace()                       // 整体删 é，重音不残留
        assertEquals("", ed.text)
    }

    @Test
    fun undoRestoresEmojiIntact() {
        val ed = newEditor()
        ed.text = "a\uD83D\uDE00b"
        ed.moveCursorTo(ed.text.length)
        ed.backspace()
        ed.undo()
        assertEquals("a\uD83D\uDE00b", ed.text)
    }
}
