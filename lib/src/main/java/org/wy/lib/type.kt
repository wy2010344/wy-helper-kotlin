package org.wy.lib

import kotlin.reflect.KProperty

typealias Compare<T> = (a: T, b: T) -> Boolean
typealias GetValue<T> = () -> T
typealias QuoteValue<T> = (v: T) -> T
typealias SetValue<T> = (v: T) -> Unit
typealias EmptyFun = () -> Unit
typealias Fun<Arg,R> = (Arg)->R

fun <T> simpleNotEqual(a: T, b: T) = a != b
fun <T> simpleEqual(a: T, b: T) = a == b


operator fun <T> GetValue<T>.getValue(thisRef: Any?, prop: KProperty<*>) = this()

// ═══════════════════════════════════════════
// StoreRef (simple get/set container)
// ═══════════════════════════════════════════

interface StoreRef<T> {
    fun get(): T
    fun set(v: T): T

    var value
        get() = get()
        set(value) {
            set(value)
        }
}
