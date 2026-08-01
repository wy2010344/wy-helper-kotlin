package org.wy.engine

import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
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
    val y: Float,
    val next: NodeWithPosition?
)

fun NodeWithPosition.include(node: Node): Boolean {
    var n: NodeWithPosition? = this
    while (n != null) {
        if (n.node == node) {
            return true
        }
        n = n.next
    }
    return false
}

val NodeWithPosition.last: NodeWithPosition
    get() {
        var it: NodeWithPosition = this
        while (it.next != null) {
            it = it.next
        }
        return it
    }

fun Node.contains(node: Node): Boolean {
    if (node == this) {
        return true

    }
    return children.find { it == node } != null
}

open class Node(
    val context: StateHolder<Node>?
) {
    open val hide = false
    val parent: Node?

    init {
        if (context == null) {
            parent = null
        } else {
            val p = context.getParent()
            if (p is Node) {
                parent = p
                context.addNode(this)
            } else if (p != null) {
                parent = null
            } else {
                throw Error("需要找到parent")
            }
        }
    }

    open fun StateHolderWithNode<Node, List<Node>>.argChildren() {}

    var getChildren: GetValue<List<Node>> = context?.renderListNode(this) {
        argChildren()
    } ?: { emptyList() }
        protected set
    val children: List<Node>
        get() = getChildren()

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

    open fun mouseClick(e: MouseEvent) {}
    open fun mouseClickCapture(e: MouseEvent) {}
    open fun mouseDown(e: MouseEvent) {}
    open fun mouseDownCapture(e: MouseEvent) {}
    open fun mouseUp(e: MouseEvent) {}
    open fun mouseUpCapture(e: MouseEvent) {}

    open fun mouseMove(e: MouseEvent) {}
    open fun mouseMoveCapture(e: MouseEvent) {}

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
     * 当前是否持有焦点（等价于 `EngineGlobal.focused === this`）。
     * `focused` 是信号，在 `draw` / `memo` 里读取即为响应式，焦点变化会自动触发重绘。
     */
    val isFocused: Boolean
        get() = engineGlobal?.focused === this

    /**
     * 请求获得焦点（等价于 `EngineGlobal.focused = this`）。
     */
    fun requestFocus() {
        engineGlobal?.focused = this
    }

    open fun draw(canvas: PlatformCanvas) {
        drawChildren(canvas)
    }

    private val engineGlobal: EngineGlobal? = context?.consume(engineGlobalContext)
}

private fun Node.drawChildren(canvas: PlatformCanvas) {
    children.forEach {
        canvas.save()
        if (it is ScrollContent) {
            val p = it.layoutParent!!
            canvas.clipRect(
                p.padding(Direction.x, StartEnd.start),
                p.padding(Direction.y, StartEnd.start),
                p.innerSize(Direction.x),
                p.innerSize(Direction.y)
            )
        }
        canvas.translate(it.position(Direction.x), it.position(Direction.y))
        it.draw(canvas)
        canvas.restore()
    }

}


fun Node.hitest(x: Float, y: Float): NodeWithPosition? {
    val rx = x - this.x
    val ry = y - this.y
    children.asReversed().forEach {
        if (it.acceptClip(rx, ry)) {
            val node = it.hitest(rx, ry)
            if (node != null) {
                return NodeWithPosition(this, rx, ry, node)
            }
        }
    }
    if (acceptHit(rx, ry)) {
        return NodeWithPosition(this, rx, ry, null)
    }
    return null
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