package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue
import kotlin.math.max
import kotlin.math.min

open class EditableTextNode(
    context: StateHolder<Node>,
    private val maxHistorySize: Int = 100
) : WrappedTextNode(context) {

    private var _text by createSignal("")
    override val text: String get() = _text

    open val cursorColor: ColorInt = rgba(0, 0, 0)
    open val cursorWidth: Float = 2f

    private var cursorVisible by createSignal(true)
    private var composingText by createSignal("")
    private var composingCursorPos by createSignal(0)

    private val undoRedo = UndoRedo(maxHistorySize)
    val canUndo: Boolean get() = undoRedo.canUndo
    val canRedo: Boolean get() = undoRedo.canRedo

    private val selectionStart: Int
        get() = min(anchorIndex, focusIndex).coerceAtLeast(0)

    private val selectionEnd: Int
        get() = max(anchorIndex, focusIndex).coerceIn(0, text.length)

    private val hasSelection: Boolean
        get() = anchorIndex >= 0 && focusIndex >= 0 && anchorIndex != focusIndex

    private fun cursorIndex(): Int {
        return if (anchorIndex >= 0) anchorIndex else 0
    }

    private fun setCursor(idx: Int) {
        val clamped = idx.coerceIn(0, text.length)
        anchorIndex = clamped
        focusIndex = clamped
    }

    private fun selectRange(start: Int, end: Int) {
        anchorIndex = start.coerceIn(0, text.length)
        focusIndex = end.coerceIn(0, text.length)
    }

    fun undo() {
        val current = TextState(text, cursorIndex())
        undoRedo.undo(current)?.let { applyState(it) }
    }

    fun redo() {
        val current = TextState(text, cursorIndex())
        undoRedo.redo(current)?.let { applyState(it) }
    }

    fun insertText(inserted: String) {
        if (hasSelection) {
            replaceSelection(inserted)
            return
        }
        val pos = cursorIndex()
        undoRedo.push(InsertTextAction(pos, inserted))
        _text = _text.insert(pos, inserted)
        setCursor(pos + inserted.length)
    }

    fun replaceSelection(replacement: String) {
        val s = selectionStart
        val e = selectionEnd
        if (s == e) {
            insertText(replacement)
            return
        }
        val originalSelected = _text.substring(s, e)
        undoRedo.push(ReplaceSelectionAction(s, originalSelected, replacement))
        _text = _text.substring(0, s) + replacement + _text.substring(e)
        setCursor(s + replacement.length)
    }

    fun backspace() {
        if (hasSelection) {
            deleteSelection()
            return
        }
        val pos = cursorIndex()
        if (pos <= 0) return
        val deleted = _text.substring(pos - 1, pos)
        undoRedo.push(DeleteTextAction(pos - 1, deleted, isBackspace = true))
        _text = _text.removeRange(pos - 1, pos)
        setCursor(pos - 1)
    }

    fun delete() {
        if (hasSelection) {
            deleteSelection()
            return
        }
        val pos = cursorIndex()
        if (pos >= _text.length) return
        val deleted = _text.substring(pos, pos + 1)
        undoRedo.push(DeleteTextAction(pos, deleted, isBackspace = false))
        _text = _text.removeRange(pos, pos + 1)
        setCursor(pos)
    }

    fun deleteSelection() {
        if (!hasSelection) return
        val s = selectionStart
        val e = selectionEnd
        val deleted = _text.substring(s, e)
        undoRedo.push(DeleteTextAction(s, deleted, isBackspace = true))
        _text = _text.removeRange(s, e)
        setCursor(s)
    }

    fun moveCursorLeft() {
        val pos = cursorIndex()
        if (pos > 0) setCursor(pos - 1)
    }

    fun moveCursorRight() {
        val pos = cursorIndex()
        if (pos < _text.length) setCursor(pos + 1)
    }

    fun moveCursorHome() {
        setCursor(0)
    }

    fun moveCursorEnd() {
        setCursor(_text.length)
    }

    fun selectAll() {
        selectRange(0, _text.length)
    }

    fun selectLeft() {
        val anchor = if (anchorIndex >= 0) anchorIndex else cursorIndex()
        val focus = focusIndex.coerceIn(0, _text.length)
        if (focus > 0) {
            anchorIndex = anchor
            focusIndex = focus - 1
        }
    }

    fun selectRight() {
        val anchor = if (anchorIndex >= 0) anchorIndex else cursorIndex()
        val focus = focusIndex.coerceIn(0, _text.length)
        if (focus < _text.length) {
            anchorIndex = anchor
            focusIndex = focus + 1
        }
    }

    private fun applyState(state: TextState) {
        _text = state.text
        setCursor(state.cursor)
    }

    init {
        val engineGlobal = context.consume(engineGlobalContext)!!
        val d3 = engineGlobal.registerKeyPress { e ->
            handleKeyPress(e)
        }
        val d4 = engineGlobal.registerComposingText { text, cursorPosition ->
            composingText = text
            composingCursorPos = cursorPosition
        }
        context.addDestroy { d3(); d4() }
    }

    private fun handleKeyPress(e: KeyEvent) {
        when {
            e.ctrl && e.key == 'z' -> undo()
            e.ctrl && e.key == 'y' -> redo()
            e.ctrl && e.key == 'a' -> selectAll()
            e.code == KeyCode.Backspace -> backspace()
            e.code == KeyCode.Delete -> delete()
            e.code == KeyCode.Left -> if (e.shift) selectLeft() else moveCursorLeft()
            e.code == KeyCode.Right -> if (e.shift) selectRight() else moveCursorRight()
            e.code == KeyCode.Home -> moveCursorHome()
            e.code == KeyCode.End -> moveCursorEnd()
            e.code == KeyCode.Enter -> insertText("\n")
            e.code == KeyCode.Tab -> insertText("\t")
            e.ctrl || e.alt -> { }
            e.key.code < 0x20 || e.key.code == 0x7F -> { }
            else -> {
                composingText = ""
                insertText(e.key.toString())
            }
        }
    }

    override fun draw(canvas: PlatformCanvas) {

        super.draw(canvas)
        if (!hasSelection && cursorVisible) {
            val pos = cursorIndex()
            drawCursor(canvas, pos)
        }

        if (composingText.isNotEmpty()) {
            drawComposingText(canvas)
        }
    }

    private fun drawComposingText(canvas: PlatformCanvas) {
        val pos = cursorIndex()
        val lineList = lines()

        if (lineList.isEmpty()) {
            canvas.drawText(
                composingText, 0f, fontSize,
                fontFamily, fontWeight, fontSize,
                color = color
            )
            return
        }

        var targetLine = 0
        var xInLine = 0f
        for ((li, line) in lineList.withIndex()) {
            if (pos >= line.start && pos <= line.end) {
                targetLine = li
                xInLine = measureText(
                    text.substring(line.start, pos),
                    fontFamily, fontWeight, fontSize
                )
                break
            }
        }

        canvas.drawText(
            composingText, xInLine, targetLine * lineHeight + fontSize,
            fontFamily, fontWeight, fontSize,
            color = color
        )
    }

    private fun drawCursor(canvas: PlatformCanvas, charPos: Int) {
        val lineList = lines()
        if (lineList.isEmpty()) {
            canvas.fillRect(
                x = 0f,
                y = 0f,
                w = cursorWidth,
                h = lineHeight,
                color = cursorColor
            )
            return
        }

        var targetLine = 0
        var xInLine = 0f
        for ((li, line) in lineList.withIndex()) {
            if (charPos >= line.start && charPos <= line.end) {
                targetLine = li
                xInLine = measureText(
                    text.substring(line.start, charPos),
                    fontFamily, fontWeight, fontSize
                )
                break
            }
        }

        canvas.fillRect(
            x = xInLine,
            y = targetLine * lineHeight,
            w = cursorWidth,
            h = lineHeight,
            color = cursorColor
        )
    }
}
