package org.wy.engine

import com.wy.mve.ShareConfig
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import com.wy.mve.ValueOrGetList
import org.wy.lib.GetValue

interface KeyHandler {
    fun handleKey(e: KeyEvent): Boolean
}

interface MouseListener {
    fun mouseDown(e: MouseEvent)
    fun mouseDownCapture(e: MouseEvent)
    fun mouseUp(e: MouseEvent)
    fun mouseUpCapture(e: MouseEvent)
    fun mouseMove(e: MouseEvent)
    fun mouseMoveCapture(e: MouseEvent)
    fun mouseClick(e: MouseEvent)
    fun mouseClickCapture(e: MouseEvent)
}

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

open class Node(
    val context: StateHolder<*, *>?
) : MouseListener, KeyHandler {
    open val hide = false
    val parent: Node?

    init {
        if (context == null) {
            parent = null
        } else {
            val p = context.getParent()
            if (p is Node) {
                parent = p
                (context as StateHolder<Node, *>).addNode(this)
            } else if (p != null) {
                parent = null
            } else {
                throw Error("需要找到parent")
            }
        }
    }

    open fun StateHolderWithNode<Node, List<Node>>.argChildren() {}

    var getChildren: GetValue<List<Node>> = context?.renderNode(this, nodeConfig) {
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

    override fun mouseClick(e: MouseEvent) {}
    override fun mouseClickCapture(e: MouseEvent) {}
    override fun mouseDown(e: MouseEvent) {}
    override fun mouseDownCapture(e: MouseEvent) {}
    override fun mouseUp(e: MouseEvent) {}
    override fun mouseUpCapture(e: MouseEvent) {}
    override fun mouseMove(e: MouseEvent) {}
    override fun mouseMoveCapture(e: MouseEvent) {}

    override fun handleKey(e: KeyEvent): Boolean = false

    open val focusable: Boolean = false

    open val focusOrder: Int? = null

    val isFocused: Boolean
        get() = engineGlobal?.focused === this

    fun requestFocus() {
        engineGlobal?.focused = this
    }

    open fun clipRect(): RectF? = null

    open fun draw(canvas: PlatformCanvas) {
        drawChildren(canvas)
    }

    private val engineGlobal: EngineGlobal? = context?.consume(engineGlobalContext)
}

internal fun Node.drawChildren(canvas: PlatformCanvas) {
    children.forEach {
        canvas.save()
        val clipRect = it.clipRect()
        if (clipRect != null) {
            canvas.clipRect(clipRect.left, clipRect.top, clipRect.right - clipRect.left, clipRect.bottom - clipRect.top)
        }
        canvas.translate(it.position(Direction.x), it.position(Direction.y))
        it.draw(canvas)
        canvas.restore()
    }
}

fun Node.hitTest(x: Float, y: Float): NodeWithPosition? {
    val rx = x - this.x
    val ry = y - this.y
    children.asReversed().forEach {
        if (it.acceptClip(rx, ry)) {
            val node = it.hitTest(rx, ry)
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
