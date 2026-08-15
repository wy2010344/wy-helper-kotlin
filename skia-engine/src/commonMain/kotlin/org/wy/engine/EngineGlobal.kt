package org.wy.engine

import com.wy.mve.Context
import org.wy.lib.EmptyFun

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

/**
 * 指针捕获句柄：捕获后该指针的 Move / Up 事件只投递给捕获者，
 * 直到 up 事件或显式 [release] 结束捕获。
 */
interface PointerCapture {
    fun release()
}

interface EngineGlobal {
    fun registerKeyPress(callback: KeyPressCallback): EmptyFun
    fun registerComposingText(callback: ComposingTextCallback): EmptyFun

    /**
     * 当前按下状态（响应式信号）。null = 未按下；
     * 非空时携带按下时的命中链与时间，按下坐标可用 `pressed.chain.windowX/Y` 取。
     */
    val pressed: HitestResult?

    /** 最近一次指针位置的命中链（响应式信号，供 hover 与指针位置查询）。 */
    val moveHitTest: HitestResult?

    /**
     * 修饰键实时状态（响应式信号，四个键独立可观察）。
     * 反映真实键盘按键状态，与鼠标按下无关；由平台每次鼠标 / 键盘事件刷新，
     * 窗口失焦时清空。绘制层可 `memo { g.shift }` 按需依赖单一按键，
     * 不会因其他修饰键变化而重跑。
     */
    val ctrl: Boolean
    val shift: Boolean
    val alt: Boolean
    val meta: Boolean

    /** 当前激活输入法输入框的编辑器（全局唯一，响应式信号）。 */
    var activeEditor: EditableTextNode?

    var focused: Node?

    val selectionManager: SelectionManager

    /**
     * 捕获指针。典型调用时机：节点在 `onPointerDown` 内调用。
     * 捕获后该 [id] 的 Move / Up 事件只投递给 [onMove] / [onUp]，
     * up 后自动结束（调用方也可提前 [PointerCapture.release]）。
     */
    fun capturePointer(
        id: Int,
        onMove: (PointerEvent) -> Unit,
        onUp: (PointerEvent) -> Unit
    ): PointerCapture

}

/**
 * 一次指针按下的完整状态：按下时的命中链 + 按下时刻。
 * 用于点击 / 长按判定（时间差）与按下起点坐标（`chain.windowX/Y`）。
 */
data class HitestResult(
    val chain: List<NodeWithPosition>,
    val time: Long
)

/** 输入法输入框的声明数据：位置、尺寸与字号。 */
data class InputOverlayData(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val fontSize: Float
)

val engineGlobalContext = Context<EngineGlobal?>(null)
