package com.wy.mve

import org.wy.lib.GetValue
import org.wy.lib.QuoteValue
import org.wy.lib.SetValue
import org.wy.signal.Memo
import org.wy.signal.memo
import java.rmi.server.RemoteCall

interface RootReturn<Node> {
    fun destroy()
    val target: GetValue<List<Node>>
}

internal open class TargetStateHolder<Node>(
    private val node: Node,
    after: SetValue<List<Node>>?,
    private val callback: StateHolder<Node>.() -> Unit,
    parent: StateHolderI<Node>?=null
) : StateHolderI<Node>(parent), RootReturn<Node> {
    override fun buildChildren() {
        provide<Node>(parentContext as Context<Node>, node)
        callback()
    }

    override val target = object : Memo<List<Node>>() {
        override fun get(old: List<Node>?, inited: Boolean): List<Node> {
            val newList = mutableListOf<Node>()
            purifyList(nodes, newList)
           return newList
        }

        override fun toString(): String {
            return "target-memo"
        }
    }.apply {
        if (after != null) {
            afters.add(after)
        }
    }

    override fun toString(): String {
        return "root-render"
    }
}


internal val parentContext = Context<Any?>(null)


fun <Node> renderRoot(
    node: Node,
    after: SetValue<List<Node>>? = null,
    callback: StateHolder<Node>.() -> Unit
): RootReturn<Node> {
    val node= TargetStateHolder(node, after, callback)
    node.create()
    return  node
}