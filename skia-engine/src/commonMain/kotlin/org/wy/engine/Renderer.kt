package org.wy.engine

import com.wy.mve.ShareConfig
import com.wy.mve.StateHolder
import com.wy.mve.ValueOrGetList
import com.wy.mve.purifyList
import com.wy.mve.renderRoot
import org.wy.lib.EmptyFun
import org.wy.signal.TrackSignal
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.memo
import org.wy.signal.setValue

private class Register(context: StateHolder<*, *>?) {
    val gestureArena = GestureArena()
    val popoverManager = PopoverManager()
    val selectionManager = SelectionManager()

    init {
        if (context != null) provide(context)
    }

    fun destroy() {
        moveList.clear(); upList.clear(); downList.clear(); wheelList.clear()
        keyPressList.clear(); composingList.clear()
        gestureRecognizers.clear()
    }

    var pressed by createSignal(false)
    var moveHitest by createSignal<NodeWithPosition?>(null)
    var focused by createSignal<Node?>(null)

    private val moveList = mutableMapOf<MouseCallback, EmptyFun>()
    private val upList = mutableMapOf<MouseCallback, EmptyFun>()
    private val downList = mutableMapOf<MouseCallback, EmptyFun>()
    private val wheelList = mutableMapOf<WheelCallback, EmptyFun>()
    private val keyPressList = mutableMapOf<KeyPressCallback, EmptyFun>()
    private val composingList = mutableMapOf<ComposingTextCallback, EmptyFun>()

    private val gestureRecognizers = mutableListOf<GestureRecognizer>()

    fun registerGestureRecognizer(r: GestureRecognizer) {
        gestureRecognizers.add(r)
    }

    fun unregisterGestureRecognizer(r: GestureRecognizer) {
        gestureRecognizers.remove(r)
    }

    fun syncGestureRecognizers() {
        gestureRecognizers.forEach { gestureArena.add(it) }
    }

    private var overlayShow: ((x: Float, y: Float, w: Float, h: Float, fontSize: Float) -> Unit)? = null
    private var overlayHide: (() -> Unit)? = null
    private var cursorHandler: ((CursorType) -> Unit)? = null
    private var lastCursor: CursorType? = null

    fun setOverlayHandler(show: (x: Float, y: Float, w: Float, h: Float, fontSize: Float) -> Unit, hide: () -> Unit) {
        overlayShow = show; overlayHide = hide
    }

    fun setCursorHandler(handler: (CursorType) -> Unit) { cursorHandler = handler }

    fun requestCursor(type: CursorType) {
        if (lastCursor == type) return
        lastCursor = type; cursorHandler?.invoke(type)
    }

    fun provide(context: StateHolder<*, *>) {
        context.provide(gestureArenaContext, gestureArena)
        context.provide(popoverManagerContext, popoverManager)
        context.provide(selectionManagerContext, selectionManager)
        context.provide(engineGlobalContext, object : EngineGlobal {
            override fun registerMouseDown(callback: MouseCallback): EmptyFun = register(downList, callback)
            override fun registerMouseMove(callback: MouseCallback): EmptyFun = register(moveList, callback)
            override fun registerMouseUp(callback: MouseCallback): EmptyFun = register(upList, callback)
            override fun registerMouseWheel(callback: WheelCallback): EmptyFun = register(wheelList, callback)
            override fun registerKeyPress(callback: KeyPressCallback): EmptyFun = register(keyPressList, callback)
            override fun registerComposingText(callback: ComposingTextCallback): EmptyFun = register(composingList, callback)

            override fun registerGestureRecognizer(r: GestureRecognizer) = this@Register.registerGestureRecognizer(r)
            override fun unregisterGestureRecognizer(r: GestureRecognizer) = this@Register.unregisterGestureRecognizer(r)

            override val pressed: Boolean get() = this@Register.pressed
            override val moveHitest: NodeWithPosition? get() = this@Register.moveHitest
            override var focused: Node?
                get() = this@Register.focused
                set(value) { this@Register.focused = value }

            override val gestureArena: GestureArena get() = this@Register.gestureArena
            override val selectionManager: SelectionManager get() = this@Register.selectionManager

            override fun requestInputOverlay(x: Float, y: Float, w: Float, h: Float, fontSize: Float) {
                overlayShow?.invoke(x, y, w, h, fontSize)
            }
            override fun hideInputOverlay() { overlayHide?.invoke() }
            override fun requestCursor(type: CursorType) { this@Register.requestCursor(type) }
        })
    }

    fun dispatchMouseUp(x: Float, y: Float) { upList.forEach { it.key(GlobalMouseEvent(x, y, it.value)) } }
    fun dispatchMouseDown(x: Float, y: Float) { downList.forEach { it.key(GlobalMouseEvent(x, y, {})) } }
    fun dispatchMouseMove(x: Float, y: Float) { moveList.forEach { it.key(GlobalMouseEvent(x, y, it.value)) } }
    fun dispatchMouseWheel(x: Float, y: Float, delta: Float) { wheelList.forEach { it.key(GlobalWheelEvent(x, y, delta, it.value)) } }
    fun dispatchKeyPress(e: KeyEvent) { keyPressList.forEach { it.key(e) } }
    fun dispatchComposingText(text: String, cursorPosition: Int) { composingList.forEach { it.key(text, cursorPosition) } }
}

open class Renderer private constructor(
    context: StateHolder<*, *>?,
    private val register: Register
) : LayoutNode(context) {

    var keyboardModifiers: Modifiers = Modifiers.None
    var mouseButtons: Int = 0

    val gestureArena: GestureArena get() = register.gestureArena
    val popoverManager: PopoverManager get() = register.popoverManager
    val selectionManager: SelectionManager get() = register.selectionManager

    var hitNode: NodeWithPosition? = null
    var globalMoveHitest: NodeWithPosition? = null

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    var stateHolder: StateHolder<*, *>? = null
        private set

    constructor(context: StateHolder<*, *>?) : this(context, Register(context)) {
        if (context == null) {
            val state = renderRoot(this@Renderer, nodeConfig) {
                register.provide(this)
                argChildren()
            }
            this.stateHolder = state as StateHolder<*, *>
            this.getChildren = state.target
            this.destroyFun = state::destroy
        } else {
            this.stateHolder = context
        }
    }

    fun setInputOverlayHandler(show: (x: Float, y: Float, w: Float, h: Float, fontSize: Float) -> Unit, hide: () -> Unit) {
        register.setOverlayHandler(show, hide)
    }

    fun setCursorHandler(handler: (CursorType) -> Unit) { register.setCursorHandler(handler) }

    fun destroy() { register.destroy(); destroyFun() }

    open fun frameCallback() {}

    private var destroyFun = {}
    var scheduled = false
    private val signal = object : TrackSignal<Unit>() {
        override fun get(old: Unit?, inited: Boolean) { frameCallback() }
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
                renderOverlay(canvas)
            }
        } catch (err: Throwable) { println("render error--$err") }
        scheduled = false
    }

    protected open fun renderOverlay(canvas: PlatformCanvas) {}

    private fun setFocused(node: Node?) {
        val old = register.focused
        if (old === node) return
        register.focused = node
        // 同步选中状态到 SelectionManager
        val selectable = node as? Selectable
        if (selectable != null) {
            register.selectionManager.select(selectable)
        } else if (old is Selectable) {
            register.selectionManager.clear()
        }
    }

    private fun focusableNodes(): List<Node> {
        val result = mutableListOf<Node>()
        fun collect(node: Node) {
            if (node.focusable && !node.hide) result.add(node)
            node.children.forEach(::collect)
        }
        children.forEach(::collect)
        if (result.any { it.focusOrder != null }) result.sortBy { it.focusOrder ?: Int.MAX_VALUE }
        return result
    }

    private fun moveFocus(next: Boolean) {
        val nodes = focusableNodes()
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

    private fun buildEventPath(hit: NodeWithPosition): List<NodeWithPosition> {
        val path = mutableListOf<NodeWithPosition>()
        var cur: NodeWithPosition? = hit
        while (cur != null) {
            path.add(cur)
            cur = cur.next
        }
        return path
    }

    fun mouseDown(x: Float, y: Float) {
        try {
            mouseButtons = 1
            register.pressed = true
            downX = x; downY = y; downTime = System.currentTimeMillis()
            gestureArena.clear()
            register.syncGestureRecognizers()
            val hit = hitTest(x, y)
            hitNode = hit
            globalMoveHitest = hit
            if (hit != null) {
                setFocused(hit.last.node)
                dispatchMouseEvent(hit, x, y, down = true)
            } else {
                setFocused(null)
            }
            register.dispatchMouseDown(x, y)
            gestureArena.dispatchDown(GlobalMouseEvent(x, y) {})
        } catch (e: Throwable) { println("mouseDown error--$e") }
    }

    fun mouseUp(x: Float, y: Float) {
        try {
            mouseButtons = 0
            register.pressed = false
            val hit = hitTest(x, y)
            hitNode = hit
            globalMoveHitest = hit
            if (hit != null) {
                dispatchMouseEvent(hit, x, y, up = true)
                val dx = x - downX; val dy = y - downY
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                val dt = System.currentTimeMillis() - downTime
                if (dist < 5f && dt < 500L) {
                    dispatchClickEvent(hit, x, y)
                }
            }
            register.dispatchMouseUp(x, y)
            gestureArena.dispatchUp(GlobalMouseEvent(x, y) {})
        } catch (e: Throwable) { println("mouseUp error--$e") }
    }

    fun mouseMove(x: Float, y: Float) {
        try {
            val hit = hitTest(x, y)
            register.moveHitest = hit
            register.requestCursor(cursorOf(hit))
            if (hit != null) {
                dispatchMouseEvent(hit, x, y, move = true)
            }
            register.dispatchMouseMove(x, y)
            gestureArena.dispatchMove(GlobalMouseEvent(x, y) {})
        } catch (e: Throwable) { println("mouseMove error--$e") }
    }

    fun mouseExit() {
        register.pressed = false
        register.moveHitest = null
        register.requestCursor(CursorType.DEFAULT)
        gestureArena.dispatchExit()
    }

    private fun dispatchMouseEvent(hit: NodeWithPosition, rootX: Float, rootY: Float, down: Boolean = false, up: Boolean = false, move: Boolean = false, wheel: Float? = null) {
        val path = buildEventPath(hit)
        try {
            path.asReversed().forEach { nodeWithPos ->
                val e = MouseEvent(nodeWithPos, this, down, up, move, wheel, rootX, rootY)
                when {
                    down -> (nodeWithPos.node as? MouseListener)?.mouseDownCapture(e)
                    up -> (nodeWithPos.node as? MouseListener)?.mouseUpCapture(e)
                    move -> (nodeWithPos.node as? MouseListener)?.mouseMoveCapture(e)
                }
                if (e.stoppedProgression) return@forEach
            }
            path.forEach { nodeWithPos ->
                val e = MouseEvent(nodeWithPos, this, down, up, move, wheel, rootX, rootY)
                when {
                    down -> (nodeWithPos.node as? MouseListener)?.mouseDown(e)
                    up -> (nodeWithPos.node as? MouseListener)?.mouseUp(e)
                    move -> (nodeWithPos.node as? MouseListener)?.mouseMove(e)
                }
            }
        } catch (err: Error) { println("event error--$err") }
    }

    private fun dispatchClickEvent(hit: NodeWithPosition, rootX: Float, rootY: Float) {
        val path = buildEventPath(hit)
        try {
            path.asReversed().forEach { nodeWithPos ->
                val e = MouseEvent(nodeWithPos, this, down = false, up = false, move = false, wheel = null, rootX = rootX, rootY = rootY)
                (nodeWithPos.node as? MouseListener)?.mouseClickCapture(e)
                if (e.stoppedProgression) return
            }
            path.forEach { nodeWithPos ->
                val e = MouseEvent(nodeWithPos, this, down = false, up = false, move = false, wheel = null, rootX = rootX, rootY = rootY)
                (nodeWithPos.node as? MouseListener)?.mouseClick(e)
            }
        } catch (err: Error) { println("click error--$err") }
    }

    fun mouseWheel(x: Float, y: Float, delta: Float) {
        try {
            register.dispatchMouseWheel(x, y, delta)
            register.gestureArena.dispatchWheel(delta)
        } catch (e: Throwable) { println("mouseWheel error--$e") }
    }

    fun keyPress(key: Char, code: KeyCode, ctrl: Boolean, shift: Boolean, alt: Boolean, meta: Boolean = false) {
        try {
            keyboardModifiers = Modifiers(ctrl, shift, alt, meta)
            val e = KeyEvent(key, code, ctrl, shift, alt, meta)
            
            // 检查是否是全局选择快捷键（Cmd+A/C/X/V）
            if (ctrl && !shift && !alt && !meta) {
                when (key) {
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
                        val current = register.selectionManager.current as? EditableTextNode
                        if (current != null && current.hasSelection) {
                            current.cut()
                            return
                        }
                    }
                    'v' -> {
                        val current = register.selectionManager.current as? EditableTextNode
                        if (current != null) {
                            current.paste()
                            return
                        }
                    }
                }
            }
            
            val handled = (register.focused as? KeyHandler)?.handleKey(e) ?: false
            if (!handled) {
                if (!alt && !meta && !ctrl && code == KeyCode.Tab) {
                    moveFocus(!shift)
                    return
                }
                register.dispatchKeyPress(e)
            }
        } catch (e: Throwable) { println("keyboard error--$e") }
    }

    fun composingText(text: String, cursorPosition: Int) {
        try {
            val focusedNode = register.focused
            if (focusedNode is EditableTextNode) {
                focusedNode.composingText = text
                focusedNode.composingCursorPos = cursorPosition
            } else {
                register.dispatchComposingText(text, cursorPosition)
            }
        } catch (e: Throwable) { println("IME error--$e") }
    }
}

private fun <K> register(map: MutableMap<K, EmptyFun>, key: K): EmptyFun {
    val destroy: EmptyFun = { map.remove(key) }
    map[key] = destroy
    return destroy
}

private fun cursorOf(chain: NodeWithPosition?): CursorType {
    var pointer = false
    var n: NodeWithPosition? = chain
    while (n != null) {
        when {
            n.node is EditableTextNode -> return CursorType.TEXT
            n.node.focusable -> pointer = true
        }
        n = n.next
    }
    return if (pointer) CursorType.POINTER else CursorType.DEFAULT
}
