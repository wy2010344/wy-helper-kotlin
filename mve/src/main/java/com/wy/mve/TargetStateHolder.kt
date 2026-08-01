package com.wy.mve

import org.wy.lib.GetValue
import org.wy.lib.SetValue
import org.wy.signal.Memo

interface RootReturn<Node> {
    fun destroy()
    val target: GetValue<List<Node>>
}

internal open class TargetStateHolder<Node>(
    override val node: Node,
    config: ShareConfig<Node>,
    private val callback: StateHolderWithNode<Node,List<Node>>.() -> Unit,
    parent: StateHolderI<Node>?=null
) : StateHolderI<Node>(config,parent), RootReturn<Node>, StateHolderWithNode<Node,List<Node>> {
    override fun buildChildren() {
        provide<Node>(parentContext as Context<Node>, node)
        callback()
    }

    override val target = object : Memo<List<Node>>() {
        override fun get(old: List<Node>?, inited: Boolean): List<Node> {
            val newList = mutableListOf<Node>()
            purifyList(nodes, newList,config::ignore)
           return newList
        }

        override fun toString(): String {
            return "target-memo"
        }
    }.apply {
        afters.add(config::after)
    }

    override fun toString(): String {
        return "root-render"
    }
}


internal val parentContext = Context<Any?>(null)


fun <Node> renderListRoot(
    node: Node,
    config: ShareConfig<Node>,
    callback: StateHolderWithNode<Node,List<Node>>.() -> Unit
): RootReturn<Node> {
    val node= TargetStateHolder(node, config, callback)
    node.create()
    return  node
}
