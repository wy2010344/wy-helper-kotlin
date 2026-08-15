package org.wy.engine

/**
 * 指针事件类型。
 */
enum class PointerType {
    Down, Move, Up, Click, Cancel, Wheel
}

/**
 * 输入设备类型。
 */
enum class PointerDevice {
    Mouse, Touch, Pen
}

/**
 * 统一指针事件：鼠标 / 触摸 / 触控笔在渲染器入口归一化为同一模型。
 *
 * - [x]/[y]：相对当前传播节点的局部坐标（随命中链传播自动换算）
 * - [rootX]/[rootY]：相对渲染根的全局坐标
 * - [id]：指针编号，多点触控 / 多笔场景用于区分
 *
 * 修饰键（Ctrl/Shift 等）不在事件中携带，统一读取全局信号
 * [EngineGlobal.ctrl] / [EngineGlobal.shift] / [EngineGlobal.alt] / [EngineGlobal.meta]
 * （反映真实键盘按键状态）。
 *
 * 节点在捕获 / 冒泡处理中调用 [stopPropagation] 可中断传播。
 */
class PointerEvent(
    val id: Int = 0,
    val type: PointerType,
    val device: PointerDevice = PointerDevice.Mouse,
    val x: Float,
    val y: Float,
    val rootX: Float = 0f,
    val rootY: Float = 0f,
    val buttons: Int = 0,
    val pressure: Float = 1f,
    val wheelDelta: Float = 0f
) {
    var stoppedProgression = false
        private set

    fun stopPropagation() {
        stoppedProgression = true
    }
}
