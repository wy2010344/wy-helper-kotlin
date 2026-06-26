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
    private val callback: StateHolder<Node>.() -> Unit
) : StateHolderI<Node>(), RootReturn<Node> {
    init {
        create()
    }

    override fun buildChildren() {
        provide(parentContext, node)
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
}


internal val parentContext = Context<Any?>(null)


fun <Node> renderRoot(
    node: Node,
    after: SetValue<List<Node>>? = null,
    callback: StateHolder<Node>.() -> Unit
): RootReturn<Node> {
    return TargetStateHolder(node, after, callback)
}