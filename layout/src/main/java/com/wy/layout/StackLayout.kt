package com.wy.layout

import org.wy.signal.memo
import kotlin.math.max


enum class AlignItem {
    center, start, end, stretch
}


interface Align {
    fun size(size: Float): Float
    fun position(size: Float, getSelfWidth: () -> Float): Float
}

interface StackChildConvert<T> {
    fun align(n: T): Align?
    fun outerSize(n: T): Float
}

interface StackObject<T> : LayoutFun<T>, StackChildConvert<T> {

    val alignItem: AlignItem
        get() = AlignItem.center

    //是否是外部提供固定尺寸
    val alignFix: Boolean
        get() = false

    override fun createLayout(o: LayoutInsideObject<T>): Layout {
        return StackLayout(this, o, this)
    }
}

class StackLayout<T>(
    private val arg: StackObject<T>,
    private val inside: LayoutInsideObject<T>,
    private val convert: StackChildConvert<T>
) : Layout {

    private val size = memo {
        if(arg.alignFix){
            return@memo inside.innerSize
        }
        var width = 0f
        inside.children.forEach {
            if (convert.align(it) == null) {
                width = max(width, convert.outerSize(it))
            }
        }
        return@memo width
    }


    private fun child(index: Int, isSize: Boolean): Float {
        val child = inside.children[index]
        val align = convert.align(child)
        if (align != null) {
            if (isSize) {
                return align.size(size())
            }
            return align.position(size()) {
                convert.outerSize(child)
            }
        }
        val alignItem = arg.alignItem
        if (alignItem == AlignItem.stretch) {
            if (isSize) {
                return size()
            }
            return 0f
        }
        if (isSize) {
            throw LayoutError("子节点应该有它自己的尺寸")
        }
        if (alignItem == AlignItem.start) {
            return 0f
        }
        if (alignItem == AlignItem.center) {
            return (size() - convert.outerSize(child)) / 2
        }
        if (alignItem == AlignItem.end) {
            return size() - convert.outerSize(child)
        }
        throw LayoutError("绝不应该抵达这里")
    }

    override val sizeFromChildren: Float
        get() = size()

    override fun childPosition(index: Int): Float {
        return child(index, false)
    }

    override fun childSize(index: Int): Float {
        return child(index, true)
    }

}