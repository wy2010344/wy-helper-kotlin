package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue
import kotlin.math.max

open class EditableTextNode(
    context: StateHolder<Node,List<Node>>,
    private val maxHistorySize: Int = 100
) : WrappedTextNode(context), ComposingTextHandler {
    override var text  by createSignal("")

    override val focusable: Boolean get() = true

    override fun cursorAt(x: Float, y: Float) = CursorType.TEXT

    open val cursorColor: ColorInt = rgba(0, 0, 0)
    open val cursorWidth: Float = 2f
    override val selectionColor: ColorInt get() = rgba(80, 140, 255, 80)

    open val composingBackgroundColor=rgba(200,200,255,50)
    open val composingUnderlineColor=rgba(0,0,0,140)
    private var cursorVisible by createSignal(true)

    open val singleLine: Boolean get() = false

    private var composingStart by createSignal(0)
    private var composingLength by createSignal(0)
    private var compositionBase:Pair<Int,String>?=null
    internal var composingText by createSignal("")
    internal var composingCursorPos by createSignal(0)

    private val undoRedo = UndoRedo(maxHistorySize)
    val canUndo: Boolean get() = undoRedo.canUndo
    val canRedo: Boolean get() = undoRedo.canRedo

    private var preferredX = Float.NaN

    /**
     * 声明式输入法输入框数据：直接由源状态（光标索引 / 段落布局 / 绝对位置）派生计算，
     * 由 Renderer 的 overlayTrack 观察，光标移动、文字或布局变化时自动重新定位。
     */
    override fun inputOverlay(): InputOverlayData? {
        if (engineGlobal.activeEditor !== this) return null
        val (ox, oy) = overlayOrigin()
        return InputOverlayData(ox, oy, 1f, 1f, fontSize)
    }

    private fun cursor(): Int = if (anchorIndex >= 0) anchorIndex.coerceIn(0, text.length) else 0

    private fun setCursor(idx: Int) {
        val c = idx.coerceIn(0, text.length)
        anchorIndex = c
        focusIndex = c
    }

    private val inComposing: Boolean
        get() = composingLength > 0

    fun undo() {
        if(inComposing)return
        val current = TextState(text, cursor())
        undoRedo.undo(current)?.let { applyState(it) }
    }

    fun redo() {
        val current = TextState(text, cursor())
        undoRedo.redo(current)?.let { applyState(it) }
    }
    fun insertText(inserted: String) {
        val textToInsert = if (singleLine) inserted.replace("\n", "").replace("\r", "") else inserted
        if (textToInsert.isEmpty()) return
        if (hasSel) {
            replaceSel(textToInsert)
            return
        }
        val pos = cursor()
        undoRedo.push(InsertTextAction(pos, textToInsert))
        text = text.insert(pos, textToInsert)
        setCursor(pos + textToInsert.length)
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

    private fun cursorRect(): List<TextRect> {
        val p = paragraph ?: return emptyList()
        val pos = cursor()
        val list = p.getRectsForRange(pos, pos + 1, RectStyle.TIGHT)
        if (list.isNotEmpty()) return list
        if (pos > 0) return p.getRectsForRange(pos - 1, pos, RectStyle.TIGHT)
        return emptyList()
    }

    fun moveUp() {
        val p = paragraph ?: return
        val rects = cursorRect()
        if (rects.isEmpty()) return setCursor(0)
        val r = rects[0]
        if (preferredX.isNaN()) preferredX = r.left + r.width / 2f
        val step = r.bottom - r.top
        val newPos = p.getGlyphPositionAtCoordinate(preferredX, r.top - step)
        setCursor(newPos.coerceIn(0, text.length))
    }

    fun moveDown() {
        val p = paragraph ?: return
        val rects = cursorRect()
        if (rects.isEmpty()) return setCursor(text.length)
        val r = rects[0]
        if (preferredX.isNaN()) preferredX = r.left + r.width / 2f
        val step = r.bottom - r.top
        val newPos = p.getGlyphPositionAtCoordinate(preferredX, r.bottom + step)
        setCursor(newPos.coerceIn(0, text.length))
    }

    fun selectUp() {
        val p = paragraph ?: return
        val rects = cursorRect()
        if (rects.isEmpty()) return
        val r = rects[0]
        if (preferredX.isNaN()) preferredX = r.left + r.width / 2f
        val a = if (anchorIndex >= 0) anchorIndex else cursor()
        val step = r.bottom - r.top
        val newPos = p.getGlyphPositionAtCoordinate(preferredX, r.top - step)
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
        val step = r.bottom - r.top
        val newPos = p.getGlyphPositionAtCoordinate(preferredX, r.bottom + step)
        anchorIndex = a
        focusIndex = newPos.coerceIn(0, text.length)
    }

    private fun applyState(state: TextState) {
        text = state.text
        setCursor(state.cursor)
    }

    private var selectionManager: SelectionManager? = null

    init {
        selectionManager = context.consume(selectionManagerContext)
        context.addDestroy {
            if (engineGlobal.activeEditor == this) hideOverlay()
            selectionManager?.clear()
        }
    }

    override fun handleKey(e: KeyEvent): Boolean {
        when {
            e.ctrl && !e.shift && e.key == 'z' -> { undo(); return true }
            (e.ctrl && e.key == 'y') || (e.ctrl && e.shift && e.key == 'z') -> { redo(); return true }
            e.code == KeyCode.Backspace -> { backspace(); preferredX = Float.NaN; return true }
            e.code == KeyCode.Delete -> { delete(); preferredX = Float.NaN; return true }
            e.code == KeyCode.Left -> { if (e.shift) selectLeft() else moveLeft(); preferredX = Float.NaN; return true }
            e.code == KeyCode.Right -> { if (e.shift) selectRight() else moveRight(); preferredX = Float.NaN; return true }
            e.code == KeyCode.Up -> { if (e.shift) selectUp() else moveUp(); return true }
            e.code == KeyCode.Down -> { if (e.shift) selectDown() else moveDown(); return true }
            e.code == KeyCode.Home -> { moveHome(); return true }
            e.code == KeyCode.End -> { moveEnd(); return true }
            e.code == KeyCode.Enter -> { if (!singleLine) insertText("\n"); preferredX = Float.NaN; return true }
            e.code == KeyCode.Tab -> { if (!singleLine) { insertText("\t"); return true }; return false }
            e.ctrl || e.alt -> return false
            e.key.code < 0x20 || e.key.code == 0x7F -> return false
            else -> {
                preferredX = Float.NaN
                composingText = ""
                insertText(e.key.toString())
                return true
            }
        }
    }

    override fun copy() {
        if (!hasSel) return
        clipboardSetText(text.substring(selStart, selEnd))
    }

    override fun cut() {
        if (!hasSel) return
        clipboardSetText(text.substring(selStart, selEnd))
        delSel()
    }

    override fun paste() {
        val t = clipboardGetText() ?: return
        if (t.isEmpty()) return
        preferredX = Float.NaN
        composingText = ""
        insertText(t)
    }

    override fun onComposing(text: String, cursorPosition: Int) {
        composingText = text
        composingCursorPos = cursorPosition
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

    override fun onPointerDownCapture(e: PointerEvent) {
        super.onPointerDownCapture(e)
        preferredX = Float.NaN
    }

    override fun onPointerMoveCapture(e: PointerEvent) {
        super.onPointerMoveCapture(e)
    }

    override fun onPointerUpCapture(e: PointerEvent) {
        super.onPointerUpCapture(e)
    }
    private fun overlayOrigin(): Pair<Float, Float> {
        val pos = cursor()
        val p = paragraph
        if (p != null) {
            val list = p.getRectsForRange(pos, pos + 1, RectStyle.TIGHT)
            if (list.isNotEmpty()) {
                return (absoluteX + paddingInlineStart + list[0].left) to
                       (absoluteY + paddingBlockStart + list[0].top)
            }
            if (pos > 0) {
                val r = p.getRectsForRange(pos - 1, pos, RectStyle.TIGHT)
                if (r.isNotEmpty()) {
                    return (absoluteX + paddingInlineStart + r[0].right) to
                           (absoluteY + paddingBlockStart + r[0].top)
                }
            }
        }
        return absoluteX + paddingInlineStart to absoluteY + paddingBlockStart
    }

    /** 隐藏输入法输入框，仅当自己是当前活跃编辑器时才真正隐藏，避免打断其他编辑器正在进行的输入 */
    internal fun hideOverlay() {
        if (engineGlobal.activeEditor === this) {
            engineGlobal.activeEditor = null
        }
    }

    /** 焦点变化时维护活跃编辑器：全局仅一个活跃编辑器持有输入框 */
    internal fun updateFocusOverlay() {
        if (isFocused) {
            if (engineGlobal.activeEditor !== this) {
                engineGlobal.activeEditor = this
            }
        } else if (engineGlobal.activeEditor === this) {
            hideOverlay()
        }
    }

    override fun draw(canvas: PlatformCanvas) {
        updateFocusOverlay()
        super.draw(canvas)

        if (!hasSel && cursorVisible && isFocused) {
            drawCursor(canvas, cursor())
        }

        if (composingText.isNotEmpty()) {
            drawComposing(canvas)
        }
    }

    private fun drawCursor(canvas: PlatformCanvas, pos: Int) {
        val p = paragraph
        if (p == null) {
            canvas.fillRect(
                paddingInlineStart,
                paddingBlockStart,
                cursorWidth,
                max(fontSize * 1.4f, 8f),
                cursorColor
            )
            return
        }
        val px = paddingInlineStart
        val py = paddingBlockStart
        val list = p.getRectsForRange(pos, pos + 1, RectStyle.TIGHT)
        if (list.isNotEmpty()) {
            val r = list[0]
            canvas.fillRect(r.left + px, r.top + py, cursorWidth, r.height, cursorColor)
        } else if (pos > 0) {
            val list2 = p.getRectsForRange(pos - 1, pos, RectStyle.TIGHT)
            if (list2.isNotEmpty()) {
                val r = list2[0]
                canvas.fillRect(r.right - cursorWidth + px, r.top + py, cursorWidth, r.height, cursorColor)
            }
        }
    }
    private fun drawComposing(canvas: PlatformCanvas) {
        if (composingLength <= 0) return
        val p = paragraph ?: return
        val px = paddingInlineStart
        val py = paddingBlockStart
        val s = composingStart
        val e = s + composingLength
        for (rect in p.getRectsForRange(s, e, RectStyle.TIGHT)) {
            canvas.fillRect(rect.left + px, rect.top + py, rect.width, rect.height, composingBackgroundColor)
            canvas.fillRect(rect.left + px, rect.bottom - 2f + py, rect.width, 2f, composingUnderlineColor)
        }
    }

    fun cancelComposition() {
        val start = composingStart
        val len = composingLength
        val base = compositionBase
        if (base != null && len > 0) {
            val restored = text.substring(0, start) + base.second + text.substring(start + len)
            if (restored != text) text = restored
            setCursor(base.first.coerceIn(0, restored.length))
        }
        composingStart = 0
        composingLength = 0
        compositionBase = null
        composingText = ""
        composingCursorPos = 0
        preferredX = Float.NaN
    }

    fun commitComposition() {
        composingStart = 0
        composingLength = 0
        compositionBase = null
        composingText = ""
        composingCursorPos = 0
    }
    protected open fun onComposing(committed: String,composing: String,cursorInComposing:Int){
        if(committed.isEmpty() && composing.isEmpty()){
            cancelComposition()
            return
        }
        if(compositionBase==null){
            val (start,oldLen)=when{
                hasSel -> selStart to (selEnd-selStart)
                else -> cursor().coerceIn(0,text.length) to 0
            }
            compositionBase=start to text.substring(start,start+oldLen)
            composingStart=start
        }
        val inserted=committed+composing
        val start=composingStart
        val oldLen=composingLength
        if(inserted.isNotEmpty() || oldLen>0){
            val newText=text.substring(0,start)+inserted+text.substring(start+oldLen)
            if(newText!=text){
                text=newText
            }
        }
        composingStart=start+committed.length
        composingLength=composing.length
        val caret=composingStart+cursorInComposing.coerceIn(0,composing.length)
        anchorIndex=caret
        focusIndex=caret
        preferredX= Float.NaN
        if(composing.isEmpty()){
            commitComposition()
        }
    }
}
