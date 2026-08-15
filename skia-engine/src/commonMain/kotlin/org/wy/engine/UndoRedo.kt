package org.wy.engine

data class TextState(val text: String, val cursor: Int)

interface TextEditAction {
    fun undo(state: TextState): TextState
    fun redo(state: TextState): TextState
}

class InsertTextAction(
    private val position: Int,
    private val inserted: String
) : TextEditAction {
    override fun undo(state: TextState): TextState {
        return TextState(
            state.text.removeRange(position, position + inserted.length),
            position
        )
    }

    override fun redo(state: TextState): TextState {
        return TextState(
            state.text.insert(position, inserted),
            position + inserted.length
        )
    }
}

class DeleteTextAction(
    private val position: Int,
    private val deleted: String,
    private val isBackspace: Boolean
) : TextEditAction {
    override fun undo(state: TextState): TextState {
        return TextState(
            state.text.insert(position, deleted),
            if (isBackspace) position else position + deleted.length
        )
    }

    override fun redo(state: TextState): TextState {
        return TextState(
            state.text.removeRange(position, position + deleted.length),
            position
        )
    }
}

class ReplaceSelectionAction(
    private val position: Int,
    private val originalSelected: String,
    private val replacement: String
) : TextEditAction {
    override fun undo(state: TextState): TextState {
        val newText = state.text.substring(0, position) +
                originalSelected +
                state.text.substring(position + replacement.length)
        return TextState(newText, position)
    }

    override fun redo(state: TextState): TextState {
        val newText = state.text.substring(0, position) +
                replacement +
                state.text.substring(position + originalSelected.length)
        return TextState(newText, position + replacement.length)
    }
}

class ReplaceRangeAction(
    private val position:Int,
    private val removed: String,
    private val inserted: String
): TextEditAction{
    override fun undo(state: TextState): TextState {
        val newText=state.text.substring(0,position)+removed+state.text.substring(position+inserted.length)
        return TextState(newText,position)
    }

    override fun redo(state: TextState): TextState {
        val newText=state.text.substring(0,position)+inserted+state.text.substring(position+removed.length)
        return TextState(newText,position)
    }
}


@Suppress("NewApi")
class UndoRedo(private val maxHistorySize: Int = 100) {
    private val undoStack = mutableListOf<TextEditAction>()
    private val redoStack = mutableListOf<TextEditAction>()

    fun push(action: TextEditAction) {
        // 动作进入撤销栈，重做栈作废
        undoStack.add(action)
        redoStack.clear()
        if (undoStack.size > maxHistorySize) {
            undoStack.removeFirst()
        }
    }

    fun undo(currentState: TextState): TextState? {
        if (undoStack.isEmpty()) return null
        val action = undoStack.removeLast()
        redoStack.add(action)
        return action.undo(currentState)
    }

    fun redo(currentState: TextState): TextState? {
        if (redoStack.isEmpty()) return null
        val action = redoStack.removeLast()
        undoStack.add(action)
        if (undoStack.size > maxHistorySize) {
            undoStack.removeFirst()
        }
        return action.redo(currentState)
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}