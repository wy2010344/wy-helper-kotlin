package org.wy.engine

/**
 * 指针事件类型。
 *
 * 新增取值一律追加到末尾：枚举序号（ordinal）一旦被外部持久化就不能重排，
 * 否则历史数据会与新的取值错位。
 */
enum class PointerType {
    Down, Move, Up, Click, Cancel, Wheel,

    /**
     * 双击：两次点击（时间间隔与落点都接近）中的第二次额外派发此事件。
     *
     * 与 Flutter / Compose 的手势语义一致，双击**不会**吞掉普通单击——同一次双击
     * 仍会先派发 [Click] 再派发本事件，因此只关心单击的节点无需为双击做任何特殊处理。
     */
    DoubleClick,

    /**
     * 右键菜单：右键按下时派发，供节点弹出上下文菜单。
     *
     * 右键不会派发 [Down]：否则右键按下会顺带启动文本拖选、滑块拖动等
     * 左键专属行为，右键就退化成了"假左键"。
     */
    ContextMenu
}

/**
 * 输入设备类型。
 */
enum class PointerDevice {
    Mouse, Touch, Pen
}

/**
 * 鼠标按键编号与按住位掩码。
 *
 * 编号沿用 AWT `MouseEvent.getButton()`：1=左键 2=中键 3=右键，
 * 平台层可以直接把 `MouseEvent.getButton()` 透传给引擎。
 *
 * 掩码沿用 AWT `MouseEvent.BUTTONn_DOWN_MASK` 的位序（左=1 中=2 右=4），
 * 用于把"当前按住了哪些键"压缩进一个整数（见 [PointerEvent.buttons]）。
 */
object PointerButton {
    /** 左键。 */
    const val LEFT = 1

    /** 中键。 */
    const val MIDDLE = 2

    /** 右键（次要按键）。 */
    const val RIGHT = 3

    /** 左键按住位。 */
    const val LEFT_DOWN_MASK = 1

    /** 中键按住位。 */
    const val MIDDLE_DOWN_MASK = 2

    /** 右键按住位。 */
    const val RIGHT_DOWN_MASK = 4

    /**
     * 按键编号 → 该键的按住位。
     *
     * 未知编号返回 0：宁可少报一个键，也不要把"不认识的键"混进按住状态里
     * 让调用方误判出左键被按住。
     */
    fun maskOf(button: Int): Int = when (button) {
        LEFT -> LEFT_DOWN_MASK
        MIDDLE -> MIDDLE_DOWN_MASK
        RIGHT -> RIGHT_DOWN_MASK
        else -> 0
    }
}

/**
 * 统一指针事件：鼠标 / 触摸 / 触控笔在渲染器入口归一化为同一模型。
 *
 * - [x]/[y]：相对当前传播节点的局部坐标（随命中链传播自动换算）
 * - [rootX]/[rootY]：相对渲染根的全局坐标
 * - [id]：指针编号，多点触控 / 多笔场景用于区分
 * - [button]：触发本次事件的按键编号（见 [PointerButton]）；移动、滚轮等
 *   没有明确按键的事件为 0
 * - [buttons]：当前按住的按键位掩码（见 [PointerButton]），可据此判断
 *   "是否正按住某个键拖拽"这类状态
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
    /** 垂直滚轮增量（旧字段，语义等价于 wheelDeltaY，保留以兼容既有读取方） */
    val wheelDelta: Float = 0f,
    /** 水平滚轮增量（触控板横向滑动 / 带横向滚轮的设备） */
    val wheelDeltaX: Float = 0f,
    /** 垂直滚轮增量 */
    val wheelDeltaY: Float = 0f,
    val button: Int = 0
) {
    var stoppedProgression = false
        private set

    fun stopPropagation() {
        stoppedProgression = true
    }
}
