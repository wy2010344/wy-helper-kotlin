package com.wy.layout

import org.wy.signal.Memo
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
    val positions: Map<Int, Float>,
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

    // memo 拆分：先判断是否有 grow 子节点，供 allowSizeFromChildren 使用
    private val hasGrowChildren = memo {
        inside.children.any { !convert.ignore(it) && convert.grow(it) > 0 }
    }

    // 将 grow 分配逻辑提取为方法，grow 分支和 else 分支共用
    private fun distributeGrow(
        insideSize: Float,
        flexChildren: List<T>,
        gap: Float,
        reverse: Boolean
    ): FlexInfo {
        var length = 0f
        val childLengths = mutableMapOf<Int, Float>()
        val positions = mutableMapOf<Int, Float>()
        val flexCount = flexChildren.size

        fun place(child: T, childLength: Float, childGap: Float) {
            val index = convert.index(child)
            childLengths[index] = childLength
            positions[index] = length
            length += childLength + childGap
        }

        val forEach: (action: (T) -> Unit) -> Unit =
            if (reverse) { action -> flexChildren.asReversed().forEach(action) } else flexChildren::forEach

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
        }

        return FlexInfo(childLengths, positions, length)
    }

    private val cache = object : Memo<FlexInfo>() {
        override fun get(old: FlexInfo?, inited: Boolean): FlexInfo {
            val gap = arg.gap
            val reverse = arg.reverse
            var length = 0f
            val childLengths = mutableMapOf<Int, Float>()
            val positions = mutableMapOf<Int, Float>()
            val children = inside.children

            // ignore=true 的元素不参与 flex 布局计算（不占空间、不参与 grow / gap 分配）
            val flexChildren = children.filter { !convert.ignore(it) }
            val flexCount = flexChildren.size

            // 只处理非 ignore 子节点：写入 positions / childLengths 并推进 length
            fun place(child: T, childLength: Float, childGap: Float) {
                val index = convert.index(child)
                childLengths[index] = childLength
                positions[index] = length
                length += childLength + childGap
            }

            val forEach: (action: (T) -> Unit) -> Unit =
                if (reverse) { action -> flexChildren.asReversed().forEach(action) } else flexChildren::forEach

            val directionFix = arg.directionJustify
            if (directionFix == DirectionJustify.grow) {
                if (hasGrowChildren()) {
                    // 有 grow 子节点：退为父约束 + grow 分配
                    return distributeGrow(inside.innerSize, flexChildren, gap, reverse)
                }
                // 无 grow 子节点：子容器撑起来
                forEach {
                    place(it, convert.outerSize(it), gap)
                }
                if (length > 0) {
                    length -= gap
                }

            } else {
                val insideSize = inside.innerSize
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
                    } else if (directionFix == DirectionJustify.end) {
                        length = remaing
                    } else if (directionFix == DirectionJustify.around) {
                        tGap = allRemaing / flexCount
                        length = tGap / 2
                    } else if (directionFix == DirectionJustify.between) {
                        if (flexCount > 1) {
                            tGap = allRemaing / (flexCount - 1)
                        } else if (flexCount == 1) {
                            val directionFixBetweenWhenOne = arg.directionFixBetweenWhenOne
                            if (directionFixBetweenWhenOne == DirectionFixBetweenWhenOne.center) {
                                length = allRemaing / 2
                            } else if (directionFixBetweenWhenOne == DirectionFixBetweenWhenOne.end) {
                                length = allRemaing
                            }
                        }
                    } else if (directionFix == DirectionJustify.evenly) {
                        tGap = allRemaing / (flexCount + 1)
                        length = tGap
                    }

                    forEach {
                        place(it, convert.outerSize(it), tGap)
                    }
                }
            }
            return FlexInfo(
                childLengths, positions, length
            )
        }

        override fun toString(): String {
            val mode = arg.directionJustify
            return "FlexLayout.cache[mode=$mode]"
        }
    }

    override fun childPosition(index: Int): Float {
        return cache().positions[index]
            ?: throw LayoutError("$index is ignored, its position is not available in FlexLayout")
    }

    override fun childSize(index: Int): Float {
        return cache().childLengths[index]
            ?: throw LayoutError("$index is ignored, its size is not available in FlexLayout")
    }

    override val sizeFromChildren: Float
        get() {
            if (arg.directionJustify == DirectionJustify.grow && !hasGrowChildren()) {
                return cache().length
            }
            return inside.innerSize
        }
    override val allowSizeFromChildren: Boolean
        //grow 模式下如果有子节点有 grow，退为父约束（由 distributeGrow 分配空间）
        get() = arg.directionJustify == DirectionJustify.grow && !hasGrowChildren()
}