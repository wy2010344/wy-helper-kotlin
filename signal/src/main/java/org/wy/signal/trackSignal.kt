package org.wy.signal

import org.wy.lib.EmptyFun

abstract class TrackSignal<T>{
    abstract fun get(old: T?, inited: Boolean) : T
    protected open fun set(v: T, oldV: T?, inited: Boolean) : EmptyFun?{
        return null
    }
    var disabled = false
        private set
    private var inited = false
    private var lastValue: T? = null
    private var destroy: EmptyFun = {}

    init {
        val deps = G.onWorkBatch?.deps
        if (deps != null) {
            // 批次执行中（受观察）：加入当前 onWorkBatch 的 deps
            deps.add(this)
        } else {
            // 非执行期创建：启动批次并加入 currentBatch 的 deps，随后续批次同步求值
            beginCurrentBatch()
            G.currentBatch.deps.add(this)
        }
    }

    fun addFun() {
        if (disabled) return
        val prev = G.currentFun
        G.currentFun = this
        // 只在 get 阶段保持追踪；set 必须在非追踪上下文执行（对齐 TS 原版）：
        // 否则 set 内读 signal/memo 会产生额外订阅、memo 重注入等原版不存在的副作用。
        // finally 只复位追踪状态，错误继续向上抛。
        val value = try {
            get(lastValue, inited)
        } finally {
            G.currentFun = prev
        }

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
        val prev = G.currentFun
        G.currentFun = this
        try {
            return callback()
        } finally {
            G.currentFun = prev
        }
    }
    fun dispose() {
        destroy()
        disabled = true
    }
}