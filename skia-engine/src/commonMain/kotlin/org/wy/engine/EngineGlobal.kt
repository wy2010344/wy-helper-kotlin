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

data class Modifiers(
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false
) {
    companion object {
        val None = Modifiers()
    }
}

interface EngineGlobal {
    fun registerMouseDown(callback: MouseCallback): EmptyFun
    fun registerMouseMove(callback:MouseCallback): EmptyFun
    fun registerMouseUp(callback: MouseCallback): EmptyFun
    fun registerMouseWheel(callback: WheelCallback): EmptyFun
    fun registerKeyPress(callback: KeyPressCallback): EmptyFun
    fun registerComposingText(callback: ComposingTextCallback): EmptyFun

    fun registerGestureRecognizer(r: GestureRecognizer)
    fun unregisterGestureRecognizer(r: GestureRecognizer)

    val pressed: Boolean
    val moveHitest: NodeWithPosition?

    var focused: Node?

    val selectionManager: SelectionManager
    val gestureArena: GestureArena

    fun requestInputOverlay(x: Float, y: Float, w: Float, h: Float, fontSize: Float)
    fun hideInputOverlay()
    fun requestCursor(type: CursorType)
}

val engineGlobalContext= Context<EngineGlobal?>(null)
