package org.wy.lib

sealed class Either<out A, out B>

data class Left<A>(val value: A) : Either<A, Nothing>()

data class Right<B>(val value: B) : Either<Nothing, B>()

val <A> A.left
    get() = Left(this)
val <B> B.right
    get() = Right(this)