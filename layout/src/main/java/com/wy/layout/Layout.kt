package com.wy.layout


interface Layout {

    val sizeFromChildren: Float
    fun childSize(index: Int): Float
    fun childPosition(index: Int): Float
    val allowSizeFromChildren: Boolean
}


interface LayoutInsideObject<T> {
    val children: List<T>
    val innerSize: Float
}

typealias LayoutFun<T> = (o: LayoutInsideObject<T>) -> Layout



class LayoutError(message:String?): Error(message)