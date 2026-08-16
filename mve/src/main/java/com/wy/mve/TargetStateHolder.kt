package com.wy.mve

import org.wy.lib.EmptyFun
import org.wy.lib.GetValue
import org.wy.lib.SetValue
import org.wy.signal.Memo

interface RootReturn<Target> : DestroyHolder {
    fun destroy()
    val target: GetValue<Target>
}

internal open class TargetStateHolder<Node, Target>(
    override val node: Node,
    config: ShareConfig<Node, Target>,
    private val callback: StateHolderWithNode<Node, Target>.() -> Unit,
    parent: StateHolderI<*, *>? = null
) : StateHolderI<Node, Target>(config, parent), RootReturn<Target>,
    StateHolderWithNode<Node, Target> {
    override fun buildChildren() {
        provide<Node>(parentContext as Context<Node>, node)
        callback()
    }

    override val target = object : Memo<Target>() {
        override fun get(old: Target?, inited: Boolean): Target {
            return config.purifyList(nodes)
        }

        override fun toString(): String {
            return "target-memo"
        }

        init {
            afters.add(config::after)
        }
    }

    override fun toString(): String {
        return "root-render"
    }
}


internal val parentContext = Context<Any?>(null)


fun <Node, Target> renderRoot(
    node: Node,
    config: ShareConfig<Node, Target>,
    callback:StateHolderWithNode<Node, Target>.() -> Unit
): RootReturn<Target> {
    val node = TargetStateHolder(node, config, callback)
    node.create()
    return node
}
