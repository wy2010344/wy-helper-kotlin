package org.wy.kanren

import org.wy.lib.kanren.*
import kotlin.math.abs

fun demoUnify() {
    println("--- eq: x = 42 ---")
    val x = Var.fresh()
    for (sub in (x eq 42.term())(Nil))
        println("x = ${(x walk sub)}")
}

fun demoOr() {
    println("\n--- or: x = 1 | x = 2 ---")
    val x = Var.fresh()
    for (sub in (x eq 1.term() or (x eq 2.term()))(Nil))
        println("x = ${(x walk sub)}")
}

fun demoAnd() {
    println("\n--- and: x = 1 & y = 2 ---")
    val x = Var.fresh()
    val y = Var.fresh()
    for (sub in (x eq 1.term() and (y eq 2.term()))(Nil))
        println("x = ${(x walk sub)}, y = ${(y walk sub)}")
}

fun demoAppendForward() {
    println("\n--- append([1,2], [3,4], z) ---")
    val z = Var.fresh()
    val a = 1.term() join 2.term() join Nil
    val b = 3.term() join 4.term() join Nil
    for (sub in append(a, b, z)(Nil).take(1))
        println("z = ${(z walk sub)}")
}

fun demoAppendReverse() {
    println("\n--- append(q, [3], [1,2,3]) ---")
    val q = Var.fresh()
    val c = 1.term() join 2.term() join 3.term() join Nil
    val b = 3.term() join Nil
    for (sub in append(q, b, c)(Nil).take(1))
        println("q = ${(q walk sub)}")
}

fun demoAppendMiddle() {
    println("\n--- append([1], r, [1,2,3]) ---")
    val r = Var.fresh()
    val a = 1.term() join Nil
    val c = 1.term() join 2.term() join 3.term() join Nil
    for (sub in append(a, r, c)(Nil).take(1))
        println("r = ${(r walk sub)}")
}

fun demoAppendSplit() {
    println("\n--- append(p, q, [1,2,3]) ---")
    val p = Var.fresh()
    val q = Var.fresh()
    val c = 1.term() join 2.term() join 3.term() join Nil
    for (sub in append(p, q, c)(Nil))
        println("p = ${(p walk sub)}, q = ${(q walk sub)}")
}

class EqStr(val s: String) : Custom() {
    override fun unify(other: Any?, substitution: Substitution) =
        if (other is EqStr && other.s == s) substitution to true
        else substitution to false
}

fun demoCustomUnify() {
    println("\n--- custom: EqStr(\"a\") == EqStr(\"a\") ---")
    for (sub in (EqStr("a") eq EqStr("a"))(Nil))
        println("unify success")
    println("--- custom: EqStr(\"a\") == EqStr(\"b\") ---")
    val r = (EqStr("a") eq EqStr("b"))(Nil).toList()
    if (r.isEmpty()) println("EqStr(a) != EqStr(b) -- expected")
}

class Near(val n: Number, val tolerance: Number) : Custom() {
    override fun unify(other: Any?, substitution: Substitution): Pair<Substitution, Boolean> {
        if (other is Num) {
            val diff = abs(n.toDouble() - other.value.toDouble())
            return substitution to (diff <= tolerance.toDouble())
        }
        if (other is Near) {
            val total = tolerance.toDouble() + other.tolerance.toDouble()
            return substitution to (abs(n.toDouble() - other.n.toDouble()) <= total)
        }
        return substitution to false
    }
}

fun demoCustomNear() {
    println("\n--- near(10, tol=1) == 10.5 ---")
    for (sub in (Near(10, 1) eq 10.5.term())(Nil))
        println("10.5 is near 10 -- success")
    println("--- near(10, tol=1) == 12 ---")
    val r = (Near(10, 1) eq 12.term())(Nil).toList()
    if (r.isEmpty()) println("12 is NOT near 10 -- expected")
}

fun demoFailSucceed() {
    println("\n--- fail & succeed ---")
    println("succeed: ${succeed()(Nil).toList().size} result(s)")
    println("fail: ${fail()(Nil).toList().size} result(s)")
}

fun demoAllAny() {
    println("\n--- all(x=1, y=2, z=3) ---")
    val x = Var.fresh()
    val y = Var.fresh()
    val z = Var.fresh()
    for (sub in all(x eq 1.term(), y eq 2.term(), z eq 3.term())(Nil))
        println("x=${(x walk sub)} y=${(y walk sub)} z=${(z walk sub)}")

    println("--- any(x=1, x=2, x=3) ---")
    val a = Var.fresh()
    for (sub in any(a eq 1.term(), a eq 2.term(), a eq 3.term())(Nil))
        println("a = ${(a walk sub)}")
}

fun main() {
    demoUnify()
    demoOr()
    demoAnd()
    demoAppendForward()
    demoAppendReverse()
    demoAppendMiddle()
    demoAppendSplit()
    demoCustomUnify()
    demoCustomNear()
    demoFailSucceed()
    demoAllAny()
}
