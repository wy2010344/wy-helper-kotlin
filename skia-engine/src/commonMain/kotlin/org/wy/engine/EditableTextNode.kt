package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue
import kotlin.math.max
import kotlin.math.min

private var activeEditor: EditableTextNode? = null
private var wasEditorClicked = false
open class EditableTextNode(
    context: StateHolder<Node>,
    private val maxHistorySize: Int = 100
) : WrappedTextNode(context) {
    override var text  by createSignal("")

    open val cursorColor: ColorInt = rgba(0, 0, 0)
    open val cursorWidth: Float = 2f
    private var cursorVisible by createSignal(true)

    private var composingText by createSignal("")
    private var composingCursorPos by createSignal(0)

    private val undoRedo = UndoRedo(maxHistorySize)
    val canUndo: Boolean get() = undoRedo.canUndo
    val canRedo: Boolean get() = undoRedo.canRedo

    private var focused by createSignal(false)
    private var preferredX = Float.NaN
    private var lastOverlayX = Float.NaN
    private var lastOverlayY = Float.NaN

    private val selStart: Int
        get() = min(anchorIndex, focusIndex).coerceAtLeast(0)
    private val selEnd: Int
        get() = max(anchorIndex, focusIndex).coerceIn(0, text.length)
    private val hasSel: Boolean
        get() = anchorIndex >= 0 && focusIndex >= 0 && anchorIndex != focusIndex

    private fun cursor(): Int = if (anchorIndex >= 0) anchorIndex.coerceIn(0, text.length) else 0

    private fun setCursor(idx: Int) {
        val c = idx.coerceIn(0, text.length)
        anchorIndex = c
        focusIndex = c
    }

    private fun selectRange(start: Int, end: Int) {
        anchorIndex = start.coerceIn(0, text.length)
        focusIndex = end.coerceIn(0, text.length)
    }

    fun undo() {
        val current = TextState(text, cursor())
        undoRedo.undo(current)?.let { applyState(it) }
    }

    fun redo() {
        val current = TextState(text, cursor())
        undoRedo.redo(current)?.let { applyState(it) }
    }

    fun insertText(inserted: String) {
        if (hasSel) {
            replaceSel(inserted)
            return
        }
        val pos = cursor()
        undoRedo.push(InsertTextAction(pos, inserted))
        text = text.insert(pos, inserted)
        setCursor(pos + inserted.length)
    }

    private fun replaceSel(replacement: String) {
        val s = selStart
        val e = selEnd
        if (s == e) {
            insertText(replacement)
            return
        }
        val orig = text.substring(s, e)
        undoRedo.push(ReplaceSelectionAction(s, orig, replacement))
        text = text.substring(0, s) + replacement + text.substring(e)
        setCursor(s + replacement.length)
    }

    fun backspace() {
        if (hasSel) {
            delSel()
            return
        }
        val pos = cursor()
        if (pos <= 0) return
        val deleted = text.substring(pos - 1, pos)
        undoRedo.push(DeleteTextAction(pos - 1, deleted, true))
        text = text.removeRange(pos - 1, pos)
        setCursor(pos - 1)
    }

    fun delete() {
        if (hasSel) {
            delSel()
            return
        }
        val pos = cursor()
        if (pos >= text.length) return
        val deleted = text.substring(pos, pos + 1)
        undoRedo.push(DeleteTextAction(pos, deleted, false))
        text = text.removeRange(pos, pos + 1)
        setCursor(pos)
    }

    private fun delSel() {
        if (!hasSel) return
        val s = selStart
        val e = selEnd
        val deleted = text.substring(s, e)
        undoRedo.push(DeleteTextAction(s, deleted, true))
        text = text.removeRange(s, e)
        setCursor(s)
    }

    fun moveLeft() {
        val p = cursor()
        if (p > 0) setCursor(p - 1)
    }

    fun moveRight() {
        val p = cursor()
        if (p < text.length) setCursor(p + 1)
    }

    fun moveHome() = setCursor(0).also { preferredX = Float.NaN }
    fun moveEnd() = setCursor(text.length).also { preferredX = Float.NaN }
    fun selectAll() = selectRange(0, text.length)

    private fun cursorRect(): List<TextRect> {
        val p = paragraph ?: return emptyList()
        val pos = cursor()
        val list = p.getRectsForRange(pos, pos + 1)
        if (list.isNotEmpty()) return list
        if (pos > 0) return p.getRectsForRange(pos - 1, pos)
        return emptyList()
    }

    fun moveUp() {
        val p = paragraph ?: return
        val rects = cursorRect()
        if (rects.isEmpty()) return setCursor(0)
        val r = rects[0]
        if (preferredX.isNaN()) preferredX = r.left + r.width / 2f
        val newPos = p.getGlyphPositionAtCoordinate(preferredX, r.top - 1f)
        setCursor(newPos.coerceIn(0, text.length))
    }

    fun moveDown() {
        val p = paragraph ?: return
        val rects = cursorRect()
        if (rects.isEmpty()) return setCursor(text.length)
        val r = rects[0]
        if (preferredX.isNaN()) preferredX = r.left + r.width / 2f
        val newPos = p.getGlyphPositionAtCoordinate(preferredX, r.bottom + 1f)
        setCursor(newPos.coerceIn(0, text.length))
    }

    fun selectUp() {
        val p = paragraph ?: return
        val rects = cursorRect()
        if (rects.isEmpty()) return
        val r = rects[0]
        if (preferredX.isNaN()) preferredX = r.left + r.width / 2f
        val a = if (anchorIndex >= 0) anchorIndex else cursor()
        val newPos = p.getGlyphPositionAtCoordinate(preferredX, r.top - 1f)
        anchorIndex = a
        focusIndex = newPos.coerceIn(0, text.length)
    }

    fun selectDown() {
        val p = paragraph ?: return
        val rects = cursorRect()
        if (rects.isEmpty()) return
        val r = rects[0]
        if (preferredX.isNaN()) preferredX = r.left + r.width / 2f
        val a = if (anchorIndex >= 0) anchorIndex else cursor()
        val newPos = p.getGlyphPositionAtCoordinate(preferredX, r.bottom + 1f)
        anchorIndex = a
        focusIndex = newPos.coerceIn(0, text.length)
    }

    private fun applyState(state: TextState) {
        text = state.text
        setCursor(state.cursor)
    }

    private val g: EngineGlobal

    init {
        g = context.consume(engineGlobalContext)!!
        val d0 = g.registerMouseDown { wasEditorClicked = false }
        val d3 = g.registerKeyPress { handleKey(it) }
        val d4 = g.registerComposingText { t, p ->
            composingText = t
            composingCursorPos = p
        }
        val d5 = g.registerMouseUp {
            if (!wasEditorClicked && activeEditor != null) {
                activeEditor?.focused = false
                activeEditor?.hideOverlay()
                activeEditor = null
            }
        }
        context.addDestroy { d0(); d3(); d4(); d5(); hideOverlay() }
    }

    private fun handleKey(e: KeyEvent) {
        focused = true
        when {
            e.ctrl && e.key == 'z' -> undo()
            e.ctrl && e.key == 'y' -> redo()
            e.ctrl && e.key == 'a' -> selectAll()
            e.code == KeyCode.Backspace -> backspace().also { preferredX = Float.NaN }
            e.code == KeyCode.Delete -> delete().also { preferredX = Float.NaN }
            e.code == KeyCode.Left -> if (e.shift) selectLeft().also { preferredX = Float.NaN } else moveLeft().also { preferredX = Float.NaN }
            e.code == KeyCode.Right -> if (e.shift) selectRight().also { preferredX = Float.NaN } else moveRight().also { preferredX = Float.NaN }
            e.code == KeyCode.Up -> if (e.shift) selectUp() else moveUp()
            e.code == KeyCode.Down -> if (e.shift) selectDown() else moveDown()
            e.code == KeyCode.Home -> moveHome()
            e.code == KeyCode.End -> moveEnd()
            e.code == KeyCode.Enter -> insertText("\n").also { preferredX = Float.NaN }
            e.code == KeyCode.Tab -> insertText("\t").also { preferredX = Float.NaN }
            e.ctrl || e.alt -> {}
            e.key.code < 0x20 || e.key.code == 0x7F -> {}
            else -> {
                preferredX = Float.NaN
                composingText = ""
                insertText(e.key.toString())
            }
        }
    }

    fun selectLeft() {
        val a = if (anchorIndex >= 0) anchorIndex else cursor()
        val f = focusIndex.coerceIn(0, text.length)
        if (f > 0) {
            anchorIndex = a; focusIndex = f - 1
        }
    }

    fun selectRight() {
        val a = if (anchorIndex >= 0) anchorIndex else cursor()
        val f = focusIndex.coerceIn(0, text.length)
        if (f < text.length) {
            anchorIndex = a; focusIndex = f + 1
        }
    }

    override fun mouseDownCapture(e: MouseEvent) {
        super.mouseDownCapture(e)
        preferredX = Float.NaN
        wasEditorClicked = true
        val prev = activeEditor
        if (prev != null && prev != this) {
            prev.focused = false
            prev.hideOverlay()
        }
        activeEditor = this
        focused = true
        showOverlay()
    }

    private fun showOverlay() {
        val p = paragraph ?: return
        val pos = cursor()
        val list = p.getRectsForRange(pos, pos + 1)
        val (ox, oy) = if (list.isNotEmpty()) {
            (absoluteX + list[0].left) to (absoluteY + list[0].top)
        } else if (pos > 0) {
            val r = p.getRectsForRange(pos - 1, pos)
            if (r.isNotEmpty()) (absoluteX + r[0].right) to (absoluteY + r[0].top)
            else absoluteX to absoluteY
        } else {
            absoluteX to absoluteY
        }
        lastOverlayX = ox
        lastOverlayY = oy
        g.requestInputOverlay(ox, oy, 1f, 1f, fontSize)
    }

    private fun updateOverlayPosition() {
        if (!focused) return
        val p = paragraph ?: return
        val pos = cursor()
        val list = p.getRectsForRange(pos, pos + 1)
        val (ox, oy) = if (list.isNotEmpty()) {
            (absoluteX + list[0].left) to (absoluteY + list[0].top)
        } else if (pos > 0) {
            val r = p.getRectsForRange(pos - 1, pos)
            if (r.isNotEmpty()) (absoluteX + r[0].right) to (absoluteY + r[0].top)
            else absoluteX to absoluteY
        } else {
            absoluteX to absoluteY
        }
        if (ox != lastOverlayX || oy != lastOverlayY) {
            lastOverlayX = ox
            lastOverlayY = oy
            g.requestInputOverlay(ox, oy, 1f, 1f, fontSize)
        }
    }

    private fun hideOverlay() {
        if (activeEditor == this) {
            g.hideInputOverlay()
            activeEditor = null
        }
    }

    override fun draw(canvas: PlatformCanvas) {
        val p = paragraph ?: return

        if (hasSel) {
            val s = min(anchorIndex, focusIndex)
            val e = max(anchorIndex, focusIndex)
            for (rect in p.getRectsForRange(s, e)) {
                canvas.fillRect(rect.left, rect.top, rect.width, rect.height, selectionColor)
            }
        }

        canvas.drawParagraph(p, 0f, 0f)
        super.draw(canvas)

        updateOverlayPosition()

        if (!hasSel && cursorVisible && focused) {
            drawCursor(canvas, cursor())
        }

        if (composingText.isNotEmpty()) {
            drawComposing(canvas)
        }
    }

    private fun drawCursor(canvas: PlatformCanvas, pos: Int) {
        val p = paragraph ?: return
        val list = p.getRectsForRange(pos, pos + 1)
        if (list.isNotEmpty()) {
            val r = list[0]
            canvas.fillRect(r.left, r.top, cursorWidth, r.height, cursorColor)
        } else if (pos > 0) {
            val list2 = p.getRectsForRange(pos - 1, pos)
            if (list2.isNotEmpty()) {
                val r = list2[0]
                canvas.fillRect(r.right - cursorWidth, r.top, cursorWidth, r.height, cursorColor)
            }
        }
    }

    private fun drawComposing(canvas: PlatformCanvas) {
        val p = paragraph ?: return
        val pos = cursor()
        val list = p.getRectsForRange(pos, pos + 1)
        val x: Float
        val y: Float
        val h: Float
        if (list.isNotEmpty()) {
            x = list[0].left
            y = list[0].top
            h = list[0].height
        } else {
            x = 0f
            y = 0f
            h = lineHeight
        }

        val cp = buildParagraph(
            text = composingText,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontSize = fontSize,
            fontColor = color,
            lineHeight = lineHeight,
            maxWidth = Float.MAX_VALUE,
            wordBreak = wordBreak
        )
        canvas.drawParagraph(cp, x, y + h - cp.height)
    }
}
