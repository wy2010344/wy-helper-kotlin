package org.wy.lib.kanren

sealed class Term

data object Nil : Term() {
    override fun toString() = "()"
}

data class Str(val value: String) : Term()
fun String.term(): Str {
    return Str(this)
}
data class Num(val value: Number) : Term()


fun Number.term(): Num {
    return Num(this)
}

data class Cons(val car: Term, val cdr: Term) : Term()

infix fun Term.join(cdr: Term): Cons{
    return Cons(this,cdr)
}

class Var : Term() {
    val flag: String = "_${++uid}"
    override fun toString() = "{$flag}"
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)

    companion object {
        private var uid = 0
        fun fresh() = Var()
    }
}

abstract class Custom : Term() {
    abstract fun unify(other: Any?,substitution: Substitution):Substitution
}
