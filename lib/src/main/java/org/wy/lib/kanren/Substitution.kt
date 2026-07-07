package org.wy.lib.kanren

typealias Substitution = Term

fun find(v: Var, sub: Substitution): Cons? {
    var s: Term = sub
    while (s is Cons) {
        val kv = s.car
        if (kv is Cons) {
            val key = kv.car
            if (key is Var && key === v) return kv
        }
        s = s.cdr
    }
    return null
}


infix fun Term.walk(sub: Substitution): Term {
    val v = this
    if (v is Var) {
        val binding = find(v, sub) ?: return v
        return binding.cdr walk sub
    }
    if (v is Cons) return Cons(
        v.car walk sub,
        v.cdr walk sub
    )
    return v
}

infix fun Substitution.query(v: Term): Term {
    return v walk this
}

fun extend(v: Var, value: Term, parent: Substitution): Substitution = v join value join  parent


fun unify(a: Term, b: Term, sub: Substitution): Substitution {
    val u = a walk sub
    val v = b walk sub
    if (u == v) return sub
    if (u is Var) return extend(u, v, sub)
    if (v is Var) return extend(v, u, sub)
    if (u is Cons && v is Cons) {
        val sub1 = unify(u.car, v.car, sub)
        return unify(u.cdr, v.cdr, sub1)
    }
    if (u is Custom) {
        return u.unify(v, sub)
    }
    if (v is Custom) {
        return v.unify(u, sub)
    }
    throw UnifyError("can't unify ${u} to ${v}")
}

class UnifyError(message: String?=null): Error(message)