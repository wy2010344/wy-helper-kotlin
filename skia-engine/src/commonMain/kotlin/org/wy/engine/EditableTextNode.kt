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

    private var rawText by createSignal("")

    /**
     * 逻辑文本真相源（纯 UTF-16 串）。
     * 写路径统一经 [writeText] 钩子：富文本子类在写入前按差异同步样式段，
     * 父类内部的 `text = ...` 赋值无需关心存储形态。
     */
    override var text: String
        get() = rawText
        set(value) { writeText(value) }

    /** 文本写入钩子：默认直写信号；子类覆写时须调用 super 完成实际落值。 */
    protected open fun writeText(newValue: String) {
        rawText = newValue
    }

    // ---------- 本地光标 / 选区 ----------
    //
    // 唯一可变事实是 caretPair：(anchor, focus) 二元组；null = 光标未落位。
    // 不变量由"单信号整体写入"保证——anchor 与 focus 永远同步更新，
    // 不存在半新半旧的中间态。下方 anchorIndex / focusIndex 只读访问器
    // 供既有逻辑按旧习惯读取。

    /** 本地选区事实；(anchor, focus)，null = 未定位。 */
    private var caretPair by createSignal<Pair<Int, Int>?>(null)

    /** 锚点（按下端 / 保持端）；-1 = 未定位。 */
    private val anchorIndex: Int get() = caretPair?.first ?: -1

    /** 焦点（活动端 / 插入点）；-1 = 未定位。 */
    private val focusIndex: Int get() = caretPair?.second ?: -1

    /** 扩选起点：已定位用 anchor，否则塌缩为当前光标。 */
    private val selAnchor: Int
        get() = if (anchorIndex >= 0) anchorIndex else cursor()

    // ---------- 显示层 tab 层展开（TabRender 集中实现） ----------
    // 显示文本：[displayText] 已做展开；索引映射复用 [TabRender] 共享逻辑。

    /**
     * 本地光标对：活跃编辑器的选区真相源，SelectionManager 派生时直接读取。
     *
     * SelectionManager 需要区分"非塌缩（真选区）"与"塌缩（光标待命态）"两种状态：
     *   - 非塌缩：用户正在编辑器里做选择（键盘扩选 / 双击选词 / selectRange），
     *     优先级应高于"已定格的指针选区"和"旧 programmatic 选区"；
     *   - 塌缩：编辑器只是待命，光标已定位，但没有选中文本。
     *     优先级应**低于**"已定格的指针选区"（否则用户拖选释放后，只要焦点
     *     在编辑器上，选区就消失——Bug 1），但仍**高于** programmatic 选区
     *     （保证编辑器的编辑动作能吸收跨节点全选）。
     *
     * 具体优先级链见 SelectionManager.currentPair。
     */
    internal val cursorSelPair: SelPair?
        get() {
            if (anchorIndex < 0 || focusIndex < 0) return null
            val len = text.length
            return SelPair(
                SelPoint(this, anchorIndex.coerceIn(0, len)),
                SelPoint(this, focusIndex.coerceIn(0, len))
            )
        }

    /** 本地选区（编辑动作的依据）：只认自己的光标信号，不受全局 programmatic 会话污染。 */
    private val localHasSel: Boolean
        get() = anchorIndex >= 0 && focusIndex >= 0 && anchorIndex != focusIndex
    private val localSelStart: Int get() = minOf(anchorIndex, focusIndex).coerceIn(0, text.length)
    private val localSelEnd: Int get() = maxOf(anchorIndex, focusIndex).coerceIn(0, text.length)

    /**
     * 首次键盘/剪贴板交互时把当前全局分配吸收为本地光标（一次性物化，非持续同步）：
     * 保证"拖拽选择后直接打字替换""全选后聚焦打字替换"等交互连续性；
     * 本地光标一旦初始化，后续编辑只由本地信号驱动。
     */
    private fun absorbGlobalSelection() {
        if (anchorIndex >= 0 && focusIndex >= 0) return
        selectionManager?.rangeOf(this)?.let { (s, e) ->
            caretPair = s to e
        }
    }

    override val focusable: Boolean get() = true

    override fun cursorAt(x: Float, y: Float) = CursorType.TEXT

    open val cursorColor: ColorInt = rgba(0, 0, 0)
    open val cursorWidth: Float = 2f
    override val selectionColor: ColorInt get() = rgba(80, 140, 255, 80)

    open val composingBackgroundColor=rgba(200,200,255,50)
    open val composingUnderlineColor=rgba(0,0,0,140)
    private var cursorVisible by createSignal(true)

    open val singleLine: Boolean get() = false

    /** 占位文本：逻辑文本为空时显示，不参与编辑与选区。 */
    var placeholder by createSignal("")

    /** 占位显示颜色。 */
    open val placeholderColor: ColorInt = rgba(128, 128, 128)

    /** 密码模式：每个字素簇显示为一个圆点，编辑仍作用于逻辑文本。 */
    var obscureText by createSignal(false)

    // ---------- 输入法组合态（预编辑上屏） ----------
    //
    // 可变事实只有三个：预编辑串、串内 IME 光标、组合基座（插入点 + 被替换原文）。
    // 组合区间 / 组合中判定全部由事实即时派生，不维护独立的 start/len 计数。

    /** 当前预编辑串（未确认部分）；空串 = 非组合态。 */
    internal var composingText by createSignal("")

    /** 预编辑串内的 IME 光标偏移（overlay 定位参考）。 */
    internal var composingCursorPos by createSignal(0)

    /** 组合基座：插入点 + 被窗口替换的原文（取消时还原）；null = 非组合态。 */
    private var compositionBase: Pair<Int, String>? = null

    /** 是否组合进行中（有未确认的预编辑串）。 */
    private val inComposing: Boolean
        get() = composingText.isNotEmpty()

    /** 组合区间起点 = 基座插入点（派生）。 */
    private val composingStart: Int
        get() = (compositionBase?.first ?: cursor()).coerceIn(0, text.length)

    /** 组合区间长度 = 预编辑串长度（派生）。 */
    private val composingLength: Int
        get() = composingText.length

    private val undoRedo = UndoRedo(maxHistorySize)
    val canUndo: Boolean get() = undoRedo.canUndo
    val canRedo: Boolean get() = undoRedo.canRedo

    // ---------- 显示文本（占位 / 掩码） ----------

    /** 当前是否处于占位显示状态。 */
    protected val showingPlaceholder: Boolean
        get() = text.isEmpty() && placeholder.isNotEmpty()

    /** 段落实际构建用的显示文本：普通文本 / 占位文本 / 逐簇圆点。 */
    internal val displayText: String
        get() = when {
            showingPlaceholder -> placeholder
            obscureText -> buildString {
                var i = 0
                while (i < text.length) {
                    append('•')
                    i = Graphemes.nextBoundary(text, i)
                }
            }
            else -> TabRender.expandTabs(text)
        }

    /** 编辑器段落：占位时显示灰色占位文本，否则由 [displaySpans] 提供正文。 */
    override val spans: List<RichTextSpan>
        get() = if (showingPlaceholder) {
            listOf(
                RichTextSpan(
                    placeholder,
                    RichTextStyle(
                        fontFamily,
                        fontSize,
                        fontWeight,
                        italic,
                        placeholderColor,
                        letterSpacing,
                        wordSpacing,
                        lineHeightMultiplier
                    )
                )
            )
        } else {
            displaySpans()
        }

    /** 正文显示片段（非占位态）：默认单一显示文本；富文本子类覆写为分段样式。 */
    protected open fun displaySpans(): List<RichTextSpan> = listOf(
        RichTextSpan(
            displayText,
            RichTextStyle(
                fontFamily,
                fontSize,
                fontWeight,
                italic,
                color,
                letterSpacing,
                wordSpacing,
                lineHeightMultiplier
            )
        )
    )

    /** 显示索引 → 逻辑索引：占位塌缩为 0；掩码下第 k 个圆点对应第 k 个字素簇起点。 */
    override fun displayToLogicIndex(displayPos: Int): Int = when {
        showingPlaceholder -> 0
        !obscureText -> TabRender.displayToLogic(text, displayPos)
        else -> {
            var i = 0
            var n = 0
            while (i < text.length && n < displayPos) {
                i = Graphemes.nextBoundary(text, i)
                n++
            }
            i
        }
    }

    /** 逻辑索引 → 显示索引：落在字素簇内部时归到该簇的圆点左缘。 */
    override fun logicToDisplayIndex(logicPos: Int): Int = when {
        showingPlaceholder -> 0
        !obscureText -> TabRender.logicToDisplay(text, logicPos)
        else -> {
            var i = 0
            var n = 0
            val limit = logicPos.coerceIn(0, text.length)
            while (i < limit) {
                val next = Graphemes.nextBoundary(text, i)
                if (next > limit) break
                i = next
                n++
            }
            n
        }
    }

    private var preferredX = Float.NaN

    /**
     * 声明式输入法输入框数据：直接由源状态（焦点 / 光标索引 / 段落布局 / 绝对位置）派生计算，
     * 由 Renderer 的 overlayTrack 观察，焦点、光标移动、文字或布局变化时自动重新定位。
     */
    override fun inputOverlay(): InputOverlayData? {
        if (engineGlobal.activeEditor !== this) return null
        val (ox, oy) = overlayOrigin()
        return InputOverlayData(ox, oy, 1f, 1f, fontSize)
    }

    internal fun cursor(): Int = if (anchorIndex >= 0) anchorIndex.coerceIn(0, text.length) else 0

    private fun setCursor(idx: Int) {
        val c = idx.coerceIn(0, text.length)
        caretPair = c to c
    }

    fun undo() {
        if(inComposing)return
        val current = TextState(text, cursor())
        undoRedo.undo(current)?.let { applyState(it) }
    }

    fun redo() {
        if (inComposing) return
        val current = TextState(text, cursor())
        undoRedo.redo(current)?.let { applyState(it) }
    }

    /**
     * 塌缩本地显式选区（由引擎在指针按下落点不在本编辑器时调用）。
     * 平台惯例：外部按下即让位——否则旧的非塌缩选区会在选区派生中
     * 遮蔽随后的拖选 / 双击选词结果。光标锚点保持不动，仅取消高亮范围。
     */
    fun collapseExternalSelection() {
        if (anchorIndex >= 0 && focusIndex != anchorIndex) {
            caretPair = anchorIndex to anchorIndex
        }
    }
    fun insertText(inserted: String) {
        val textToInsert = if (singleLine) inserted.replace("\n", "").replace("\r", "") else inserted
        if (textToInsert.isEmpty()) return
        if (localHasSel) {
            replaceSel(textToInsert)
            return
        }
        val pos = cursor()
        undoRedo.push(InsertTextAction(pos, textToInsert))
        text = text.insert(pos, textToInsert)
        setCursor(pos + textToInsert.length)
    }

    private fun replaceSel(replacement: String) {
        val s = localSelStart
        val e = localSelEnd
        if (s == e) {
            insertText(replacement)
            return
        }
        val orig = text.substring(s, e)
        undoRedo.push(ReplaceSelectionAction(s, orig, replacement))
        text = text.substring(0, s) + replacement + text.substring(e)
        setCursor(s + replacement.length)
    }

    /** 按字素簇删除光标前一个"字符"（emoji / 组合字符不拆半）。 */
    fun backspace() {
        if (localHasSel) {
            delSel()
            return
        }
        val pos = cursor()
        if (pos <= 0) return
        val start = Graphemes.prevBoundary(text, pos)
        if (start >= pos) return
        val deleted = text.substring(start, pos)
        undoRedo.push(DeleteTextAction(start, deleted, true))
        text = text.removeRange(start, pos)
        setCursor(start)
    }

    /** 按字素簇删除光标后一个"字符"。 */
    fun delete() {
        if (localHasSel) {
            delSel()
            return
        }
        val pos = cursor()
        if (pos >= text.length) return
        val end = Graphemes.nextBoundary(text, pos)
        if (end <= pos) return
        val deleted = text.substring(pos, end)
        undoRedo.push(DeleteTextAction(pos, deleted, false))
        text = text.removeRange(pos, end)
        setCursor(pos)
    }

    private fun delSel() {
        if (!localHasSel) return
        val s = localSelStart
        val e = localSelEnd
        val deleted = text.substring(s, e)
        undoRedo.push(DeleteTextAction(s, deleted, true))
        text = text.removeRange(s, e)
        setCursor(s)
    }

    /** 按字素簇左移光标。 */
    fun moveLeft() {
        val p = cursor()
        if (p > 0) setCursor(Graphemes.prevBoundary(text, p))
    }

    /** 按字素簇右移光标。 */
    fun moveRight() {
        val p = cursor()
        if (p < text.length) setCursor(Graphemes.nextBoundary(text, p))
    }

    // ---------- 行 / 文档 / 词 / 页 导航（Home·End·Ctrl+方向键·PageUp·PageDown） ----------

    /** 光标所在软行区间（[start, end)，不含换行符）；无布局时 null。
     *  pos == length 时落在最后一行（半开区间匹配不到行尾光标）。 */
    private fun lineRangeAt(pos: Int): Pair<Int, Int>? {
        val p = paragraph ?: return null
        if (text.isEmpty()) return null
        val m = p.getLineMetrics().firstOrNull { pos >= it.startIndex && pos < it.endIndex }
            ?: p.getLineMetrics().lastOrNull()?.takeIf { pos == text.length }
            ?: return null
        var end = m.endIndex.coerceIn(m.startIndex, text.length)
        if (end > m.startIndex && text[end - 1] == '\n') end--
        if (end > m.startIndex && text[end - 1] == '\r') end--
        return m.startIndex to end
    }

    private fun lineStart(pos: Int): Int = lineRangeAt(pos)?.first ?: 0
    private fun lineEnd(pos: Int): Int = lineRangeAt(pos)?.second ?: text.length

    /** 扩选到 [newPos]：anchor 保持（未初始化则取当前光标），focus 移动。 */
    private fun extendTo(newPos: Int) {
        caretPair = selAnchor to newPos.coerceIn(0, text.length)
    }

    private fun moveTo(newPos: Int, extend: Boolean) {
        if (extend) extendTo(newPos) else setCursor(newPos)
    }

    fun moveHome() {
        preferredX = Float.NaN
        setCursor(lineStart(cursor()))
    }

    fun moveEnd() {
        preferredX = Float.NaN
        setCursor(lineEnd(cursor()))
    }

    fun selectHome() {
        preferredX = Float.NaN
        extendTo(lineStart(focusIndex.coerceIn(0, text.length)))
    }

    fun selectEnd() {
        preferredX = Float.NaN
        extendTo(lineEnd(focusIndex.coerceIn(0, text.length)))
    }

    fun moveDocStart() {
        preferredX = Float.NaN
        setCursor(0)
    }

    fun moveDocEnd() {
        preferredX = Float.NaN
        setCursor(text.length)
    }

    fun selectDocStart() {
        preferredX = Float.NaN
        extendTo(0)
    }

    fun selectDocEnd() {
        preferredX = Float.NaN
        extendTo(text.length)
    }

    fun movePrevWord() {
        preferredX = Float.NaN
        setCursor(Words.prevBoundary(text, cursor()))
    }

    fun moveNextWord() {
        preferredX = Float.NaN
        setCursor(Words.nextBoundary(text, cursor()))
    }

    fun selectPrevWord() {
        preferredX = Float.NaN
        extendTo(Words.prevBoundary(text, focusIndex.coerceIn(0, text.length)))
    }

    fun selectNextWord() {
        preferredX = Float.NaN
        extendTo(Words.nextBoundary(text, focusIndex.coerceIn(0, text.length)))
    }

    /** 删除光标前一个词；有选区时退化为删除选区。 */
    fun deleteWordBackward() {
        if (localHasSel) return delSel()
        val pos = cursor()
        if (pos <= 0) return
        val start = Words.prevBoundary(text, pos)
        if (start >= pos) return
        undoRedo.push(DeleteTextAction(start, text.substring(start, pos), true))
        text = text.removeRange(start, pos)
        setCursor(start)
        preferredX = Float.NaN
    }

    /** 删除光标后一个词；有选区时退化为删除选区。 */
    fun deleteWordForward() {
        if (localHasSel) return delSel()
        val pos = cursor()
        if (pos >= text.length) return
        val end = Words.nextBoundary(text, pos)
        if (end <= pos) return
        undoRedo.push(DeleteTextAction(pos, text.substring(pos, end), false))
        text = text.removeRange(pos, end)
        setCursor(pos)
        preferredX = Float.NaN
    }

    /** PageUp/PageDown 一次跳动的行数。 */
    open val pageLines: Int get() = 12

    /** 垂直跳 [count] 行（负上正下），保持 [preferredX] 视觉列；无布局时回退文档首尾。 */
    private fun jumpLines(count: Int, extend: Boolean) {
        val fallback = if (count < 0) 0 else text.length
        val r = cursorRect().firstOrNull()
        val newPos = when {
            r == null -> fallback
            else -> {
                val p = paragraph!!
                if (preferredX.isNaN()) preferredX = r.left + r.width / 2f
                val step = r.bottom - r.top
                val newPos = p.getGlyphPositionAtCoordinate(preferredX, r.top + step * count)
                displayToLogicIndex(newPos)
            }
        }
        moveTo(newPos, extend)
    }

    fun movePageUp() = jumpLines(-pageLines, extend = false)
    fun movePageDown() = jumpLines(pageLines, extend = false)
    fun selectPageUp() = jumpLines(-pageLines, extend = true)
    fun selectPageDown() = jumpLines(pageLines, extend = true)

    private fun cursorRect(): List<TextRect> {
        val p = paragraph ?: return emptyList()
        val pos = logicToDisplayIndex(cursor())
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
        setCursor(displayToLogicIndex(newPos))
    }

    fun moveDown() {
        val p = paragraph ?: return
        val rects = cursorRect()
        if (rects.isEmpty()) return setCursor(text.length)
        val r = rects[0]
        if (preferredX.isNaN()) preferredX = r.left + r.width / 2f
        val step = r.bottom - r.top
        val newPos = p.getGlyphPositionAtCoordinate(preferredX, r.bottom + step)
        setCursor(displayToLogicIndex(newPos))
    }

    fun selectUp() {
        val p = paragraph ?: return
        val rects = cursorRect()
        if (rects.isEmpty()) return
        val r = rects[0]
        if (preferredX.isNaN()) preferredX = r.left + r.width / 2f
        val a = selAnchor
        val step = r.bottom - r.top
        val newPos = p.getGlyphPositionAtCoordinate(preferredX, r.top - step)
        caretPair = a to displayToLogicIndex(newPos)
    }

    fun selectDown() {
        val p = paragraph ?: return
        val rects = cursorRect()
        if (rects.isEmpty()) return
        val r = rects[0]
        if (preferredX.isNaN()) preferredX = r.left + r.width / 2f
        val a = selAnchor
        val step = r.bottom - r.top
        val newPos = p.getGlyphPositionAtCoordinate(preferredX, r.bottom + step)
        caretPair = a to displayToLogicIndex(newPos)
    }

    private fun applyState(state: TextState) {
        text = state.text
        setCursor(state.cursor)
    }

    override fun handleKey(e: KeyEvent): Boolean {
        absorbGlobalSelection()
        // 快捷键匹配对大小写归一：CapsLock 开启或 Shift 参与时平台上报大写（'Z'/'Y'）
        val key = e.key.lowercaseChar()
        when {
            e.ctrl && !e.shift && key == 'z' -> { undo(); return true }
            (e.ctrl && key == 'y') || (e.ctrl && e.shift && key == 'z') -> { redo(); return true }

            e.code == KeyCode.Backspace -> {
                if (e.ctrl || e.alt) deleteWordBackward() else backspace()
                preferredX = Float.NaN
                return true
            }
            e.code == KeyCode.Delete -> {
                if (e.ctrl || e.alt) deleteWordForward() else delete()
                preferredX = Float.NaN
                return true
            }
            e.code == KeyCode.Left -> {
                when {
                    e.ctrl && e.shift -> selectPrevWord()
                    e.ctrl -> movePrevWord()
                    e.shift -> selectLeft()
                    else -> moveLeft()
                }
                preferredX = Float.NaN
                return true
            }
            e.code == KeyCode.Right -> {
                when {
                    e.ctrl && e.shift -> selectNextWord()
                    e.ctrl -> moveNextWord()
                    e.shift -> selectRight()
                    else -> moveRight()
                }
                preferredX = Float.NaN
                return true
            }
            e.code == KeyCode.Home -> {
                when {
                    e.ctrl && e.shift -> selectDocStart()
                    e.ctrl -> moveDocStart()
                    e.shift -> selectHome()
                    else -> moveHome()
                }
                return true
            }
            e.code == KeyCode.End -> {
                when {
                    e.ctrl && e.shift -> selectDocEnd()
                    e.ctrl -> moveDocEnd()
                    e.shift -> selectEnd()
                    else -> moveEnd()
                }
                return true
            }
            e.code == KeyCode.PageUp -> { if (e.shift) selectPageUp() else movePageUp(); return true }
            e.code == KeyCode.PageDown -> { if (e.shift) selectPageDown() else movePageDown(); return true }
            e.code == KeyCode.Up -> { if (e.shift) selectUp() else moveUp(); return true }
            e.code == KeyCode.Down -> { if (e.shift) selectDown() else moveDown(); return true }
            e.code == KeyCode.Enter -> { if (!singleLine) insertText("\n"); preferredX = Float.NaN; return true }
            e.code == KeyCode.Tab -> { if (!singleLine) { insertText("\t"); return true }; return false }
            e.ctrl || e.alt -> return false
            e.key.code < 0x20 || e.key.code == 0x7F -> return false
            else -> {
                preferredX = Float.NaN
                endComposition(restore = false) // 裸字符键放行时放弃进行中的组合
                insertText(e.key.toString())
                return true
            }
        }
    }

    override fun copy() {
        absorbGlobalSelection()
        if (!localHasSel) return
        clipboardSetText(text.substring(localSelStart, localSelEnd))
    }

    override fun cut() {
        absorbGlobalSelection()
        if (!localHasSel) return
        clipboardSetText(text.substring(localSelStart, localSelEnd))
        delSel()
    }

    override fun paste() {
        val t = clipboardGetText() ?: return
        if (t.isEmpty()) return
        preferredX = Float.NaN
        endComposition(restore = false)
        insertText(t)
    }

    /**
     * 平台上报组合文本（完整预编辑串；空串 = 本次组合结束）。
     *
     * 预编辑以"窗口替换"写入逻辑文本：
     * - 首帧在基座（当前光标 / 本地选区）处插入，被其替换的原文记入 [compositionBase]；
     * - 后续帧整体替换上一帧预编辑窗口，不追加、不残留；
     * - 空串上报（提交完成）仅移除窗口——已确认文本由平台经常规插入路径另行写入。
     */
    override fun onComposing(text: String, cursorPosition: Int) {
        if (text.isEmpty()) {
            endComposition(restore = false)
            return
        }
        absorbGlobalSelection()
        val cur = this.text
        val (start, winLen) = compositionBase?.let { it.first to composingText.length }
            ?: run {
                val (s, e) = if (localHasSel) localSelStart to localSelEnd else {
                    val c = cursor().coerceIn(0, cur.length)
                    c to c
                }
                compositionBase = s to cur.substring(s, e)
                s to (e - s)
            }
        val headEnd = start.coerceAtMost(cur.length)
        val tailStart = (start + winLen).coerceAtMost(cur.length)
        this.text = cur.substring(0, headEnd) + text + cur.substring(tailStart)
        setCursor(start + text.length)
        composingText = text
        composingCursorPos = cursorPosition.coerceIn(0, text.length)
    }

    /**
     * 结束组合态。[restore] 为 true 时还原被预编辑窗口替换的原文（取消语义），
     * 否则仅移除窗口（正常提交）。非组合态下为无害空操作。
     */
    fun endComposition(restore: Boolean) {
        val base = compositionBase
        val winLen = composingLength
        compositionBase = null
        composingText = ""
        composingCursorPos = 0
        preferredX = Float.NaN
        if (base == null && winLen == 0) return

        val start = base?.first ?: cursor().coerceIn(0, text.length)
        val restored = if (restore) (base?.second ?: "") else ""
        val cur = text
        if (start <= cur.length) {
            val newText = cur.substring(0, start) + restored +
                cur.substring((start + winLen).coerceAtMost(cur.length))
            if (newText != cur) this.text = newText
        }
        // 还原/移除窗口后，光标一律回到基座插入点
        setCursor(start)
    }

    fun selectLeft() {
        val a = selAnchor
        val f = focusIndex.coerceIn(0, text.length)
        if (f > 0) {
            caretPair = a to Graphemes.prevBoundary(text, f)
        }
    }

    fun selectRight() {
        val a = selAnchor
        val f = focusIndex.coerceIn(0, text.length)
        if (f < text.length) {
            caretPair = a to Graphemes.nextBoundary(text, f)
        }
    }

    /** 编辑器内全选：整体写入本地光标信号，全局派生自动跟随。 */
    fun selectAll() {
        caretPair = 0 to text.length
    }

    // 以下查询面向逻辑文本（占位 / 掩码下与段落显示文本不同）

    override val textLength: Int get() = text.length

    override val selectedText: String
        get() {
            val r = assignedRange ?: return ""
            val s = r.first.coerceIn(0, text.length)
            val e = r.second.coerceIn(0, text.length)
            return if (e > s) text.substring(s, e) else ""
        }

    override fun textInRange(start: Int, end: Int): String =
        if (end > start) text.substring(start, end.coerceAtMost(text.length)) else ""

    /** 光标定位到任意偏移（编程式）：写本地光标信号，塌缩即接管全局派生链。 */
    fun moveCursorTo(offset: Int) {
        setCursor(offset)
    }

    /** 编辑器内设置任意选区区间 [start, end)（编程式）：写本地光标信号，两端相等即光标定位。 */
    fun selectRange(start: Int, end: Int) {
        caretPair = start.coerceIn(0, text.length) to end.coerceIn(0, text.length)
    }

    override fun onPointerDownCapture(e: PointerEvent) {
        super.onPointerDownCapture(e)
        preferredX = Float.NaN
        // 若当前已存在给本编辑器分配的非塌缩选区，说明是"双击/三击等路径先由引擎
        // 调用 select() 写入了本地选区"，此时再 setCursor(collapsed) 会覆盖掉选词/
        // 选段结果，因此直接跳过（光标位置由 SelectionManager 的焦点端决定）。
        val existing = assignedRange
        if (existing != null && existing.second > existing.first) return
        // 点击定位光标，并塌缩全局会话清除其他节点选区
        // （拖拽中的选区由 SelectionManager 按指针信号接管，与本地光标互不干扰）
        val p = paragraph ?: return
        val pos = displayToLogicIndex(
            p.getGlyphPositionAtCoordinate(
                e.x - paddingInlineStart,
                e.y - paddingBlockStart
            )
        )
        setCursor(pos)
    }
    private fun overlayOrigin(): Pair<Float, Float> {
        val pos = logicToDisplayIndex(cursor())
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

    override fun draw(canvas: PlatformCanvas) {
        super.draw(canvas)

        if (!hasSel && cursorVisible && isFocused) {
            drawCursor(canvas, cursor())
        }

        if (composingText.isNotEmpty()) {
            drawComposing(canvas)
        }
    }

    private fun drawCursor(canvas: PlatformCanvas, logicPos: Int) {
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
        val pos = logicToDisplayIndex(logicPos)
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
        val s = logicToDisplayIndex(composingStart)
        val e = logicToDisplayIndex(composingStart + composingLength)
        for (rect in p.getRectsForRange(s, e, RectStyle.TIGHT)) {
            canvas.fillRect(rect.left + px, rect.top + py, rect.width, rect.height, composingBackgroundColor)
            canvas.fillRect(rect.left + px, rect.bottom - 2f + py, rect.width, 2f, composingUnderlineColor)
        }
    }
}
