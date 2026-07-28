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

    private fun getCharRect(charIdx: Int): TextRect? {
        val p = paragraph() ?: return null
        val clamped = charIdx.coerceIn(0, text.length)
        if (text.isEmpty()) return null
        return if (clamped >= text.length) {
            val rects = p.getRectsForRange(text.length - 1, text.length)
            if (rects.isNotEmpty()) {
                val last = rects.last()
                TextRect(last.right, last.top, last.right, last.bottom)
            } else null
        } else {
            val rects = p.getRectsForRange(clamped, clamped + 1)
            if (rects.isNotEmpty()) rects.first() else null
        }
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
            drawCursor(canvas)
        }
        if (composingText.isNotEmpty()) {
            drawComposingText(canvas)
        }
    }

    private fun drawComposingText(canvas: PlatformCanvas) {
        val pos = cursorIndex()
        val rect = getCharRect(pos)
        val composingParagraph = buildParagraph(
            text = composingText,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontSize = fontSize,
            fontColor = color,
            lineHeight = lineHeight,
            maxWidth = Float.MAX_VALUE,
            wordBreak = WordBreak.BREAK_WORD
        )
        if (rect != null) {
            canvas.drawParagraph(composingParagraph, rect.left, rect.top)
        } else {
            canvas.drawParagraph(composingParagraph, 0f, 0f)
        }
    }

    private fun drawCursor(canvas: PlatformCanvas) {
        val pos = cursorIndex()
        val rect = getCharRect(pos)
        if (rect != null) {
            canvas.fillRect(
                x = rect.left,
                y = rect.top,
                w = cursorWidth,
                h = rect.height,
                color = cursorColor
            )
        } else {
            canvas.fillRect(
                x = 0f,
                y = 0f,
                w = cursorWidth,
                h = lineHeight,
                color = cursorColor
            )
        }
    }
}
