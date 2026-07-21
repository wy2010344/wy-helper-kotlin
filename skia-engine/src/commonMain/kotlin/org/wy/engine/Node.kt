package org.wy.engine

import com.wy.mve.StateHolder
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


open class Node(
    val parent: Node?
) {
    constructor(context: StateHolder<Node>) : this(
        context.getParent() ?: throw Error("需要找到父节点才行")
    ) {
        getChildren = context.renderNode(this, ::collectIndex) {
            argChildren()
        }
    }

    open fun StateHolder<Node>.argChildren() {}

    var getChildren: GetValue<List<Node>> = { emptyList() }
        internal set
    val children: List<Node>
        get() = getChildren()

    open fun argPosition(direction: Direction): Float = 0f

    var index = 0
        internal set
        get() {
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

    open fun mouseClick(e: MouseEvent) {}
    open fun mouseClickCapture(e: MouseEvent) {}
    open fun mouseDown(e: MouseEvent) {}
    open fun mouseDownCapture(e: MouseEvent) {}
    open fun mouseUp(e: MouseEvent) {}
    open fun mouseUpCapture(e: MouseEvent) {}

    open fun draw(canvas: PlatformCanvas) {
        drawChildren(canvas)
    }
}

fun Node.drawChildren(canvas: PlatformCanvas) {
    children.forEach {
        canvas.save()
        canvas.translate(it.x, it.y)
        it.draw(canvas)
        canvas.restore()
    }

}


fun Node.hitest(x: Float, y: Float): NodeWithPosition? {
    val rx = x - this.x
    val ry = y - this.y
    children.asReversed().forEach {
        val node = it.hitest(rx, ry)
        if (node != null) {
            return NodeWithPosition(this, rx, ry, node)
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
    var layoutIndex = 0
    for (node in list) {
        node.index = index++
        if (node is RectNode) {
            node.layoutIndex = layoutIndex++
        }
    }

}