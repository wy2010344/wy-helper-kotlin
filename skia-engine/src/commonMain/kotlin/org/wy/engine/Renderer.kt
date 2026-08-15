package org.wy.engine

import com.wy.mve.StateHolder
import com.wy.mve.renderRoot
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

    private val cursorTrack = object : TrackSignal<CursorType>() {
        override fun get(old: CursorType?, inited: Boolean): CursorType {
            if (register.moveHitTest != null) {
                val last = register.moveHitTest!!.chain.last()
                return last.node.cursorAt(last.x, last.y)
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

        override fun set(v: InputOverlayData?, oldV: InputOverlayData?, inited: Boolean): EmptyFun? {
            if (v != null) setInputOverlay(v) else hideInputOverlay()
            return null
        }
    }

    open fun setCursor(v: CursorType) {}
    open fun setInputOverlay(data: InputOverlayData) {}
    open fun hideInputOverlay() {}
    fun destroy() {
        register.destroy();
        destroyFun()
        cursorTrack.dispose()
        overlayTrack.dispose()
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
        recordPicture(outerWidth, outerHeight) { draw(it) }
    }

    fun render(canvas: PlatformCanvas) {
        scheduled = true
        try {
            canvas.clear(rgba(255, 255, 255))
            signal.collect {
                didDraw().draw(canvas, 0f, 0f)
            }
        } catch (err: Throwable) {
            println("render error--$err")
        }
        scheduled = false
    }

    private fun setFocused(node: Node?) {
        val old = register.focused
        if (old === node) return
        register.focused = node
    }

    private val focusableNodes by memo {
        val result = mutableListOf<Node>()
        fun collect(node: Node) {
            if (node.focusable && !node.hide) result.add(node)
            node.children.forEach(::collect)
        }
        children.forEach(::collect)
        if (result.any { it.focusOrder != null }) result.sortBy { it.focusOrder ?: Int.MAX_VALUE }
        result
    }

    private fun moveFocus(next: Boolean) {
        val nodes = focusableNodes
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

    fun mouseDown(x: Float, y: Float) {
        try {
            val hit = hitTestResult(x, y)
            // 让 moveHitest 始终反映最近的指针位置（含按下瞬间）
            register.moveHitTest = hit
            register.pressed = hit
            setFocused(hit.chain.last().node)
            dispatchPointer(hit, PointerType.Down, x, y)
        } catch (e: Throwable) {
            println("mouseDown error--$e")
        }
    }

    fun mouseUp(x: Float, y: Float) {
        try {
            // 按下信息先快照再清空，供下方 click 判定使用
            val down = register.pressed
            register.pressed = null
            // 指针捕获：up 先投递给捕获者并结束捕获，随后仍走正常树分发（非按下态，通常无副作用）
            val captured = register.captured(0)
            if (captured != null) {
                val e = PointerEvent(type = PointerType.Up, x = x, y = y, rootX = x, rootY = y)
                captured.onUp(e)
                captured.release()
            }
            val hit = hitTestResult(x, y)
            register.moveHitTest = hit
            dispatchPointer(hit, PointerType.Up, x, y)
            if (down != null) {
                val dx = x - down.chain.first().x
                val dy = y - down.chain.first().y
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                val dt = System.currentTimeMillis() - down.time
                if (dist < 5f && dt < 500L) {
                    dispatchPointer(hit, PointerType.Click, x, y)
                }
            }
            // 注意：修饰键反映真实键盘状态，松开鼠标不清空
        } catch (e: Throwable) {
            println("mouseUp error--$e")
        }
    }

    fun mouseMove(x: Float, y: Float) {
        try {
            // 指针捕获：move 只投递给捕获者，不进入树分发
            val captured = register.captured(0)
            if (captured != null) {
                val e = PointerEvent(type = PointerType.Move, x = x, y = y, rootX = x, rootY = y)
                captured.onMove(e)
                return
            }
            val hit = hitTestResult(x, y)
            register.moveHitTest = hit
            dispatchPointer(hit, PointerType.Move, x, y)
        } catch (e: Throwable) {
            println("mouseMove error--$e")
        }
    }

    fun mouseExit() {
        register.pressed = null
        register.moveHitTest = null
    }

    fun mouseWheel(x: Float, y: Float, delta: Float) {
        try {
            val hit = hitTestResult(x, y)
            dispatchPointer(hit, PointerType.Wheel, x, y, wheelDelta = delta)
        } catch (e: Throwable) {
            println("mouseWheel error--$e")
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
                        val current = register.selectionManager.current
                        if (current != null && current.hasSelection) {
                            current.cut()
                            return
                        }
                    }

                    'v' -> {
                        val current = register.selectionManager.current
                        if (current != null) {
                            current.paste()
                            return
                        }
                    }
                }
            }

            val handled = register.focused?.handleKey(e) ?: false
            if (!handled) {
                if (!alt && !meta && !ctrl && code == KeyCode.Tab) {
                    moveFocus(!shift)
                    return
                }
                register.dispatchKeyPress(e)
            }
        } catch (e: Throwable) {
            println("keyboard error--$e")
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
            println("IME error--$e")
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
            println("keyboard error--$e")
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
    wheelDelta: Float = 0f
) {
    result.chain.forEach {
        //捕获
        val e = PointerEvent(
            type = type,
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

private fun Node.hitTestResult(x: Float, y: Float): HitestResult {
    return HitestResult(
        hitTest(x, y).orEmpty(),
        System.currentTimeMillis()
    )
}