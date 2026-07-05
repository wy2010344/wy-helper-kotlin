package com.wy.mve

import org.wy.lib.GetValue


internal open class EachValue<Node, T, O>(
    val getSignal: GetValue<*>,
    parent: StateHolderI<Node>,
    parentContextIndex: Int
) : StateHolderI<Node>(
    parent,
    parentContextIndex
), EachTime<T> {
    private var out: O = null as O
    operator fun invoke(): O {
        return out
    }

    override var value = null as T
        get() {
            getSignal()
            return field
        }

    override var index: Int = 0
        get() {
            getSignal()
            return field
        }
}
