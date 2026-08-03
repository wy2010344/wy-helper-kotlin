package com.wy.mve

import org.wy.lib.EmptyFun
import org.wy.lib.GetValue
import org.wy.lib.SetValue
import org.wy.signal.Memo

typealias Creater<Node, Target, T, K, O> = StateHolder<Node, Target>.(K, EachTime<T>) -> O


interface ShareConfig<Node,Target> {
    fun purifyList(nodes:List<ValueOrGetList<Node>>):Target
    fun after(list: Target): Unit
}

enum class DuplicateInfo { IGNORE, WARN, THROW }
interface StateHolder<Node, Target> {
    fun <T> provide(context: Context<T>, value: T)


    fun addNode(n: Node)
    fun <T> consume(context: Context<T>): T

    fun addDestroy(destroy: EmptyFun)

    val destroyed: Boolean

    fun <T, K, O> renderForEach(
        forEach: (callback: (K, T) -> GetValue<O>) -> Unit,
        duplicateInfo: DuplicateInfo = DuplicateInfo.IGNORE,
        creater: Creater<Node, Target, T, K, O>,
    ): Memo<*>

    fun renderNode(
        node: Node,
        callback: StateHolderWithNode<Node, Target>.() -> Unit
    ): GetValue<Target>

    fun <Node,Target> renderNode(
        node: Node,
        config: ShareConfig<Node,Target>,
        callback: StateHolderWithNode<Node, Target>.() -> Unit
    ): GetValue<Target>

    fun getParent(): Any?
}

interface StateHolderWithNode<T, F> : StateHolder<T, F> {
    val node: T
    val target: GetValue<F>
}