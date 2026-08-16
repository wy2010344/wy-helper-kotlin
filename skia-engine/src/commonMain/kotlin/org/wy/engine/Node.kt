package org.wy.engine

import com.wy.mve.DestroyHolder
import com.wy.mve.ShareConfig
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import com.wy.mve.ValueOrGetList
import org.wy.lib.GetValue

enum class Direction {
    x, y
}

val Direction.opposite: Direction
    get() = when (this) {
        Direction.x -> Direction.y
        Direction.y -> Direction.x
    }

data class NodeWithPosition(
    val node: Node,
    val x: Float,
    val y: Float
)

fun List<NodeWithPosition>.include(node: Node): Boolean {
    return this.find { it.node == node } != null
}

/** 命中链是否包含指定节点（供 hover / pressed 判断，链头为根容器）。 */
fun HitestResult.include(node: Node): Boolean = chain.include(node)

/** 命中点在窗口坐标系中的 X（= 链头根容器的绝对坐标 + 局部偏移）。 */
val NodeWithPosition.windowX: Float
    get() = node.absolutePosition(Direction.x) + x

/** 命中点在窗口坐标系中的 Y。 */
val NodeWithPosition.windowY: Float
    get() = node.absolutePosition(Direction.y) + y

fun Node.contains(node: Node): Boolean {
    if (node == this) {
        return true
    }
    return children.find { it == node } != null
}

internal val nodeConfig = object : ShareConfig<Node, List<Node>> {
    fun ignore(node: Node): Boolean {
        return node.hide
    }

    override fun after(list: List<Node>) {
        collectIndex(list)
    }

    override fun purifyList(nodes: List<ValueOrGetList<Node>>): List<Node> {
        val newList = mutableListOf<Node>()
        com.wy.mve.purifyList(nodes, newList, ::ignore)
        return newList
    }
}

infix fun StateHolder<*, *>.unaryPlus(right: Node) {

}

/**
 * @todo，还是老实将context添加与构造分开，比如ooc中就没有构造，匿名类只是事件回调。
 */
open class Node(
    protected val context: StateHolder<*, *>?,
    engineGlobal: EngineGlobal? = context?.consume(engineGlobalContext)
) {
    open fun cursorAt(x: Float, y: Float) =
        if (focusable) CursorType.POINTER else CursorType.DEFAULT

    /**
     * 声明式输入法输入框数据：默认 null 表示不需要 IME 输入框；
     * 需要输入框的节点（如 [EditableTextNode]）override 返回当前光标位置等信息。
     */
    open fun inputOverlay(): InputOverlayData? = null
    val engineGlobal: EngineGlobal
    open val hide = false
    val parent: Node?

    init {
        if (engineGlobal == null) {
            throw Error("未找到EngineGlobal")
        }
        this.engineGlobal = engineGlobal
        if (context == null) {
            parent = null
        } else {
            val p = context.getParent()
            if (p is Node) {
                parent = p
                @Suppress("UNCHECKED_CAST")
                (context as StateHolder<Node, *>).addNode(this)
            } else if (p != null) {
                parent = null
            } else {
                throw Error("需要找到parent")
            }
        }
    }

    open fun StateHolderWithNode<Node, List<Node>>.argChildren() {}

    /**
     * children 的惰性构建：构造期只声明构建动作，首次访问 [children] 时才真正执行
     * `renderNode → argChildren()`。这样 argChildren() 运行时派生类属性已全部初始化，
     * 不会读到默认值（基类构造期调用 open 方法的经典问题）。
     */
    private var getChildrenValue: GetValue<List<Node>>? = null
    private var childrenBuilding = false
    protected open fun createGetChildren(): () -> List<Node> {
       return (context?.renderNode(this, nodeConfig) { argChildren() }
            ?: { emptyList() })
    }
    val children: List<Node>
        get(){
            val g = getChildrenValue
            if (g != null) return g()
            check(!childrenBuilding) { "children 构建中不能递归访问" }
            childrenBuilding = true
            try {
                val build = createGetChildren()
                getChildrenValue=build
                return build()
            } finally {
                childrenBuilding = false
            }
        }

    open fun argPosition(direction: Direction): Float = 0f

    var index = 0
        internal set
        get() {
            if (hide) {
                throw Error("已经隐藏不再显示")
            }
            parent?.children
            return field
        }

    open val x: Float
        get() = argPosition(Direction.x)

    open val y: Float
        get() = argPosition(Direction.y)

    open fun acceptHit(x: Float, y: Float): Boolean {
        return false
    }

    open fun acceptClip(x: Float, y: Float): Boolean = true

    // 指针 / 键盘事件：所有节点都有默认空实现，需要处理的子类 override
    // 指针事件在命中链上先捕获（Capture，子→根）再冒泡（根→子），可 stopPropagation 中断。
    open fun onPointerClick(e: PointerEvent) {}
    open fun onPointerClickCapture(e: PointerEvent) {}
    open fun onPointerDown(e: PointerEvent) {}
    open fun onPointerDownCapture(e: PointerEvent) {}
    open fun onPointerUp(e: PointerEvent) {}
    open fun onPointerUpCapture(e: PointerEvent) {}
    open fun onPointerMove(e: PointerEvent) {}
    open fun onPointerMoveCapture(e: PointerEvent) {}
    open fun onPointerWheel(e: PointerEvent) {}
    open fun onPointerWheelCapture(e: PointerEvent) {}
    open fun handleKey(e: KeyEvent): Boolean = false

    /**
     * 是否可被 Tab 焦点遍历拾取（相当于 web 的 `tabindex >= 0`）。
     */
    open val focusable: Boolean = false

    /**
     * Tab 遍历的显式顺序（相当于 web 的 `tabindex` 正数、Compose 的 `focusOrder`、
     * Flutter 的 `FocusTraversalOrder`）。值越小越先被遍历；`null` 表示不指定，
     * 排在所有显式顺序之后，按文档顺序。
     */
    open val focusOrder: Int? = null

    /**
     * 是否圈定焦点（相当于 web 模态对话框的焦点陷阱）：为 true 时，
     * Tab / Shift+Tab 只在以本节点为根的子树上循环，焦点不逃逸到外部。
     *
     * 由弹出层（如 [org.wy.engine.helper.DialogBase]）声明为 true。
     * 引擎不存储"当前圈定者"状态，moveFocus 时从当前 [EngineGlobal.focused]
     * 沿父链上溯到最近的 focusTrap 节点即得圈定范围——焦点在最内层弹出层时
     * 就圈定最内层，天然支持嵌套，无需额外中间状态。
     */
    open val focusTrap: Boolean = false
    open fun draw(canvas: PlatformCanvas) {
        children.forEach { drawChild(it, canvas) }
    }

    /**
     * 绘制单个子节点：save → drawAtParent → translate → draw → restore。
     * 子类可覆写以加入裁剪/跳过逻辑（如滚动容器只绘制可视区域内的子节点）。
     */
    open fun drawChild(child: Node, canvas: PlatformCanvas) {
        canvas.save()
        child.drawAtParent(canvas)
        canvas.translate(child.x, child.y)
        child.draw(canvas)
        canvas.restore()
    }

    open fun drawAtParent(canvas: PlatformCanvas) {}

}

fun Node.hitTest(x: Float, y: Float): MutableList<NodeWithPosition>? {
    val rx = x - this.x
    val ry = y - this.y
    children.asReversed().forEach {
        if (it.acceptClip(rx, ry)) {
            val node = it.hitTest(rx, ry)
            if (node != null) {
                node.add(0, NodeWithPosition(this, rx, ry))
                return node
            }
        }
    }
    if (acceptHit(rx, ry)) {
        return mutableListOf(NodeWithPosition(this, rx, ry))
    }
    return null
}

/**
 * 当前是否持有焦点（等价于 `EngineGlobal.focused === this`）。
 * `focused` 是信号，在 `draw` / `memo` 里读取即为响应式，焦点变化会自动触发重绘。
 */
val Node.isFocused: Boolean
    get() = engineGlobal.focused === this

/**
 * 请求获得焦点（等价于 `EngineGlobal.focused = this`）。
 */
fun Node.requestFocus() {
    engineGlobal.focused = this
}

fun Node.position(direction: Direction) = when (direction) {
    Direction.x -> x
    Direction.y -> y
}

fun Node.absolutePosition(d: Direction): Float {
    var n = position(d)
    var p = parent
    while (p != null) {
        n += p.position(d)
        p = p.parent
    }
    return n
}

val Node.absoluteX
    get() = absolutePosition(Direction.x)
val Node.absoluteY
    get() = absolutePosition(Direction.y)

internal fun collectIndex(list: List<Node>) {
    var index = 0
    for (node in list) {
        node.index = index++
    }
}
