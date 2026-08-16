package com.wy.layout

import org.wy.lib.forEachRight
import org.wy.signal.memo


enum class DirectionJustify {
    start, end, center, between, around, evenly,

    //由子节点撑起来
    grow
}

enum class DirectionFixBetweenWhenOne {
    start, center, end
}

interface FlexChildConvert<T> {
    fun index(n: T): Int
    fun grow(n: T): Float
    fun outerSize(n: T): Float
    fun ignore(n:T): Boolean
}

private data class FlexInfo(
    val childLengths: Map<Int, Float>,
    val list: List<Float>,
    val length: Float
)

interface FlexObject<T> : LayoutFun<T>, FlexChildConvert<T> {
    val gap: Float
        get() = 0f

    val directionJustify: DirectionJustify
        get() = DirectionJustify.grow

    val reverse: Boolean
        get() = false

    val directionFixBetweenWhenOne: DirectionFixBetweenWhenOne
        get() = DirectionFixBetweenWhenOne.center

    override fun invoke(o: LayoutInsideObject<T>): Layout {
        return FlexLayout(this, o, this)
    }
}

/**
 * 使用try...catch会陷入无限死循环。。。
 */
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

        // ignore=true 的元素不参与 flex 布局计算（不占空间、不参与 grow / gap 分配），
        // 由元素自身提供位置与尺寸。这里仍为其填入自身尺寸与 position 槽位，保证 layoutIndex 对齐。
        val flexChildren = children.filter { !convert.ignore(it) }
        val flexCount = flexChildren.size

        // 处理一个子节点：被 ignore 的元素只占槽位、不推进长度，并返回其自身尺寸
        fun place(child: T, childLength: Float, childGap: Float) {
            val index = convert.index(child)
            if (convert.ignore(child)) {
                childLengths[index] = convert.outerSize(child)
            } else {
                childLengths[index] = childLength
                length += childLength + childGap
            }
            list.add(length)
        }

        val forEach: (action: (T) -> Unit) -> Unit =
            if (reverse) children::forEachRight else children::forEach

        val directionFix = arg.directionJustify
        if (directionFix == DirectionJustify.grow) {
            forEach {
                place(it, convert.outerSize(it), gap)
            }
            if (length > 0) {
                length -= gap
            }

        } else {
            val insideSize = inside.innerSize
            //外部提供了尺寸
            val growIndex = mutableMapOf<Int, Float>()
            var growAll = 0f
            var totalLength = 0f
            flexChildren.forEach {
                val index = convert.index(it)
                val grow = convert.grow(it)
                if (grow > 0) {
                    growAll += grow
                    growIndex[index] = grow
                } else {
                    totalLength += convert.outerSize(it)
                }
            }

            if (growAll > 0) {
                val remaing = insideSize - (gap * flexCount - gap) - totalLength
                forEach {
                    val index = convert.index(it)
                    val grow = growIndex[index] ?: 0f
                    val childLength = if (grow > 0) {
                        if (remaing > 0) remaing * grow / growAll else 0f
                    } else convert.outerSize(it)

                    place(it, childLength, gap)
                }
            } else {
                var tGap = gap
                val allRemaing = insideSize - totalLength
                val remaing = allRemaing - (gap * flexCount - gap)
                if (directionFix == DirectionJustify.center) {
                    length = remaing / 2
                    list[0] = length
                } else if (directionFix == DirectionJustify.end) {
                    length = remaing
                    list[0] = length
                } else if (directionFix == DirectionJustify.around) {
                    tGap = allRemaing / flexCount
                    length = tGap / 2
                    list[0] = length
                } else if (directionFix == DirectionJustify.between) {
                    if (flexCount > 1) {
                        tGap = allRemaing / (flexCount - 1)
                    } else if (flexCount == 1) {
                        val directionFixBetweenWhenOne = arg.directionFixBetweenWhenOne
                        if (directionFixBetweenWhenOne == DirectionFixBetweenWhenOne.center) {
                            list[0] = allRemaing / 2
                        } else if (directionFixBetweenWhenOne == DirectionFixBetweenWhenOne.end) {
                            list[0] = allRemaing
                        }
                    }
                } else if (directionFix == DirectionJustify.evenly) {
                    tGap = allRemaing / (flexCount + 1)
                    length = tGap
                    list[0] = length
                }

                forEach {
                    place(it, convert.outerSize(it), tGap)
                }
            }
        }
        if (reverse) {
            list.reverse()
        }
        return@memo FlexInfo(
            childLengths, list, length
        )
    }

    override fun childPosition(index: Int): Float {
        //两个flex嵌套，依赖孙节点的尺寸。但父节点问子节点尺寸，子节点先问父节点自己尺寸，就会循环
        return cache().list[index]
    }

    override fun childSize(index: Int): Float {
        return cache().childLengths[index] ?: 0f
    }

    override val sizeFromChildren: Float
        get() {
            if (arg.directionJustify == DirectionJustify.grow) {
                return cache().length
            }
            return inside.innerSize
        }
    override val allowSizeFromChildren: Boolean
        //必须阻断，否则进入死循环
        get() = arg.directionJustify == DirectionJustify.grow
}