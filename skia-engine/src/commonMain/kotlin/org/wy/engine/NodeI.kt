package org.wy.engine

import com.wy.mve.StateHolder

abstract class NodeI(
    context: StateHolder<Node>
) : Node {

    final override val parent: Node = context.getParent() ?: throw Error("需要找到父节点才行")
    private val target = context.renderNode(this, ::collectIndex) {
        buildChildren()
    }

    var index: Int = 0
        internal set
        get() {
            parent.children
            return field
        }

    override val children: List<Node>
        get() = target()
}