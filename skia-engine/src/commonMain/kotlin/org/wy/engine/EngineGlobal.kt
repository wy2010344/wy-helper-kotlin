package org.wy.engine

import com.wy.mve.Context
import com.wy.mve.StateHolder
import org.wy.lib.EmptyFun

expect enum class KeyCode {
    Backspace, Delete, Left, Right, Home, End, Up, Down, Enter, Tab, Escape,
    PageUp, PageDown, Unknown
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

/**
 * 系统光标类型。节点通过 [Node.cursorAt] 返回"当前位置该显示什么光标"，
 * 引擎汇总后交给平台实现（如 Desktop 的 AWT `setCursor`）。
 *
 * 各平台实现时：无法精确映射的类型应回退到语义最接近的类型或 [DEFAULT]。
 */
enum class CursorType {
    /** 默认箭头。 */
    DEFAULT,

    /** 手型（可点击元素，如按钮 / 链接）。 */
    POINTER,

    /** 文本 I 型（可编辑文本）。 */
    TEXT,

    /** 十字准线（精确拾取 / 画布）。 */
    CROSSHAIR,

    /** 可拖动 / 移动。 */
    MOVE,

    /** 垂直调整大小（上 / 下）。 */
    RESIZE_NS,

    /** 水平调整大小（左 / 右）。 */
    RESIZE_EW,

    /** 左上—右下对角线调整大小。 */
    RESIZE_NWSE,

    /** 右上—左下对角线调整大小。 */
    RESIZE_NESW,

    /** 忙碌（长时间任务）。 */
    WAIT,

    /** 帮助。 */
    HELP,

    /** 禁止操作。 */
    NOT_ALLOWED,
}

/**
 * 指针捕获句柄：捕获后该指针的 Move / Up 事件只投递给捕获者，
 * 直到 up 事件或显式 [release] 结束捕获。
 */
interface PointerCapture {
    fun release()
}

interface Pop {
    fun render(holder: StateHolder<Node, List<Node>>)
}

interface Toast {
    fun render(holder: StateHolder<Node, List<Node>>)
}

interface EngineGlobal {

    fun appendPop(callback: StateHolder<Node, List<Node>>.(pop: Pop) -> Unit): Pop

    fun removePop(pop: Pop): Boolean

    fun appendToast(callback: StateHolder<Node, List<Node>>.(toast: Toast) -> Unit): Toast

    fun removeToast(toast: Toast): Boolean

    fun registerKeyPress(callback: KeyPressCallback): EmptyFun
    fun registerComposingText(callback: ComposingTextCallback): EmptyFun

    /**
     * 指针选择会话（响应式信号）。null = 尚无会话；
     * mouseDown 创建（Shift 时复用上一会话的 press 以继承锚点），
     * mouseUp / mouseExit 填入 [PointerSelect.release] 定格。
     * 由引擎写入；var 以便测试环境直接模拟指针序列。
     */
    var pointerSelect: PointerSelect?

    /** 最近一次指针位置的命中链（响应式信号，供 hover 与指针位置查询）。 */
    var moveHitTest: HitestResult?

    /**
     * 最近一次指针按下的命中链（响应式信号）。引擎在每次 Down 写入，
     * Up / mouseExit 清空；与 [pointerSelect] 选区会话解耦——双击 / 三击
     * 等刻意不开会话的按下同样驱动按压态与点击派发。
     */
    var pointerDownHit: HitestResult?

    /** 当前是否按住中：即 [pointerDownHit] 尚未被 Up / mouseExit 清除。供按压态视觉反馈等使用。 */
    val pressed: HitestResult?
        get() = pointerDownHit

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

    /**
     * 当前激活输入法输入框的编辑器。
     *
     * 派生自 [focused]：全局焦点唯一，聚焦的 `EditableTextNode` 即活跃编辑器
     * （`focused as? EditableTextNode`）。无独立存储，focused 变化时自动更新，
     * 调用方在反应式上下文（memo / TrackSignal / draw）读取即自动跟踪焦点变化。
     */
    val activeEditor: EditableTextNode?

    var focused: Node?

    val selectionManager: SelectionManager

    /**
     * 渲染树根节点：供选区等"全树派生"做遍历计算（如 SelectionManager 的
     * 可选集合）。由引擎在构建根时注入；headless 测试环境可为 null，
     * 此时 SelectionManager 退回显式提供的补充清单。
     */
    val rootNode: Node?

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

    /**
     * 注册渲染后效果：在 [Renderer.render] 完成绘制后同步消费，
     * 保证回调执行时所有节点已构建完毕（比 signal batch 的 addEffect 更可预测）。
     */
    fun addPostRenderEffect(effect: EmptyFun)

}

/**
 * 一次指针按下的完整状态：按下时的命中链 + 按下时刻 + 输入设备。
 * 用于点击 / 长按判定（时间差）、按下起点坐标（`chain.windowX/Y`）
 * 与"触摸设备不做 hover 反馈"判断（[device]）。
 */
data class HitestResult(
    val chain: List<NodeWithPosition>,
    val time: Long,
    val x: Float,
    val y: Float,
    val device: PointerDevice = PointerDevice.Mouse
)

/**
 * 一次指针选择会话：按下快照 + 释放快照。
 *
 * - [release] 为 null 表示仍在按住中：焦点端点跟随 [EngineGlobal.moveHitTest]；
 * - [release] 非 null 表示已松手定格：焦点冻结在释放位置，hover 不再影响选区；
 * - Shift+按下时引擎复用上一会话的 [press]（锚点继承），仅重新进入按住态——
 *   因此 Shift 扩展、连续 Shift+click 的锚点记忆不需要任何额外状态，全部由
 *   该结构本身承载。
 *
 * 由引擎在 mouseDown（创建 / 复用）与 mouseUp / mouseExit（填 release）写入，
 * 是"指针落在哪"这一原始事实的记录；选区由 SelectionManager 从它纯派生。
 */
data class PointerSelect(
    val press: HitestResult,
    val release: HitestResult?
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
