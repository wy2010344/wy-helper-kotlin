package org.wy.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class UndoRedoTest {
    @Test
    fun testInsertUndoRedo() {
        val undoRedo = UndoRedo()
        var state = TextState("Hello", 5)

        undoRedo.push(InsertTextAction(5, " World"))
        state = undoRedo.redo(state) ?: state
        assertEquals("Hello World", state.text)
        assertEquals(11, state.cursor)

        state = undoRedo.undo(state) ?: state
        assertEquals("Hello", state.text)
        assertEquals(5, state.cursor)
    }

    @Test
    fun testDeleteBackspaceUndoRedo() {
        val undoRedo = UndoRedo()
        var state = TextState("Hello World", 11)

        val deleted = "World"
        undoRedo.push(DeleteTextAction(6, deleted, isBackspace = false))
        state = undoRedo.redo(state) ?: state
        assertEquals("Hello ", state.text)
        assertEquals(6, state.cursor)

        state = undoRedo.undo(state) ?: state
        assertEquals("Hello World", state.text)
        assertEquals(11, state.cursor)
    }

    @Test
    fun testReplaceSelectionUndoRedo() {
        val undoRedo = UndoRedo()
        var state = TextState("Hello World", 0)

        undoRedo.push(ReplaceSelectionAction(0, "Hello", "Hi"))
        state = undoRedo.redo(state) ?: state
        assertEquals("Hi World", state.text)
        assertEquals(2, state.cursor)

        state = undoRedo.undo(state) ?: state
        assertEquals("Hello World", state.text)
        assertEquals(0, state.cursor)
    }

    @Test
    fun testMultipleUndoRedo() {
        val undoRedo = UndoRedo()
        var state = TextState("", 0)

        undoRedo.push(InsertTextAction(0, "A"))
        state = undoRedo.redo(state) ?: state
        undoRedo.push(InsertTextAction(1, "B"))
        state = undoRedo.redo(state) ?: state
        undoRedo.push(InsertTextAction(2, "C"))
        state = undoRedo.redo(state) ?: state
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
        state = undoRedo.redo(state) ?: state
        undoRedo.push(InsertTextAction(1, "B"))
        state = undoRedo.redo(state) ?: state

        state = undoRedo.undo(state) ?: state
        assertEquals("A", state.text)
        assertTrue(undoRedo.canRedo)

        undoRedo.push(InsertTextAction(1, "X"))
        state = undoRedo.redo(state) ?: state
        assertEquals("AX", state.text)
        assertFalse(undoRedo.canRedo)
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
            undoRedo.push(InsertTextAction(i, "X$i"))
            state = undoRedo.redo(state) ?: state
        }

        assertTrue(undoRedo.canUndo)
        state = undoRedo.undo(state) ?: state
        assertTrue(undoRedo.canUndo)
        state = undoRedo.undo(state) ?: state
        assertTrue(undoRedo.canUndo)
        state = undoRedo.undo(state) ?: state
        assertFalse(undoRedo.canUndo)
    }
}