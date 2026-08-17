package org.wy.lib

fun <T> List<T>.forEachRight(callback:(T)-> Unit){
    this.asReversed().forEach(callback)
}

fun <T> List<T>.contact(v:T): MutableList<T> {
    return mutableListOf<T>().also {
        it.addAll(this)
        it.add(v)
    }
}