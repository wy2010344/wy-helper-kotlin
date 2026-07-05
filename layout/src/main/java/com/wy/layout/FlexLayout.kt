package com.wy.layout

import org.wy.lib.forEachRight
import org.wy.signal.memo


enum class DirectionJustify {
    start, end, center, between, around, evenly
}

enum class DirectionFixBetweenWhenOne {
    start, center, end
}

interface FlexChildConvert<T> {
    fun index(n: T): Int
    fun grow(n: T): Float
    fun outerSize(n: T): Float
}

private data class FlexInfo(
    val childLengths: Map<Int, Float>,
    val list: List<Float>,
    val fixSize: Boolean,
    val length: Float
)

interface FlexObject<T> : LayoutFun<T>, FlexChildConvert<T> {
    val gap: Float
        get() = 0f

    val directionJustify: DirectionJustify
        get() = DirectionJustify.center

    val reverse: Boolean
        get() = false

    val directionFixBetweenWhenOne: DirectionFixBetweenWhenOne
        get() = DirectionFixBetweenWhenOne.center

    override fun createLayout(o: LayoutInsideObject<T>): Layout {
        return FlexLayout(this, o, this)
    }
}

class FlexLayout<T>(
    private val arg: FlexObject<T>,
    private val inside: LayoutInsideObject<T>,
    private val convert: FlexChildConvert<T>
) : Layout {
    private val cache = memo {
        val gap = arg.gap
        val reverse = arg.reverse
        var length = 0f
        val list = mutableListOf(0f)
        val childLengths = mutableMapOf<Int, Float>()
        val children = inside.children

        val forEach: (action: (T) -> Unit) -> Unit =
            if (reverse) children::forEachRight else children::forEach

        var insideSize = 0f
        var getInsideSize = true
        try {
            insideSize = inside.innerSize
        } catch (err: Throwable) {
            getInsideSize = false
        }

        if (getInsideSize) {
            val growIndex = mutableMapOf<Int, Float>()
            var growAll = 0f
            var totalLength = 0f
            children.forEach {
                val grow = convert.grow(it)
                if (grow > 0) {
                    growAll += grow
                    growIndex[convert.index(it)] = grow
                } else {
                    totalLength += convert.outerSize(it)
                }
            }

            if (growAll > 0) {
                val remaing = insideSize - (gap * children.size - gap) - totalLength
                forEach {
                    val index = convert.index(it)
                    val grow = growIndex[index] ?: 0f
                    val childLength = if (grow > 0) {
                        if (remaing > 0) remaing * grow / growAll else 0f
                    } else convert.outerSize(it)

                    childLengths[index] = childLength
                    length += childLength + gap
                    list.add(length)
                }
            } else {
                val directionFix = arg.directionJustify
                var tGap = gap
                val allRemaing = insideSize - totalLength
                val remaing = allRemaing - (gap * children.size - gap)
                if (directionFix == DirectionJustify.center) {
                    length = remaing / 2
                    list[0] = length
                } else if (directionFix == DirectionJustify.end) {
                    length = remaing
                    list[0] = length
                } else if (directionFix == DirectionJustify.around) {
                    tGap = allRemaing / children.size
                    length = tGap / 2
                    list[0] = length
                } else if (directionFix == DirectionJustify.between) {
                    if (children.size > 1) {
                        tGap = allRemaing / (children.size - 1)
                    } else if (children.size == 1) {
                        val directionFixBetweenWhenOne = arg.directionFixBetweenWhenOne
                        if (directionFixBetweenWhenOne == DirectionFixBetweenWhenOne.center) {
                            list[0] = allRemaing / 2
                        } else if (directionFixBetweenWhenOne == DirectionFixBetweenWhenOne.end) {
                            list[0] = allRemaing
                        }
                    }
                } else if (directionFix == DirectionJustify.evenly) {
                    tGap = allRemaing / (children.size + 1)
                    length = tGap
                    list[0] = length
                }

                forEach {
                    val childrenLength = convert.outerSize(it)
                    childLengths[convert.index(it)] = childrenLength
                    length += childrenLength + tGap
                    list.add(length)
                }
            }
        } else {
            forEach {
                val childLength = convert.outerSize(it)
                childLengths[convert.index(it)] = childLength
                length += childLength + gap
                list.add(length)
            }
            if (length > 0) {
                length -= gap
            }
        }
        if (reverse) {
            list.reverse()
        }
        return@memo FlexInfo(
            childLengths, list, getInsideSize, length
        )
    }

    override fun childPosition(index: Int): Float {
        return cache().list[index]
    }

    override fun childSize(index: Int): Float {
        return cache().childLengths[index] ?: 0f
    }

    override val sizeFromChildren: Float
        get() = if (cache().fixSize) throw Error("外部提供了尺寸") else cache().length
}