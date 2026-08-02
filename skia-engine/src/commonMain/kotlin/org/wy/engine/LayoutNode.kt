package org.wy.engine

import com.wy.layout.Align
import com.wy.layout.Layout
import com.wy.layout.LayoutInsideObject
import com.wy.mve.StateHolder
import org.wy.engine.layout.LayoutDirection
import org.wy.engine.layout.absoluteLayoutDirection
import org.wy.lib.GetValue
import org.wy.signal.Memo
import org.wy.signal.memo
import kotlin.math.max

enum class StartEnd {
    start, end
}

data class LayoutSize(
    val value: Float,
    val fromInside: Boolean
)

/**
 * [LayoutSize] + 方向：指定某个方向上的一种尺寸（宽或高），
 * 用于只给一个方向、其余按比例推算的场景（如 [org.wy.engine.ImageNode]）。
 */
data class LayoutSizeDirection(
    val direction: Direction,
    val value: Float,
    val fromInside: Boolean
)

val layoutSize0 = LayoutSize(0f, true)

enum class SizeFrom {
    inside, outside
}

private fun findLayoutList(n: Node, list: MutableList<LayoutNode>) {
    n.children.forEach {
        if (it is LayoutNode) {
            list.add(it)
        } else {
            findLayoutList(it, list)
        }
    }
}

open class LayoutNode(context: StateHolder<Node,List<Node>>?) : Node(context) {
    open val layout: LayoutDirection
        get() = absoluteLayoutDirection


    val layoutX: GetValue<Layout> = createLayout(Direction.x)
    val layoutY: GetValue<Layout> = createLayout(Direction.y)


    override fun acceptHit(x: Float, y: Float): Boolean {
        val w = outerSize(Direction.x)
        val h = outerSize(Direction.y)
        return x > 0 && x < w && y > 0 && y < h
    }

    val layoutParent: LayoutNode? = run {
        var p: Node? = parent
        while (p != null) {
            if (p is LayoutNode) {
                return@run p
            }
            p = p.parent
        }
        return@run null
    }

    var layoutIndex: Int = 0
        internal set
        get() {
            if(hide){
                throw Error("已经隐藏不再显示")
            }
            layoutParent?.layoutChildren
            return field
        }


    val getLayoutChildren = object : Memo<List<LayoutNode>>() {
        override fun get(old: List<LayoutNode>?, inited: Boolean): List<LayoutNode> {
            val list = mutableListOf<LayoutNode>()
            findLayoutList(this@LayoutNode, list)
            return list
        }

        init {
            afters.add { list ->
                var i = 0
                list.forEach {
                    it.layoutIndex = i++
                }
            }
        }
    }

    val layoutChildren: List<LayoutNode>
        get() = getLayoutChildren()

    open fun argSize(direction: Direction): LayoutSize = layoutSize0


    open val argWidth: LayoutSize
        get() = argSize(Direction.x)
    open val argHeight: LayoutSize
        get() = argSize(Direction.y)


    open fun argPadding(direction: Direction, startEnd: StartEnd) = 0f
    open fun argPaddingInline(startEnd: StartEnd) = argPadding(Direction.x, startEnd)

    open fun argPaddingBlock(startEnd: StartEnd) = argPadding(Direction.y, startEnd)

    open val paddingInlineStart
        get() = argPaddingInline(StartEnd.start)
    open val paddingInlineEnd
        get() = argPaddingInline(StartEnd.end)
    open val paddingBlockStart
        get() = argPaddingBlock(StartEnd.start)
    open val paddingBlockEnd
        get() = argPaddingBlock(StartEnd.end)

}

fun LayoutNode.padding(direction: Direction, startEnd: StartEnd) = when (direction) {
    Direction.x -> when (startEnd) {
        StartEnd.start -> paddingInlineStart
        StartEnd.end -> paddingInlineEnd
    }

    Direction.y -> when (startEnd) {
        StartEnd.start -> paddingBlockStart
        StartEnd.end -> paddingBlockEnd
    }
}

fun LayoutNode.size(direction: Direction) = when (direction) {
    Direction.x -> argWidth
    Direction.y -> argHeight
}

fun LayoutNode.outerSize(direction: Direction): Float {
    val s = size(direction)
    if (s.fromInside) {
        return max(
            0f,
            s.value + padding(direction, StartEnd.start) + padding(direction, StartEnd.end)
        )
    }
    return max(0f, s.value)
}

fun LayoutNode.innerSize(direction: Direction): Float {
    val s = size(direction)
    if (s.fromInside) {
        return max(0f, s.value)
    }
    return max(0f, s.value - padding(direction, StartEnd.start) - padding(direction, StartEnd.end))
}

val LayoutNode.outerWidth
    get() = outerSize(Direction.x)
val LayoutNode.outerHeight
    get() = outerSize(Direction.y)
val LayoutNode.innerWidth
    get() = innerSize(Direction.x)
val LayoutNode.innerHeight
    get() = innerSize(Direction.y)

fun LayoutNode.fillInnerRect(canvas: PlatformCanvas, color: Int = rgba(0, 0, 0)) {
    canvas.fillRect(paddingInlineStart, paddingBlockStart, innerWidth, innerHeight, color)
}

fun LayoutNode.fillOuterRect(
    canvas: PlatformCanvas,
    color: Int = rgba(0, 0, 0)
) {
    canvas.fillRect(0f, 0f, outerWidth, outerHeight, color)
}


fun LayoutNode.strokeInnerRect(
    canvas: PlatformCanvas,
    color: Int = rgba(0, 0, 0),
    strokeWidth: Float = 1f
) {
    canvas.strokeRect(
        paddingInlineStart,
        paddingBlockStart,
        innerWidth,
        innerHeight,
        color,
        strokeWidth
    )
}

fun LayoutNode.strokeOuterRect(
    canvas: PlatformCanvas,
    color: Int = rgba(0, 0, 0), strokeWidth: Float = 1f
) {
    canvas.strokeRect(0f, 0f, outerWidth, outerHeight, color, strokeWidth)
}

fun LayoutNode.fillOuterRoundRect(
    canvas: PlatformCanvas,
    radius: Float,
    color: Int = rgba(0, 0, 0)
) {
    canvas.fillRoundRect(0f, 0f, outerWidth, outerHeight, radius, color)
}

fun LayoutNode.fillInnerRoundRect(
    canvas: PlatformCanvas,
    radius: Float,
    color: Int = rgba(0, 0, 0)
) {
    canvas.fillRoundRect(
        paddingInlineStart, paddingBlockStart, innerWidth, innerHeight, radius, color
    )
}

fun LayoutNode.strokeOuterRoundRect(
    canvas: PlatformCanvas,
    radius: Float,
    color: Int = rgba(0, 0, 0),
    strokeWidth: Float = 1f
) {
    canvas.strokeRoundRect(0f, 0f, outerWidth, outerHeight, radius, color, strokeWidth)
}

fun LayoutNode.strokeInnerRoundRect(
    canvas: PlatformCanvas,
    radius: Float,
    color: Int = rgba(0, 0, 0),
    strokeWidth: Float = 1f
) {
    canvas.strokeRoundRect(
        paddingInlineStart, paddingBlockStart, innerWidth, innerHeight, radius, color, strokeWidth
    )
}

fun LayoutNode.fillOuterOval(
    canvas: PlatformCanvas,
    color: Int = rgba(0, 0, 0)
) {
    canvas.fillOval(0f, 0f, outerWidth, outerHeight, color)
}

fun LayoutNode.fillInnerOval(
    canvas: PlatformCanvas,
    color: Int = rgba(0, 0, 0)
) {
    canvas.fillOval(paddingInlineStart, paddingBlockStart, innerWidth, innerHeight, color)
}

fun LayoutNode.strokeOuterOval(
    canvas: PlatformCanvas,
    color: Int = rgba(0, 0, 0),
    strokeWidth: Float = 1f
) {
    canvas.strokeOval(0f, 0f, outerWidth, outerHeight, color, strokeWidth)
}

fun LayoutNode.strokeInnerOval(
    canvas: PlatformCanvas,
    color: Int = rgba(0, 0, 0),
    strokeWidth: Float = 1f
) {
    canvas.strokeOval(
        paddingInlineStart, paddingBlockStart, innerWidth, innerHeight, color, strokeWidth
    )
}

/**
 * 沿节点外缘画一圈描边，[gap] 为离外缘的间距（负值表示向内收缩，
 * 常用于焦点环：`strokeOuterRing(canvas, 2f, radius, ACCENT, 2f)`）。
 */
fun LayoutNode.strokeOuterRing(
    canvas: PlatformCanvas,
    gap: Float,
    radius: Float,
    color: Int = rgba(0, 0, 0),
    strokeWidth: Float = 1f
) {
    canvas.strokeRoundRect(
        -gap, -gap, outerWidth + gap * 2f, outerHeight + gap * 2f, radius, color, strokeWidth
    )
}


fun LayoutNode.sizeFromParent(direction: Direction): LayoutSize {
    val lp = layoutParent
    if (lp != null) {
        return LayoutSize(
            lp.layoutValue(direction).childSize(layoutIndex),
            false
        )
    }
    throw Error("未找到父节点")
}

fun LayoutNode.sizeFromChildren(direction: Direction): LayoutSize {
    return LayoutSize(
        layoutValue(direction).sizeFromChildren,
        true
    )
}

fun LayoutNode.layoutValue(direction: Direction) = when (direction) {
    Direction.x -> layoutX()
    Direction.y -> layoutY()
}


fun LayoutNode.createLayout(direction: Direction): GetValue<Layout> {
    val insideObject: LayoutInsideObject<LayoutNode> = object : LayoutInsideObject<LayoutNode> {
        override val children: List<LayoutNode>
            get() = layoutChildren
        override val innerSize: Float
            get() = innerSize(direction)
    }

    return memo {
        val layout = when (direction) {
            //延迟到运行时去取值
            Direction.x -> layout.layoutX
            Direction.y -> layout.layoutY
        }
        layout(insideObject)
    }
}

fun LayoutNode.absoluteInInner(x: Float, y: Float): Boolean {
    return inRange(
        absoluteX + paddingInlineStart,
        x,
        innerWidth
    ) && inRange(absoluteY + paddingBlockStart, y, innerHeight)
}