package org.wy.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * UndoRedo 的真实语义：
 * - push(action)：一个编辑动作已完成，action 进入撤销栈，同时清空重做栈。
 * - undo(state)：从撤销栈弹出，应用逆操作，压入重做栈。
 * - redo(state)：从重做栈弹出，应用正操作，压入撤销栈。
 */
class UndoRedoTest {
    @Test
    fun testInsertUndoRedo() {
        val undoRedo = UndoRedo()
        var state = TextState("Hello", 5)

        undoRedo.push(InsertTextAction(5, " World"))
        state = TextState("Hello World", 11)

        state = undoRedo.undo(state) ?: state
        assertEquals("Hello", state.text)
        assertEquals(5, state.cursor)

        state = undoRedo.redo(state) ?: state
        assertEquals("Hello World", state.text)
        assertEquals(11, state.cursor)
    }

    @Test
    fun testDeleteBackspaceUndoRedo() {
        val undoRedo = UndoRedo()
        var state = TextState("Hello World", 11)

        undoRedo.push(DeleteTextAction(6, "World", isBackspace = false))
        state = TextState("Hello ", 6)

        state = undoRedo.undo(state) ?: state
        assertEquals("Hello World", state.text)
        assertEquals(11, state.cursor)

        state = undoRedo.redo(state) ?: state
        assertEquals("Hello ", state.text)
        assertEquals(6, state.cursor)
    }

    @Test
    fun testReplaceSelectionUndoRedo() {
        val undoRedo = UndoRedo()
        var state = TextState("Hello World", 0)

        undoRedo.push(ReplaceSelectionAction(0, "Hello", "Hi"))
        state = TextState("Hi World", 2)

        state = undoRedo.undo(state) ?: state
        assertEquals("Hello World", state.text)
        assertEquals(0, state.cursor)

        state = undoRedo.redo(state) ?: state
        assertEquals("Hi World", state.text)
        assertEquals(2, state.cursor)
    }

    @Test
    fun testMultipleUndoRedo() {
        val undoRedo = UndoRedo()
        var state = TextState("", 0)

        undoRedo.push(InsertTextAction(0, "A"))
        state = TextState("A", 1)
        undoRedo.push(InsertTextAction(1, "B"))
        state = TextState("AB", 2)
        undoRedo.push(InsertTextAction(2, "C"))
        state = TextState("ABC", 3)
        assertEquals("ABC", state.text)

        state = undoRedo.undo(state) ?: state
        assertEquals("AB", state.text)
        state = undoRedo.undo(state) ?: state
        assertEquals("A", state.text)

        state = undoRedo.redo(state) ?: state
        assertEquals("AB", state.text)
    }

    @Test
    fun testRedoAfterNewActionClearsRedoStack() {
        val undoRedo = UndoRedo()
        var state = TextState("", 0)

        undoRedo.push(InsertTextAction(0, "A"))
        state = TextState("A", 1)

        state = undoRedo.undo(state) ?: state
        assertEquals("", state.text)
        assertTrue(undoRedo.canRedo)

        undoRedo.push(InsertTextAction(0, "B"))
        state = TextState("B", 1)
        assertFalse(undoRedo.canRedo)

        state = undoRedo.undo(state) ?: state
        assertEquals("", state.text)
        assertTrue(undoRedo.canRedo)
    }

    @Test
    fun testEmptyUndoRedo() {
        val undoRedo = UndoRedo()
        val state = TextState("Hello", 5)
        assertNull(undoRedo.undo(state))
        assertNull(undoRedo.redo(state))
    }

    @Test
    fun testMaxHistorySize() {
        val undoRedo = UndoRedo(maxHistorySize = 3)
        var state = TextState("", 0)

        repeat(5) { i ->
            undoRedo.push(InsertTextAction(state.text.length, "X$i"))
            state = TextState(state.text + "X$i", state.text.length + 2)
        }

        assertTrue(undoRedo.canUndo)
        state = undoRedo.undo(state) ?: state
        assertTrue(undoRedo.canUndo)
        state = undoRedo.undo(state) ?: state
        assertTrue(undoRedo.canUndo)
        state = undoRedo.undo(state) ?: state
        assertFalse(undoRedo.canUndo)
    }

    @Test
    fun testUndoStackOverflowDropsOldest() {
        val undoRedo = UndoRedo(maxHistorySize = 2)
        var state = TextState("", 0)

        repeat(3) { i ->
            undoRedo.push(InsertTextAction(state.text.length, "X$i"))
            state = TextState(state.text + "X$i", state.text.length + 2)
        }

        // 最早的 X0 被挤出历史，只能撤销到 X1
        state = undoRedo.undo(state) ?: state
        assertEquals("X0X1", state.text)
        state = undoRedo.undo(state) ?: state
        assertEquals("X0", state.text)
        assertFalse(undoRedo.canUndo)
    }
}
