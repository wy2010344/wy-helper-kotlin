package com.wy.mve

sealed class ValueOrGetList<T>
class Value<T>(val value: T) : ValueOrGetList<T>()
abstract class GetList<T>() : ValueOrGetList<T>() {
    abstract fun getList(): List<ValueOrGetList<T>>
}


fun <Node> purifyList(children: List<ValueOrGetList<Node>>, list: MutableList<Node>) {
    children.forEach {
        when (it) {
            is Value<Node> ->
                list.add(it.value)
            is GetList<Node> -> {
                purifyList(it.getList(), list)
            }
        }
    }
}