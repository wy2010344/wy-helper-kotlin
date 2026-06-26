package org.wy.signal

import org.wy.lib.Compare
import org.wy.lib.QuoteValue
import org.wy.lib.StoreRef
import org.wy.lib.simpleNotEqual
import kotlin.reflect.KProperty

interface OneSetStoreRef<T> {
    fun get(): T
    val value
        get() = get()

    fun getOnlySet(message: String = "already get this set for only use"): QuoteValue<T>
}

private class Signal<T>(
    initial: T,
    private val shouldChange: Compare<T> = ::simpleNotEqual
) {
    private var storage: T = initial
    private val listeners = mutableSetOf<TrackSignal<*>>()

    fun get(): T {
        val v = storage
        G.currentRelay?.let { relay ->
            relay[::get] = v
        }
        G.currentFun?.let { listeners.add(it) }
        return v
    }

   private fun didSet(v: T): T {
        if (G.onWorkBatch != null && listeners.isNotEmpty()) {
            throw Error("计算期间不允许修改值")
        }
        if (shouldChange(v, storage)) {
            if (G.callGet) {
                G.stateVersion = Any()
                G.callGet = false
            }
            storage = v
            if (listeners.isNotEmpty()) {
                listeners.forEach { addListener(it) }
                listeners.clear()
                beginCurrentBatch()
            }
        }
        return v
    }

    private var onlySet = false
    fun getOnlySet(message: String = "already get this set for only use"): QuoteValue<T> {
        if (onlySet) throw IllegalStateException(message)
        onlySet = true
        return ::didSet
    }
}

private fun addListener(listener: TrackSignal<*>) {
    G.currentBatch.listeners.add(listener)
}


// ═══════════════════════════════════════════
// createSignal / createLateSignal
// ═══════════════════════════════════════════

fun <T> createSignal(value: T, shouldChange: Compare<T> = ::simpleNotEqual): StoreRef<T> {
    val s = Signal(value, shouldChange)
    val onlySet = s.getOnlySet()
    return object : StoreRef<T> {
        override fun get() = s.get()
        override fun set(v: T) = onlySet(v)
    }
}

fun <T> createLateSignal(value: T, shouldChange: Compare<T> = ::simpleNotEqual): OneSetStoreRef<T> {
    val s = Signal(value, shouldChange)
    return object : OneSetStoreRef<T> {
        override fun get() = s.get()
        override fun getOnlySet(message: String) = s.getOnlySet(message)
    }
}


// ═══════════════════════════════════════════
// Property Delegates
// ═══════════════════════════════════════════

operator fun <T> StoreRef<T>.getValue(thisRef: Any?, prop: KProperty<*>) = get()
operator fun <T> StoreRef<T>.setValue(thisRef: Any?, prop: KProperty<*>, v: T) {
    set(v)
}
