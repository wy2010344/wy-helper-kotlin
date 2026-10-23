package org.wy.signal


import kotlin.collections.iterator
import org.wy.lib.*
import kotlin.reflect.KProperty

operator fun <T> Memo<T>.getValue(thisRef: Any?, prop: KProperty<*>): T = this()

private val stackMemos = mutableListOf<Memo<*>>()

internal fun checkMemoStack(){
    if(stackMemos.size>0){
        throw Error("之前有memo没有正常退出--${stackMemos.size}")
    }
}

internal fun resetStackMemos(){
    stackMemos.clear()
}
//有一种可能，重入循环
abstract class Memo<T> : GetValue<T> {
    abstract fun get(old: T?, inited: Boolean): T
    val afters = mutableSetOf<SetValue<T>>()
    private val relays = mutableMapOf<GetValue<*>, Any?>()
    private var lastValue: T? = null
    private var inited = false
    private var stateVersion: Any? = null
    private var listenerVersion: Any? = null


    private fun checkEnter() {
        if (stackMemos.contains(this)) {
            throw Error("重复进入...${this}")
        }
        stackMemos.add(0, this)
    }

    private fun checkLeave() {
        val last = stackMemos.removeAt(0)
        if (last != this) {
            throw Error("出入并不匹配")
        }
    }

    override fun invoke(): T {
        checkEnter()
        // 异常安全：无论 get() 内部抛什么，栈都必须回到进入前的状态，
        // 否则残留条目会让后续所有 memo 调用连锁崩溃（重复进入 / 出入不匹配）。
        var left = false
        try {
            G.callGet = true
            if (stateVersion == G.stateVersion) {
                val cf = G.currentFun
                if (cf != null && listenerVersion != cf) {
                    listenerVersion = cf
                    val oldRelay = G.currentRelay
                    G.currentRelay = null
                    try {
                        for ((k, _) in relays) k()
                    } finally {
                        G.currentRelay = oldRelay
                    }
                }
                G.currentRelay?.let { relay -> relay[this] = lastValue }
                checkLeave()
                left = true
                @Suppress("UNCHECKED_CAST")
                return lastValue as T
            }
            listenerVersion = null
            stateVersion = G.stateVersion

            var shouldAfter = false
            val oldRelay = G.currentRelay
            G.currentRelay = null
            try {
                if (inited) {
                    if (relayChanged(relays)) {
                        val v = memoGetImpl(lastValue, inited)
                        if (v != lastValue) {
                            lastValue = v
                            shouldAfter = true
                        }
                    }
                } else {
                    lastValue = memoGetImpl(null, false)
                    inited = true
                    shouldAfter = true
                }
            } finally {
                G.currentRelay = oldRelay
            }
            G.currentRelay?.let { relay -> relay[this] = lastValue }
            //必须在after之前
            checkLeave()
            left = true
            if (shouldAfter) {
                afters.forEach { it(lastValue as T) }
            }
            @Suppress("UNCHECKED_CAST")
            return lastValue as T
        } finally {
            if (!left) {
                // 异常路径：静默出栈（不掩盖原始异常）；正常路径不会走到这里
                runCatching { checkLeave() }
            }
        }
    }

    private fun memoGetImpl(
        lastValue: T?,
        inited: Boolean
    ): T {
        relays.clear()
        val oldRelay = G.currentRelay
        G.currentRelay = relays
        try {
            return get(lastValue, inited)
        } finally {
            G.currentRelay = oldRelay
        }
    }
}


fun <T> memo(get: GetValue<T>): Memo<T> = get as? Memo
    ?: object : Memo<T>() {
        override fun get(old: T?, inited: Boolean): T {
            return get()
        }
    }

private fun relayChanged(relays: Map<GetValue<*>, Any?>): Boolean {
    for ((get, old) in relays) {
        val v = get()
        if (v != old) return true
    }
    return false
}
