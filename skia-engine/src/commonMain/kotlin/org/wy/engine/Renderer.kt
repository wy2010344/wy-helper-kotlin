package org.wy.engine

import com.wy.layout.Layout
import com.wy.mve.renderRoot
import org.wy.lib.EmptyFun
import org.wy.lib.GetValue
import org.wy.signal.TrackSignal
import org.wy.signal.memo
import kotlin.collections.set

open class Renderer : Node(null), LayoutNode {
    final override val layoutParent: LayoutNode? = null
   final override val layoutIndex: Int = 0

    val getLayoutChildren = memo {
        children.filterIsInstance<LayoutNode>()
    }
    final override val layoutChildren: List<LayoutNode>
        get() = getLayoutChildren()


    private val moveList = mutableMapOf<MouseCallback, EmptyFun>()
    private val upList = mutableMapOf<MouseCallback, EmptyFun>()
    private val wheelList = mutableMapOf<WheelCallback, EmptyFun>()
    private val keyPressList = mutableMapOf<KeyPressCallback, EmptyFun>()
    private val composingList = mutableMapOf<ComposingTextCallback, EmptyFun>()

    private val state = renderRoot<Node>(this@Renderer, ::collectIndex) {
        provide(engineGlobalContext, object : EngineGlobal {
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
        argChildren()
    }

    fun destroy() {
        moveList.clear()
        upList.clear()
        wheelList.clear()
        keyPressList.clear()
        composingList.clear()
        state.destroy()
    }

    init {
        this.getChildren = state.target
    }

    open fun frameCallback() {}

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
            upList.forEach { it.key(GlobalMouseEvent(x, y, it.value)) }
        } catch (e: Throwable) {
            println("全局mouseup事件出错--$e")
        }
    }

    fun mouseMove(x: Float, y: Float) {
        try {
            moveList.forEach { it.key(GlobalMouseEvent(x, y, it.value)) }
        } catch (e: Throwable) {
            println("全局mouseup事件出错--$e")
        }
    }

    fun mouseWheel(x: Float, y: Float, delta: Float) {
        try {
            wheelList.forEach { it.key(GlobalWheelEvent(x, y, delta, it.value)) }
        } catch (e: Throwable) {
            println("全局mouseup事件出错--$e")
        }
    }

    fun keyPress(key: Char, code: KeyCode, ctrl: Boolean, shift: Boolean, alt: Boolean) {
        try {
            val e = KeyEvent(key, code, ctrl, shift, alt)
            keyPressList.forEach { it.key(e) }
        } catch (e: Throwable) {
            println("键盘事件出错--$e")
        }
    }

    fun composingText(text: String, cursorPosition: Int) {
        try {
            composingList.forEach { it.key(text, cursorPosition) }
        } catch (e: Throwable) {
            println("输入法事件出错--$e")
        }
    }


    final override val layoutX: GetValue<Layout> = createLayout(Direction.x)
    final override val layoutY: GetValue<Layout> = createLayout(Direction.y)
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
