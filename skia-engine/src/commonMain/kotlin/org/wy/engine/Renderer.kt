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

private class Register(context: StateHolder<Node, List<Node>>?) {
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

    fun provide(context: StateHolder<Node, List<Node>>) {
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

private val nodeConfig = object : ShareConfig<Node, List<Node>> {
    fun ignore(node: Node): Boolean {
        return node.hide
    }

    override fun after(list: List<Node>) {
        collectIndex(list)
    }

    override fun purifyList(nodes: List<ValueOrGetList<Node>>): List<Node> {
        val newList = mutableListOf<Node>()
        purifyList(nodes, newList, ::ignore)
        return newList
    }
}

open class Renderer private constructor(
    context: StateHolder<Node, List<Node>>?,
    private val register: Register
) : LayoutNode(context) {
    constructor(context: StateHolder<Node, List<Node>>?) : this(context, Register(context)) {
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
            hitest(x, y)?.let {
                mouseEventOf(it, MouseEventEnum.click)
            }
        } catch (e: Throwable) {
            println("全局mouseClick事件出错--$e")
        }
    }

    fun mouseDown(x: Float, y: Float) {
        try {
            register.pressed = true
            hitest(x, y)?.let {
                setFocused(it.last.node)
                mouseEventOf(it, MouseEventEnum.down)
            } ?: run {
                setFocused(null)
            }
            register.dispatchMouseDown(x, y)
        } catch (e: Throwable) {
            println("全局mouseDown事件出错--$e")
        }
    }

    private fun setFocused(node: Node?) {
        val old = register.focused
        if (old === node) return
        register.focused = node
    }

    /**
     * 收集整棵节点树中可聚焦的节点；有 `focusOrder` 的按值升序排在前面，
     * 其余按文档顺序排在后面。
     */
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

    /**
     * Tab 移动到下一个可聚焦节点，Shift+Tab 移动到上一个；循环遍历。
     */
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
            register.pressed = false
            hitest(x, y)?.let {
                mouseEventOf(it, MouseEventEnum.up)
            }
            register.dispatchMouseUp(x, y)
        } catch (e: Throwable) {
            println("全局mouseUp事件出错--$e")
        }
    }

    fun mouseMove(x: Float, y: Float) {
        try {
            val nodeWithPosition = hitest(x, y)
            register.moveHitest = nodeWithPosition
            register.requestCursor(cursorOf(nodeWithPosition))
            if (nodeWithPosition != null) {
                mouseEventOf(nodeWithPosition, MouseEventEnum.move)
            }
            register.dispatchMouseMove(x, y)
        } catch (e: Throwable) {
            println("全局mouseMove事件出错--$e")
        }
    }

    fun mouseExit() {
        register.pressed = false
        register.moveHitest = null
        register.requestCursor(CursorType.DEFAULT)
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
            if (!alt && !meta && !ctrl && code == KeyCode.Tab) {
                moveFocus(!shift)
                return
            }
            val e = KeyEvent(key, code, ctrl, shift, alt, meta)
            register.dispatchKeyPress(e)
        } catch (e: Throwable) {
            println("键盘事件出错--$e")
        }
    }

    fun composingText(text: String, cursorPosition: Int) {
        try {
            register.dispatchComposingText(text, cursorPosition)
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

private enum class MouseEventEnum {
    click, down, up, move
}

private fun mouseEventOf(nodeWithPosition: NodeWithPosition, type: MouseEventEnum) {
    val root = nodeWithPosition
    var nodeWithPosition: NodeWithPosition? = nodeWithPosition
    val list = mutableListOf<NodeWithPosition>()
    //这里检查时，如果直接事件发生，造成状态改变，再向上查询时会出错。
    while (nodeWithPosition != null) {
        //捕获
        val e = MouseEvent(nodeWithPosition.x, nodeWithPosition.y, root.x, root.y)
        sendMouseEvent(nodeWithPosition.node, type, e, true)
        if (e.stoppedProgression) {
            return
        }
        list.add(nodeWithPosition)
        nodeWithPosition = nodeWithPosition.next
    }
    list.asReversed().forEach {
        //冒泡
        val e = MouseEvent(it.x, it.y, root.x, root.y)
        sendMouseEvent(it.node, type, e, false)
        if (e.stoppedProgression) {
            return
        }
    }
}

private fun sendMouseEvent(node: Node, type: MouseEventEnum, e: MouseEvent, capture: Boolean) {
    when (type) {
        MouseEventEnum.click -> if (capture) node.mouseClickCapture(e) else node.mouseClick(e)
        MouseEventEnum.down -> if (capture) node.mouseDownCapture(e) else node.mouseDown(e)
        MouseEventEnum.up -> if (capture) node.mouseUpCapture(e) else node.mouseUp(e)
        MouseEventEnum.move -> if (capture) node.mouseMoveCapture(e) else node.mouseMove(e)
    }
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