package org.wy.engine

import com.wy.mve.StateHolder

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
   val next:NodeWithPosition?
)


interface Node {
    val parent: Node?
    fun position(d: Direction): Float {
        return 0f
    }

    val children: List<Node>

    fun StateHolder<Node>.buildChildren() {}

    fun hitest(x: Float, y: Float): NodeWithPosition? {
        val rx = x - position(Direction.x)
        val ry = y - position(Direction.y)
        children.asReversed().forEach {
            val node = it.hitest(rx, ry)
            if (node != null) {
                return NodeWithPosition(this,rx,ry,node)
            }
        }
        if (acceptHit(rx, ry)) {
            return NodeWithPosition(this, rx, ry,null)
        }
        return null
    }

    fun acceptHit(x: Float, y: Float): Boolean {
        return false
    }

    fun mouseClick(e: MouseEvent) {}
    fun mouseClickCapture(e: MouseEvent){}
    fun draw(canvas: PlatformCanvas) {
        drawSelf(canvas)
        children.forEach {
            canvas.save()
            val x = it.position(Direction.x)
            val y = it.position(Direction.y)
            canvas.translate(x, y)
            it.draw(canvas)
            canvas.restore()
        }
    }

    fun drawSelf(canvas: PlatformCanvas) {}
}

internal fun collectIndex(list: List<Node>) {
    var index = 0
    var layoutIndex = 0
    for (node in list) {
        if (node is NodeI) {
            node.index = index++
        }
        if (node is RectNode) {
            node.layoutIndex = layoutIndex++
        }
    }

}