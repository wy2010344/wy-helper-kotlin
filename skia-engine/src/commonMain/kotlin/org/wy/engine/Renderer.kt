package org.wy.engine

import com.wy.mve.StateHolder
import com.wy.mve.renderListRoot
import org.wy.lib.EmptyFun
import org.wy.signal.TrackSignal
import kotlin.collections.set

private class Register(context: StateHolder<Node>?) {
    init {
        if (context != null) {
            provide(context)
        }
    }

    fun destroy() {
        moveList.clear()
        upList.clear()
        wheelList.clear()
        keyPressList.clear()
        composingList.clear()
    }

    private val moveList = mutableMapOf<MouseCallback, EmptyFun>()
    private val upList = mutableMapOf<MouseCallback, EmptyFun>()
    private val wheelList = mutableMapOf<WheelCallback, EmptyFun>()
    private val keyPressList = mutableMapOf<KeyPressCallback, EmptyFun>()
    private val composingList = mutableMapOf<ComposingTextCallback, EmptyFun>()

    fun provide(context: StateHolder<Node>) {
        context.provide(engineGlobalContext, object : EngineGlobal {
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
        })
    }

    fun dispatchMouseUp(x: Float, y: Float) {
        upList.forEach { it.key(GlobalMouseEvent(x, y, it.value)) }
    }

    fun dispatchMouseMove(x: Float, y: Float) {

        moveList.forEach { it.key(GlobalMouseEvent(x, y, it.value)) }
    }

    fun dispatchMouseWheel(x: Float, y: Float, delta: Float) {
        wheelList.forEach { it.key(GlobalWheelEvent(x, y, delta, it.value)) }
    }

    fun dispatchKeyPress(e: KeyEvent) {keyPressList.forEach { it.key(e) }}
    fun dispatchComposingText(text: String, cursorPosition: Int) {
        composingList.forEach { it.key(text, cursorPosition) }
    }
}

open class Renderer private constructor(
    context: StateHolder<Node>?,
    private val register: Register
) : LayoutNode(null) {
    constructor(context: StateHolder<Node>?) : this(context, Register(context)) {
        if (context == null) {
            val state = renderListRoot<Node>(this@Renderer, ::collectIndex) {
                register.provide(this)
                argChildren()
            }
            this.getChildren = state.target
            this.destroyFun = state::destroy
        }
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

    fun render(canvas: PlatformCanvas) {
        scheduled = true
        try {
            canvas.clear(rgba(255, 255, 255))
            signal.collect {
                outerSize(Direction.x)
                outerSize(Direction.y)
                draw(canvas)
            }
        } catch (err: Throwable) {
            println("渲染出错--$err")
        }
        scheduled = false
    }

    private fun mouseEventOf(x: Float, y: Float, type: MouseEventEnum) {
        try {
            var nodeWithPosition: NodeWithPosition? = hitest(x, y) ?: return
            val list = mutableListOf<NodeWithPosition>()
            //这里检查时，如果直接事件发生，造成状态改变，再向上查询时会出错。
            while (nodeWithPosition != null) {
                //捕获
                val e = MouseEvent(nodeWithPosition.x, nodeWithPosition.y, x, y)
                sendMouseEvent(nodeWithPosition.node, type, e, true)
                if (e.stoppedProgression) {
                    return
                }
                list.add(nodeWithPosition)
                nodeWithPosition = nodeWithPosition.next
            }
            list.asReversed().forEach {
                //冒泡
                val e = MouseEvent(it.x, it.y, x, y)
                sendMouseEvent(it.node, type, e, false)
                if (e.stoppedProgression) {
                    return
                }
            }
        } catch (e: Throwable) {
            println("事件出错--${type}-$e")
        }
    }

    fun mouseClick(x: Float, y: Float) {
        mouseEventOf(x, y, MouseEventEnum.click)
    }

    private fun sendMouseEvent(node: Node, type: MouseEventEnum, e: MouseEvent, capture: Boolean) {
        when (type) {
            MouseEventEnum.click -> if (capture) node.mouseClickCapture(e) else node.mouseClick(e)
            MouseEventEnum.down -> if (capture) node.mouseDownCapture(e) else node.mouseDown(e)
            MouseEventEnum.up -> if (capture) node.mouseUpCapture(e) else node.mouseUp(e)
        }
    }

    fun mouseDown(x: Float, y: Float) {
        mouseEventOf(x, y, MouseEventEnum.down)
    }

    fun mouseUp(x: Float, y: Float) {
        try {
            mouseEventOf(x, y, MouseEventEnum.up)
            register.dispatchMouseUp(x, y)

        } catch (e: Throwable) {
            println("全局mouseup事件出错--$e")
        }
    }

    fun mouseMove(x: Float, y: Float) {
        try {
            register.dispatchMouseMove(x,y)
        } catch (e: Throwable) {
            println("全局mouseup事件出错--$e")
        }
    }

    fun mouseWheel(x: Float, y: Float, delta: Float) {
        try {
            register.dispatchMouseWheel(x,y,delta)
        } catch (e: Throwable) {
            println("全局mouseup事件出错--$e")
        }
    }

    fun keyPress(key: Char, code: KeyCode, ctrl: Boolean, shift: Boolean, alt: Boolean) {
        try {
            val e = KeyEvent(key, code, ctrl, shift, alt)
            register.dispatchKeyPress(e)
        } catch (e: Throwable) {
            println("键盘事件出错--$e")
        }
    }

    fun composingText(text: String, cursorPosition: Int) {
        try {
            register.dispatchComposingText(text,cursorPosition)
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
    click, down, up
}
