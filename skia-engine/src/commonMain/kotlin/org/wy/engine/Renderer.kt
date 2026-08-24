package org.wy.engine

import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import com.wy.mve.renderRoot
import org.wy.engine.helper.toastContainer
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.lib.EmptyFun
import org.wy.lib.getValue
import org.wy.signal.TrackSignal
import org.wy.signal.memo

interface ComposingTextHandler {
    fun onComposing(text: String, cursorPosition: Int)
}

open class Renderer private constructor(
    context: StateHolder<*, *>?,
    private val register: Register
) : LayoutNode(context, register) {
    constructor(context: StateHolder<*, *>?) : this(context, Register(context))

    init {
        // 自身即渲染树根：注入给 EngineGlobal，供选区等全树遍历派生使用
        register.rootNode = this
    }

    override fun createGetChildren(): () -> List<Node> {
        if (context == null) {
            val state = renderRoot(this@Renderer, nodeConfig) {
                register.provide(this)
                argChildren()
                renderForEach({ callback ->
                    register.popList.forEach {
                        callback(it, it)
                    }
                }) { pop, e ->
                    pop.render(this)
                }
                toastContainer {
                    object : RectNode(this), FlexParam {
                        override val layout: LayoutDirection = FlexObject(this)
                        override val gap: Float
                            get() = 10f

                        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                            renderForEach({ callback ->
                                register.toastList.forEach {
                                    callback(it, it)
                                }
                            }) { toast, _ ->
                                toast.render(this)
                            }
                        }
                    }
                }
            }
            destroyList.add(state::destroy)
            return state.target
        }
        return super.createGetChildren()
    }

    private val cursorTrack = object : TrackSignal<CursorType>() {
        override fun get(old: CursorType?, inited: Boolean): CursorType {
            // 指针落在空白区域时 hitTest 返回 null，链为空是正常状态，直接回退默认光标
            val chain = register.moveHitTest?.chain
            if (chain.isNullOrEmpty()) return CursorType.DEFAULT
            // 命中链 root→leaf，leaf 可能是内部文字等"纯展示"节点（cursorAt=DEFAULT）；
            // 从深到浅取第一个声明了非默认光标的节点，保证悬停在控件内容上也能正确反馈
            for (i in chain.indices.reversed()) {
                val n = chain[i]
                val c = n.node.cursorAt(n.x, n.y)
                if (c != CursorType.DEFAULT) return c
            }
            return CursorType.DEFAULT
        }

        override fun set(v: CursorType, oldV: CursorType?, inited: Boolean): EmptyFun? {
            setCursor(v)
            return null
        }
    }

    private val overlayTrack = object : TrackSignal<InputOverlayData?>() {
        override fun get(old: InputOverlayData?, inited: Boolean): InputOverlayData? {
            return register.activeEditor?.inputOverlay()
        }

        override fun set(
            v: InputOverlayData?,
            oldV: InputOverlayData?,
            inited: Boolean
        ): EmptyFun? {
            if (v != null) setInputOverlay(v) else hideInputOverlay()
            return null
        }
    }

    open fun setCursor(v: CursorType) {}
    open fun setInputOverlay(data: InputOverlayData) {}
    open fun hideInputOverlay() {}

    override val destroyed: Boolean
        get() = overlayTrack.disabled

    private val destroyList = mutableListOf<EmptyFun>()
    override fun addDestroy(callback: EmptyFun) {
        destroyList.add(callback)
    }

    fun destroy() {
        destroyList.forEach(::run)
        register.destroy();
        cursorTrack.dispose()
        overlayTrack.dispose()
    }

    open fun frameCallback() {}
    var scheduled = false
    private val signal = object : TrackSignal<Unit>() {
        override fun get(old: Unit?, inited: Boolean) {
            frameCallback()
        }
    }
    val didDraw = memo {
        recordPicture(outerWidth, outerHeight) { draw(it) }
    }

    fun render(canvas: PlatformCanvas) {
        scheduled = true
        try {
            canvas.clear(rgba(255, 255, 255))
            signal.collect {
                didDraw().draw(canvas, 0f, 0f)
            }
            register.drainPostRenderEffects()
        } catch (err: Throwable) {
            engineLogError("render error", err)
        }
        scheduled = false
    }

    private fun setFocused(node: Node?) {
        val old = register.focused
        if (old === node) return
        register.focused = node
    }

    private fun collectFocusable(root: Node): List<Node> {
        val result = mutableListOf<Node>()
        fun collect(node: Node) {
            if (node.focusable && !node.hide) result.add(node)
            node.children.forEach(::collect)
        }
        collect(root)
        if (result.any { it.focusOrder != null }) result.sortBy { it.focusOrder ?: Int.MAX_VALUE }
        return result
    }

    private val focusableNodes by memo { collectFocusable(this) }

    /** 从 [focused] 沿父链上溯，返回最近的 [Node.focusTrap] 节点（弹出层圈定范围）。 */
    private fun findFocusTrap(focused: Node?): Node? {
        var cur = focused
        while (cur != null) {
            if (cur.focusTrap) return cur
            cur = cur.parent
        }
        return null
    }

    private fun moveFocus(next: Boolean) {
        // 焦点在当前弹出层（focusTrap）内时，只在圈定子树内遍历；否则全局遍历
        val trap = findFocusTrap(register.focused)
        val nodes = if (trap != null) collectFocusable(trap) else focusableNodes
        if (nodes.isEmpty()) return
        val current = register.focused
        val index = nodes.indexOfFirst { it === current }
        val targetIndex = when {
            index < 0 -> if (next) 0 else nodes.size - 1
            next -> (index + 1) % nodes.size
            else -> (index - 1 + nodes.size) % nodes.size
        }
        setFocused(nodes[targetIndex])
    }

    /**
     * 连击跟踪：记录"上一次按下"快照（目标 / 位置 / 时刻），判定同目标、时间窗内、
     * 近距再次按下即递增计数（单击 → 双击 → 三击）。
     *
     * 不能用 [EngineGlobal.pressed] 替代：pressed 是"当下按住"的实时状态
     * （Up 即清除），而连击判定需要离散的历史按下记录。
     */
    private class ClickTracker {
        var node: Selectable? = null
        var x = 0f
        var y = 0f
        var time = 0L
        var count = 0

        /** 记录一次按下，返回连击数。 */
        fun recordDown(target: Node?, x: Float, y: Float, now: Long): Int {
            val repeated = target != null &&
                target === node &&
                now - time < 400L &&
                kotlin.math.abs(x - this.x) < 5f &&
                kotlin.math.abs(y - this.y) < 5f
            count = if (repeated) count + 1 else 1
            node = target as? Selectable
            this.x = x; this.y = y; time = now
            return count
        }
    }

    private val clicks = ClickTracker()

    /** 双击后的按住拖拽会话：以双击词为锚，移动端点按词粒度扩展。
     *  会话存活本身即蕴含"指针按住中"——它在 mouseDown 时建立、mouseUp / mouseExit
     *  时销毁，无需独立的按压布尔来重复跟踪该生命周期。 */
    private var wordDragSession: WordDragSession? = null

    private class WordDragSession(
        val sel: Selectable,
        val anchorStart: Int,
        val anchorEnd: Int
    )

    fun mouseDown(x: Float, y: Float, device: PointerDevice = PointerDevice.Mouse) {
        try {
            val hit = hitTestResult(x, y, device)
            // 让 moveHitest 始终反映最近的指针位置（含按下瞬间）
            register.moveHitTest = hit
            register.pointerDownHit = hit
            val now = System.currentTimeMillis()
            val leaf = hit.chain.lastOrNull()?.node
            // 连击判定：同一可选节点、400ms 内、位移极小（双击 → 三击递增，否则重置单击）
            val clickCount = clicks.recordDown(leaf, x, y, now)
            wordDragSession = null

            // 平台惯例：按下落在活跃编辑器之外时，其本地显式选区立即塌缩让位——
            // 否则旧的非塌缩选区会在选区派生（#2 优先级）中遮蔽随后的拖选 / 选词结果。
            val ed = register.activeEditor
            if (ed != null && hit.chain.none { it.node === ed }) {
                ed.collapseExternalSelection()
            }

            if (clickCount >= 3) {
                // 三击选段：一次性物化整个逻辑段落（'\n' 分隔），不开拖拽会话。
                // 仅对在册可选节点生效（活性 + selectionEnabled 子树声明），
                // 否则按普通点击处理（onPointerClick 兜底）。
                val sel = leaf as? Selectable
                if (sel != null && register.selectionManager.isSelectable(sel)) {
                    setFocused(leaf)
                    val off = sel.positionForPoint(hit.x, hit.y)
                    val para = sel.paragraphRangeAt(off) ?: (0 to sel.textLength)
                    if (para.second > para.first) {
                        register.selectionManager.select(sel, para.first, sel, para.second)
                    }
                }
                dispatchPointer(hit, PointerType.Down, x, y, device = device)
                return
            }

            if (clickCount == 2) {
                // 双击选词：一次性物化为编程式选区（编辑器聚焦时自动分流为其内部选区）。
                // 不开启常规拖拽会话——holding 会话会压制程序化选区；词拖扩展由
                // [wordDragSession] 在 mouseMove 中单独驱动，松手即结束。
                val sel = leaf as? Selectable
                if (sel != null && register.selectionManager.isSelectable(sel)) {
                    setFocused(leaf)
                    val off = sel.positionForPoint(hit.x, hit.y)
                    val word = sel.wordRangeAt(off)
                    if (word != null && word.second > word.first) {
                        register.selectionManager.select(sel, word.first, sel, word.second)
                        wordDragSession = WordDragSession(sel, word.first, word.second)
                    } else {
                        // 无分词能力时退化为选中整个节点文本
                        register.selectionManager.select(sel, 0, sel, sel.textLength)
                    }
                }
                dispatchPointer(hit, PointerType.Down, x, y, device = device)
                return
            }

            // 开启选择会话；Shift+按下时复用上一会话的 press（锚点继承，焦点重新跟随移动）
            val prev = register.pointerSelect
            register.pointerSelect = if (register.shift && prev != null) {
                PointerSelect(prev.press, null)
            } else {
                PointerSelect(hit, null)
            }
            setFocused(hit.chain.lastOrNull()?.node)
            dispatchPointer(hit, PointerType.Down, x, y, device = device)
        } catch (e: Throwable) {
            engineLogError("mouseDown error", e)
        }
    }

    fun mouseUp(x: Float, y: Float, device: PointerDevice = PointerDevice.Mouse) {
        try {
            // 松手：词拖扩展会话结束（选区保持最后一次扩展结果）
            wordDragSession = null
            // 按压态先快照并立即清除（Up 之后按住结束），供下方 click 判定使用
            val down = register.pointerDownHit
            register.pointerDownHit = null
            val hit = hitTestResult(x, y, device)
            register.moveHitTest = hit
            // 松手定格：填入 release 后 hover 不再影响选区（纯数据变化，无命令）
            register.pointerSelect = register.pointerSelect?.copy(release = hit)
            // 指针捕获：up 先投递给捕获者并结束捕获，随后仍走正常树分发（非按下态，通常无副作用）
            val captured = register.captured(0)
            if (captured != null) {
                val e = PointerEvent(
                    type = PointerType.Up,
                    x = x,
                    y = y,
                    rootX = x,
                    rootY = y,
                    device = device
                )
                captured.onUp(e)
                captured.release()
            }
            dispatchPointer(hit, PointerType.Up, x, y, device = device)
            if (down != null) {
                val downPos = down.chain.firstOrNull()
                if (downPos != null) {
                    val dx = x - downPos.x
                    val dy = y - downPos.y
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    val dt = System.currentTimeMillis() - down.time
                    if (dist < 5f && dt < 500L) {
                        dispatchPointer(hit, PointerType.Click, x, y, device = device)
                    }
                }
            }
            // 注意：修饰键反映真实键盘状态，松开鼠标不清空
        } catch (e: Throwable) {
            engineLogError("mouseUp error", e)
        }
    }

    fun mouseMove(x: Float, y: Float, device: PointerDevice = PointerDevice.Mouse) {
        try {
            // 指针捕获：move 只投递给捕获者，不进入树分发
            val captured = register.captured(0)
            if (captured != null) {
                val e = PointerEvent(
                    type = PointerType.Move,
                    x = x,
                    y = y,
                    rootX = x,
                    rootY = y,
                    device = device
                )
                captured.onMove(e)
                // 捕获期间仍刷新命中链快照：跨节点拖拽选择靠它定位指针下的目标文本节点
                register.moveHitTest = hitTestResult(x, y, device)
                return
            }
            val hit = hitTestResult(x, y, device)
            register.moveHitTest = hit
            // 双击后按住拖动：以锚词为基准按词粒度扩展选区（仅同节点内生效）
            val drag = wordDragSession
            if (drag != null && hit.chain.any { it.node === drag.sel }) {
                val off = drag.sel.positionForPoint(x, y)
                val (a, b) = expandWordSelection(drag.anchorStart, drag.anchorEnd, off) {
                    drag.sel.wordRangeAt(it)
                }
                if (b > a) {
                    register.selectionManager.select(drag.sel, a, drag.sel, b)
                }
            }
            dispatchPointer(hit, PointerType.Move, x, y, device = device)
        } catch (e: Throwable) {
            engineLogError("mouseMove error", e)
        }
    }

    fun mouseExit() {
        // 按住拖出窗口：把当前进度定格（选区保持连续），再清理 hover 位置
        val s = register.pointerSelect
        if (s != null && s.release == null) {
            register.pointerSelect = s.copy(release = register.moveHitTest ?: s.press)
        }
        // 词拖扩展同理结束（已物化的选区原样保留）
        wordDragSession = null
        // 按住拖出窗口：按压态与会话一同定格（与旧派生语义保持一致）
        register.pointerDownHit = null
        register.moveHitTest = null
    }

    fun mouseWheel(x: Float, y: Float, delta: Float) {
        try {
            val hit = hitTestResult(x, y)
            dispatchPointer(hit, PointerType.Wheel, x, y, wheelDelta = delta)
        } catch (e: Throwable) {
            engineLogError("mouseWheel error", e)
        }
    }

    fun keyPress(
        key: Char,
        code: KeyCode,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
        meta: Boolean = false
    ) {
        try {
            val e = KeyEvent(key, code, ctrl, shift, alt, meta)

            // 检查是否是全局选择快捷键（Cmd+A/C/X/V）；
            // 大小写归一：CapsLock 开启时平台上报大写字符
            if (ctrl && !shift && !alt && !meta) {
                when (key.lowercaseChar()) {
                    'a' -> {
                        register.selectionManager.selectAll()
                        return
                    }

                    'c' -> {
                        val text = register.selectionManager.selectedText
                        if (text != null && text.isNotEmpty()) {
                            clipboardSetText(text)
                            return
                        }
                    }

                    'x' -> {
                        val editor = register.activeEditor
                        if (editor != null && editor.hasSelection) {
                            editor.cut()
                            return
                        }
                    }

                    'v' -> {
                        val editor = register.activeEditor
                        if (editor != null) {
                            editor.paste()
                            return
                        }
                    }
                }
            }

            // 焦点可能指向已销毁节点（原始事实不清理），派发前校验活性
            val handled = register.focused?.takeIf { !it.destroyed }?.handleKey(e) ?: false
            if (!handled) {
                if (!alt && !meta && !ctrl && code == KeyCode.Tab) {
                    moveFocus(!shift)
                    return
                }
                register.dispatchKeyPress(e)
            }
        } catch (e: Throwable) {
            engineLogError("keyboard error", e)
        }
    }

    fun composingText(text: String, cursorPosition: Int) {
        try {
            val focusedNode = register.focused
            if (focusedNode is ComposingTextHandler) {
                focusedNode.onComposing(text, cursorPosition)
            } else {
                register.dispatchComposingText(text, cursorPosition)
            }
        } catch (e: Throwable) {
            engineLogError("IME error", e)
        }
    }

    /**
     * 上报当前修饰键状态（键盘按下 / 释放事件都要调用）。
     * 修饰键是纯键盘状态，与按键分发（[keyPress]）解耦：平台每次键盘事件都上报
     * 权威的修饰键快照（AWT `isXxxDown`），引擎据此维护四个信号。
     */
    fun updateModifiers(ctrl: Boolean, shift: Boolean, alt: Boolean, meta: Boolean) {
        try {
            register.applyModifiers(ctrl, shift, alt, meta)
        } catch (e: Throwable) {
            engineLogError("keyboard error", e)
        }
    }

    /** 清空修饰键状态（窗口失焦等场景调用，防止修饰键残留）。 */
    fun clearModifiers() {
        register.clearModifiers()
    }
}

private fun dispatchPointer(
    result: HitestResult,
    type: PointerType,
    rootX: Float,
    rootY: Float,
    wheelDelta: Float = 0f,
    device: PointerDevice = PointerDevice.Mouse
) {
    result.chain.forEach {
        //捕获
        val e = PointerEvent(
            type = type,
            device = device,
            x = it.x,
            y = it.y,
            rootX = rootX,
            rootY = rootY,
            wheelDelta = wheelDelta
        )
        sendPointer(it.node, type, e, true)
        if (e.stoppedProgression) {
            return
        }
    }
    result.chain.asReversed().forEach {
        //冒泡
        val e = PointerEvent(
            type = type,
            device = device,
            x = it.x,
            y = it.y,
            rootX = rootX,
            rootY = rootY,
            wheelDelta = wheelDelta
        )
        sendPointer(it.node, type, e, false)
        if (e.stoppedProgression) {
            return
        }
    }
}

private fun sendPointer(node: Node, type: PointerType, e: PointerEvent, capture: Boolean) {
    when (type) {
        PointerType.Click -> if (capture) node.onPointerClickCapture(e) else node.onPointerClick(e)
        PointerType.Down -> if (capture) node.onPointerDownCapture(e) else node.onPointerDown(e)
        PointerType.Up -> if (capture) node.onPointerUpCapture(e) else node.onPointerUp(e)
        PointerType.Move -> if (capture) node.onPointerMoveCapture(e) else node.onPointerMove(e)
        PointerType.Wheel -> if (capture) node.onPointerWheelCapture(e) else node.onPointerWheel(e)
        PointerType.Cancel -> if (capture) node.onPointerUpCapture(e) else node.onPointerUp(e)
    }
}

private fun Node.hitTestResult(x: Float, y: Float, device: PointerDevice = PointerDevice.Mouse): HitestResult {
    return HitestResult(
        hitTest(x, y).orEmpty(),
        System.currentTimeMillis(),
        x, y,
        device
    )
}