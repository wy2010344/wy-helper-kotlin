package org.wy.signal

import org.wy.lib.EmptyFun

abstract class TrackSignal<T>{
    abstract fun get(old: T?, inited: Boolean) : T
    protected open fun set(v: T, oldV: T?, inited: Boolean) : EmptyFun?{
        return null
    }
    private var disabled = false
    private var inited = false
    private var lastValue: T? = null
    private var destroy: EmptyFun = {}

    init {
        val deps = G.onWorkBatch?.deps ?: G.currentBatch.deps
        deps.add(this)
    }

    fun addFun() {
        if (disabled) return
        G.currentFun = this
        val value = get(lastValue, inited)
        G.currentFun = null

        if (inited) {
            if (value != lastValue) {
                destroy()
                destroy = set(value, lastValue, inited) ?: {}
                lastValue = value
            }
        } else {
            destroy = set(value, null, false) ?: {}
            lastValue = value
            inited = true
        }
    }

    fun<T> collect(callback:()->T):T{
        if(G.currentFun!=null){
            throw Error("禁止在受观察处发起...")
        }
        G.currentFun = this
        val v = callback()
        G.currentFun = null
        return v
    }
    fun dispose() {
        destroy()
        disabled = true
    }
}