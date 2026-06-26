package com.wy.layout


interface Layout {

    val sizeFromChildren: Float
    fun childSize(index: Int): Float
    fun childPosition(index: Int): Float
}


interface LayoutInsideObject<T> {
    val children: List<T>

    val innerSizeForLayout: Float
}

interface LayoutFun<T> {
    fun createLayout(o: LayoutInsideObject<T>): Layout
}