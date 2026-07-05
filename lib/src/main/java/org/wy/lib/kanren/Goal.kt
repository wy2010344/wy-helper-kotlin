package org.wy.lib.kanren

typealias Goal = (Substitution) -> Sequence<Substitution>

infix fun Goal.and(other: Goal): Goal = { sub ->
    sequence {
        for (s1 in this@and(sub)) {
            yieldAll(other(s1))
        }
    }
}

infix fun Goal.or(other: Goal): Goal = { sub ->
    sequence {
        yieldAll(this@or(sub))
        yieldAll(other(sub))
    }
}

infix fun Term.eq(b: Term): Goal= {sub ->
    sequence {
        val (sub1, ok) = unify(this@eq, b, sub)
        if (ok) yield(sub1)
    }
}

fun fail(): Goal = { emptySequence() }

fun succeed(): Goal = { sequenceOf(it) }

fun all(vararg goals: Goal): Goal = goals.reduce { a, b -> a and b }

fun any(vararg goals: Goal): Goal = goals.reduce { a, b -> a or b }

fun append(a: Term, b: Term, c: Term): Goal = { sub ->
    sequence {
        val h = Var.fresh()
        val t = Var.fresh()
        val res = Var.fresh()
        val body: Goal =
            ((a eq Nil) and (b eq c)) or ((a eq (h join t)) and (c eq (h join res)) and append(
                t,
                b,
                res
            ))
        yieldAll(body(sub))
    }
}
