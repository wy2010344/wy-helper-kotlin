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
    val selectionManager = SelectionManager()
    val gestureArena = GestureArena()

    init {
        if (context != null) {
            provide(context)
        }
    }

    fun destroy() {
        moveList.clear()
        upList.clear()
        downList.clear()
        wheelList.clear()
        keyPressList.clear()
        composingList.clear()
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

    private var overlayShow: ((x: Float, y: Float, w: Float, h: Float, fontSize: Float) -> Unit)? =
        null
    private var overlayHide: (() -> Unit)? = null
    private var cursorHandler: ((CursorType) -> Unit)? = null
    private var lastCursor: CursorType? = null

    fun setOverlayHandler(
        show: (x: Float, y: Float, w: Float, h: Float, fontSize: Float) -> Unit,
        hide: () -> Unit
    ) {
        overlayShow = show
        overlayHide = hide
    }

    fun setCursorHandler(handler: (CursorType) -> Unit) {
        cursorHandler = handler
    }

    fun requestCursor(type: CursorType) {
        if (lastCursor == type) return
        lastCursor = type
        cursorHandler?.invoke(type)
    }

    fun provide(context: StateHolder<*, *>) {
        context.provide(engineGlobalContext, object : EngineGlobal {
            override fun registerMouseDown(callback: MouseCallback): EmptyFun {
                return register(downList, callback)
            }

            override fun registerMouseMove(callback: MouseCallback): EmptyFun {
                return register(moveList, callback)
            }

            override fun registerMouseUp(callback: MouseCallback): EmptyFun {
                return register(upList, callback)
            }

            override fun registerMouseWheel(callback: WheelCallback): EmptyFun {
                return register(wheelList, callback)
            }

            override fun registerKeyPress(callback: KeyPressCallback): EmptyFun {
                return register(keyPressList, callback)
            }

            override fun registerComposingText(callback: ComposingTextCallback): EmptyFun {
                return register(composingList, callback)
            }

            override val pressed: Boolean
                get() = this@Register.pressed

            override val moveHitest: NodeWithPosition?
                get() = this@Register.moveHitest

            override var focused: Node?
                get() = this@Register.focused
                set(value) {
                    this@Register.focused = value
                }

            override val selectionManager: SelectionManager
                get() = this@Register.selectionManager
            override val gestureArena: GestureArena
                get() = this@Register.gestureArena

            override fun requestInputOverlay(
                x: Float,
                y: Float,
                w: Float,
                h: Float,
                fontSize: Float
            ) {
                overlayShow?.invoke(x, y, w, h, fontSize)
            }

            override fun hideInputOverlay() {
                overlayHide?.invoke()
            }

            override fun requestCursor(type: CursorType) {
                this@Register.requestCursor(type)
            }
        })
    }

    fun dispatchMouseUp(x: Float, y: Float) {
        upList.forEach { it.key(GlobalMouseEvent(x, y, it.value)) }
    }

    fun dispatchMouseDown(x: Float, y: Float) {
        downList.forEach { it.key(GlobalMouseEvent(x, y, {})) }
    }

    fun dispatchMouseMove(x: Float, y: Float) {

        moveList.forEach { it.key(GlobalMouseEvent(x, y, it.value)) }
    }

    fun dispatchMouseWheel(x: Float, y: Float, delta: Float) {
        wheelList.forEach { it.key(GlobalWheelEvent(x, y, delta, it.value)) }
    }

    fun dispatchKeyPress(e: KeyEvent) {
        keyPressList.forEach { it.key(e) }
    }

    fun dispatchComposingText(text: String, cursorPosition: Int) {
        composingList.forEach { it.key(text, cursorPosition) }
    }
}
open class Renderer private constructor(
    context: StateHolder<*, *>?,
    private val register: Register
) : LayoutNode(context) {

    var keyboardModifiers: Modifiers = Modifiers.None
    var mouseButtons: Int = 0

    val selectionManager: SelectionManager get() = register.selectionManager
    val gestureArena: GestureArena get() = register.gestureArena

    var hitNode: NodeWithPosition? = null
    var globalMoveHitest: NodeWithPosition? = null

    constructor(context: StateHolder<*, *>?) : this(context, Register(context)) {
        if (context == null) {
            val state = renderRoot(this@Renderer, nodeConfig) {
                register.provide(this)
                argChildren()
            }
            this.getChildren = state.target
            this.destroyFun = state::destroy
        }
    }


    fun setInputOverlayHandler(
        show: (x: Float, y: Float, w: Float, h: Float, fontSize: Float) -> Unit,
        hide: () -> Unit
    ) {
        register.setOverlayHandler(show, hide)
    }

    fun setCursorHandler(handler: (CursorType) -> Unit) {
        register.setCursorHandler(handler)
    }

    fun destroy() {
        register.destroy()
        destroyFun()
    }

    open fun frameCallback() {}

    private var destroyFun = {}
    var scheduled = false
    private val signal = object : TrackSignal<Unit>() {
        override fun get(old: Unit?, inited: Boolean) {
            frameCallback()
        }
    }
    val didDraw = memo {
        recordPicture(outerWidth, outerHeight) {
            draw(it)
        }
    }

    fun render(canvas: PlatformCanvas) {
        scheduled = true
        try {
            canvas.clear(rgba(255, 255, 255))
            signal.collect {
                didDraw().draw(canvas, 0f, 0f)
            }
        } catch (err: Throwable) {
            println("渲染出错--$err")
        }
        scheduled = false
    }


    fun mouseClick(x: Float, y: Float) {
        try {
            val hit = hitTest(x, y)
            if (hit == null) return
        } catch (e: Throwable) {
            println("全局mouseClick事件出错--$e")
        }
    }

    fun mouseDown(x: Float, y: Float) {
        try {
            mouseButtons = 1
            register.pressed = true
            hitTest(x, y)?.let {
                setFocused(it.last.node)
                mouseEventOf(x, y, down = true)
            } ?: run {
                setFocused(null)
            }
            register.dispatchMouseDown(x, y)
            gestureArena.dispatchDown(GlobalMouseEvent(x, y) {})
        } catch (e: Throwable) {
            println("全局mouseDown事件出错--$e")
        }
    }

    private fun setFocused(node: Node?) {
        val old = register.focused
        if (old === node) return
        register.focused = node
    }

    private fun focusableNodes(): List<Node> {
        val result = mutableListOf<Node>()
        fun collect(node: Node) {
            if (node.focusable && !node.hide) {
                result.add(node)
            }
            node.children.forEach(::collect)
        }
        children.forEach(::collect)
        if (result.any { it.focusOrder != null }) {
            result.sortBy { it.focusOrder ?: Int.MAX_VALUE }
        }
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

    fun mouseUp(x: Float, y: Float) {
        try {
            mouseButtons = 0
            register.pressed = false
            hitTest(x, y)?.let {
                mouseEventOf(x, y, up = true)
            }
            register.dispatchMouseUp(x, y)
            selectionManager.handleMouseUp()
            gestureArena.dispatchUp(GlobalMouseEvent(x, y) {})
        } catch (e: Throwable) {
            println("全局mouseUp事件出错--$e")
        }
    }

    fun mouseMove(x: Float, y: Float) {
        try {
            val nodeWithPosition = hitTest(x, y)
            register.moveHitest = nodeWithPosition
            register.requestCursor(cursorOf(nodeWithPosition))
            if (nodeWithPosition != null) {
                mouseEventOf(x, y, move = true)
            }
            register.dispatchMouseMove(x, y)
            selectionManager.handleMouseMove(x, y)
            gestureArena.dispatchMove(GlobalMouseEvent(x, y) {})
        } catch (e: Throwable) {
            println("全局mouseMove事件出错--$e")
        }
    }

    fun mouseExit() {
        register.pressed = false
        register.moveHitest = null
        register.requestCursor(CursorType.DEFAULT)
    }

    private fun mouseEventOf(rootX: Float, rootY: Float, down: Boolean = false, up: Boolean = false, move: Boolean = false, wheel: Float? = null) {
        val hit = hitTest(rootX, rootY)
        if (hit == null) return
        hitNode = hit
        globalMoveHitest = hit

        val capturePath = mutableListOf<NodeWithPosition>()
        var cur: NodeWithPosition? = hit
        while (cur != null) {
            capturePath.add(cur)
            cur = cur.node.parent?.let { p ->
                NodeWithPosition(p, cur!!.x, cur!!.y, cur)
            }
        }

        val bubblePath = mutableListOf<NodeWithPosition>()
        cur = hit
        while (cur != null) {
            bubblePath.add(cur)
            cur = cur.node.parent?.let { p ->
                NodeWithPosition(p, cur!!.x, cur!!.y, cur)
            }
        }

        try {
            capturePath.asReversed().forEach { nodeWithPos ->
                val e = MouseEvent(nodeWithPos, this, down, up, move, wheel)
                (nodeWithPos.node as? MouseListener)?.mouseDownCapture(e)
                if (e.stoppedProgression) return@forEach
            }
            bubblePath.forEach { nodeWithPos ->
                val e = MouseEvent(nodeWithPos, this, down, up, move, wheel)
                when {
                    down -> (nodeWithPos.node as? MouseListener)?.mouseDown(e)
                    up -> (nodeWithPos.node as? MouseListener)?.mouseUp(e)
                    move -> (nodeWithPos.node as? MouseListener)?.mouseMove(e)
                }
            }
        } catch (err: Error) {
            println("事件处理出错--$err")
        }
    }

    fun mouseWheel(x: Float, y: Float, delta: Float) {
        try {
            register.dispatchMouseWheel(x, y, delta)
        } catch (e: Throwable) {
            println("全局mouseWheel事件出错--$e")
        }
    }

    fun keyPress(
        key: Char, code: KeyCode,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
        meta: Boolean = false
    ) {
        try {
            keyboardModifiers = Modifiers(ctrl, shift, alt, meta)
            if (!alt && !meta && !ctrl && code == KeyCode.Tab) {
                moveFocus(!shift)
                return
            }
            val e = KeyEvent(key, code, ctrl, shift, alt, meta)
            (focused as? KeyHandler)?.handleKey(e) ?: run {
                register.dispatchKeyPress(e)
            }
        } catch (e: Throwable) {
            println("键盘事件出错--$e")
        }
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
        } catch (e: Throwable) {
            println("输入法事件出错--$e")
        }
    }
}

private fun <K> register(map: MutableMap<K, EmptyFun>, key: K): EmptyFun {
    val destroy: EmptyFun = {
        map.remove(key)
    }
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