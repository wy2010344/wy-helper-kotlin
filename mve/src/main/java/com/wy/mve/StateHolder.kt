package com.wy.mve

import org.wy.lib.EmptyFun
import org.wy.lib.GetValue
import org.wy.lib.QuoteValue
import org.wy.lib.SetValue
import org.wy.signal.Memo

typealias Creater<Node, T, K, O> = StateHolder<Node>.(K, EachTime<T>) -> O

enum class DuplicateInfo { IGNORE, WARN, THROW }
interface StateHolder<Node> {
    fun <T> provide(context: Context<T>, value: T)

    fun <T> consume(context: Context<T>): T

    fun addDestroy(destroy: EmptyFun)

    val destroyed: Boolean

    fun <T, K, O> renderForEach(
        forEach: (callback: (K, T) -> GetValue<O>) -> Unit,
        duplicateInfo: DuplicateInfo = DuplicateInfo.IGNORE,
        creater: Creater<Node, T, K, O>,
    ): Memo<*>

    fun renderNode(
        node: Node,
        after: SetValue<List<Node>>? = null,
        callback: StateHolder<Node>.() -> Unit
    ): GetValue<List<Node>>

    fun getParent(): Node?
}