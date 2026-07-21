package com.wy.layout

import org.wy.lib.GetValue


private val absoluteLayoutObject = object : Layout{
    override val sizeFromChildren: Float
        get() = throw LayoutError("没有默认值")

    override fun childPosition(index: Int): Float {
        return 0f
    }

    override fun childSize(index: Int): Float {
        throw LayoutError("没有子节点的尺寸")
    }

    override val allowSizeFromChildren: Boolean
        get() = false
}

private val absoluteLayoutFun:LayoutFun<Any> = object : LayoutFun<Any>{
    override fun invoke(o: LayoutInsideObject<Any>): Layout {
        return absoluteLayoutObject
    }
}

fun <T> absoluteLayout(): LayoutFun<T>{
    return absoluteLayoutFun as LayoutFun<T>
}