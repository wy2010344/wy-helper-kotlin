package org.wy.signal


import kotlin.collections.iterator
import org.wy.lib.*

private val stackMemos=mutableListOf<Memo<*>>()

//有一种可能，重入循环
abstract class Memo<T> : GetValue<T> {
    abstract fun get(old: T?, inited: Boolean): T
    val afters = mutableSetOf<SetValue<T>>()
    private val relays = mutableMapOf<GetValue<*>, Any?>()
    private var lastValue: T? = null
    private var inited = false
    private var stateVersion: Any? = null
    private var listenerVersion: Any? = null


    private fun checkEnter(){
        if(stackMemos.contains(this)){
            throw Error("重复进入...${this}")
        }
        stackMemos.add(0,this)
    }
    private fun checkLeave(){
        val last=stackMemos.removeAt(0)
        if(last!=this){
            throw Error("出入并不匹配")
        }
    }

    override fun invoke(): T {

        checkEnter()
        G.callGet = true
        if (stateVersion == G.stateVersion) {
            val cf = G.currentFun
            if (cf != null && listenerVersion != cf) {
                listenerVersion = cf
                for ((k, _) in relays) k()
            }
            G.currentRelay?.let { relay -> relay[this] = lastValue }
            checkLeave()
            @Suppress("UNCHECKED_CAST")
            return lastValue as T
        }
        listenerVersion = null
        stateVersion = G.stateVersion

        var shouldAfter = false
        val oldRelay = G.currentRelay
        G.currentRelay = null

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
        G.currentRelay = oldRelay
        G.currentRelay?.let { relay -> relay[this] = lastValue }

        if (shouldAfter) {
            afters.forEach { it(lastValue as T) }
        }
        checkLeave()
        @Suppress("UNCHECKED_CAST")
        return lastValue as T
    }

    private fun memoGetImpl(
        lastValue: T?,
        inited: Boolean
    ): T {
        relays.clear()
        G.currentRelay = relays
        val v = get(lastValue, inited)
        G.currentRelay = null
        return v
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
