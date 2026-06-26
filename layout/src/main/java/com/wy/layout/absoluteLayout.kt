package com.wy.layout


private val absoluteLayoutObject = object : Layout{
    override val sizeFromChildren: Float
        get() = throw Error("没有默认值")

    override fun childPosition(index: Int): Float {
        return 0f
    }

    override fun childSize(index: Int): Float {
        throw Error("没有子节点的尺寸")
    }
}

private val absoluteLayoutFun:LayoutFun<Any> = object : LayoutFun<Any>{
    override fun createLayout(o: LayoutInsideObject<Any>): Layout {
        return absoluteLayoutObject
    }
}

fun <T> absoluteLayout(): LayoutFun<T>{
    return absoluteLayoutFun as LayoutFun<T>
}