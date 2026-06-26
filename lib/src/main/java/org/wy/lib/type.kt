package org.wy.lib

typealias Compare<T> = (a: T, b: T) -> Boolean
typealias GetValue<T> = () -> T
typealias QuoteValue<T> = (v: T) -> T
typealias SetValue<T> = (v: T) -> Unit
typealias EmptyFun = () -> Unit

fun <T> simpleNotEqual(a: T, b: T) = a != b
fun <T> simpleEqual(a: T, b: T) = a == b

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
