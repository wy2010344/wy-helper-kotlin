package org.wy.engine

import com.wy.mve.Context
import org.wy.lib.EmptyFun

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
    val alt: Boolean
)

typealias KeyPressCallback = (e: KeyEvent) -> Unit
typealias ComposingTextCallback = (text: String, cursorPosition: Int) -> Unit

interface EngineGlobal {
    fun registerMouseMove(callback:MouseCallback): EmptyFun
    fun registerMouseUp(callback: MouseCallback): EmptyFun
    fun registerMouseWheel(callback: WheelCallback): EmptyFun
    fun registerKeyPress(callback: KeyPressCallback): EmptyFun
    fun registerComposingText(callback: ComposingTextCallback): EmptyFun
}

val engineGlobalContext= Context<EngineGlobal?>(null)