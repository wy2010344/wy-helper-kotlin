package org.wy.engine

import com.wy.mve.Context
import org.wy.lib.EmptyFun
import org.wy.lib.StoreRef

data class GlobalMouseEvent(
    val x: Float,
    val y: Float,
    val destroy: EmptyFun
)

typealias MouseCallback=(e:GlobalMouseEvent)-> Unit
data class GlobalWheelEvent(
    val x: Float,
    val y: Float,
    val delta: Float,
    val destroy: EmptyFun
)
typealias WheelCallback=(e:GlobalWheelEvent)-> Unit

expect enum class KeyCode {
    Backspace, Delete, Left, Right, Home, End, Up, Down, Enter, Tab, Escape, Unknown
}

data class KeyEvent(
    val key: Char,
    val code: KeyCode,
    val ctrl: Boolean,
    val shift: Boolean,
    val alt: Boolean,
    val meta: Boolean
)

typealias KeyPressCallback = (e: KeyEvent) -> Unit
typealias ComposingTextCallback = (text: String, cursorPosition: Int) -> Unit

enum class CursorType {
    DEFAULT, POINTER, TEXT
}

interface EngineGlobal {
    fun registerMouseDown(callback: MouseCallback): EmptyFun
    fun registerMouseMove(callback:MouseCallback): EmptyFun
    fun registerMouseUp(callback: MouseCallback): EmptyFun
    fun registerMouseWheel(callback: WheelCallback): EmptyFun
    fun registerKeyPress(callback: KeyPressCallback): EmptyFun
    fun registerComposingText(callback: ComposingTextCallback): EmptyFun

    val pressed: Boolean
    val moveHitest: NodeWithPosition?

    var focused: Node?
    /**
     * 请求平台在指定屏幕位置显示原生输入控件（隐藏的 JTextField），
     * 用于代理所有文本输入和 IME 组合。
     * 平台实现应负责将 JTextField 定位到 (x, y) 并让它获得焦点。
     */
    fun requestInputOverlay(x: Float, y: Float, w: Float, h: Float, fontSize: Float)

    /** 隐藏原生输入控件 */
    fun hideInputOverlay()

    /** 请求平台切换鼠标光标（如悬停在可点击控件上时显示手型光标） */
    fun requestCursor(type: CursorType)
}

val engineGlobalContext= Context<EngineGlobal?>(null)