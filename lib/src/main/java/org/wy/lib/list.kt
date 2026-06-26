package org.wy.lib

fun <T> List<T>.forEachRight(callback:(T)-> Unit){
    this.asReversed().forEach(callback)
}