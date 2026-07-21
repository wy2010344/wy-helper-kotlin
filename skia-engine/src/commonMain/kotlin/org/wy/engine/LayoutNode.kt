package org.wy.engine

import com.wy.layout.Align
import com.wy.layout.Layout
import com.wy.layout.LayoutInsideObject
import com.wy.layout.alawaysAbsolute
import org.wy.engine.layout.LayoutDirection
import org.wy.engine.layout.absoluteLayoutDirection
import org.wy.lib.GetValue
import org.wy.signal.memo

enum class StartEnd {
    start, end
}

data class LayoutSize(
    val value: Float,
    val fromInside: Boolean
)

val layoutSize0= LayoutSize(0f,true)

enum class SizeFrom {
    inside, outside
}

interface LayoutNode {
    val grow: Float
        get() = 0f
    val align: Align?
        get() = null

    val layout: LayoutDirection
        get() = absoluteLayoutDirection
    ////
    val layoutParent: LayoutNode?

    val layoutIndex: Int

    val layoutChildren: List<LayoutNode>

    val layoutX: GetValue<Layout>
    val layoutY: GetValue<Layout>
    fun argSize(direction: Direction): LayoutSize = layoutSize0


    val argWidth: LayoutSize
        get()=argSize(Direction.x)
    val argHeight: LayoutSize
        get() = argSize(Direction.y)


    fun argPadding(direction: Direction, startEnd: StartEnd) = 0f
    fun argPaddingInline(startEnd: StartEnd) =  argPadding(Direction.x,startEnd)

    fun argPaddingBlock(startEnd: StartEnd) = argPadding(Direction.y,startEnd)

    val paddingInlineStart
        get() = argPaddingInline(StartEnd.start)
    val paddingInlineEnd
        get() = argPaddingInline(StartEnd.end)
    val paddingBlockStart
        get() = argPaddingBlock(StartEnd.start)
    val paddingBlockEnd
        get() = argPaddingBlock(StartEnd.end)

}

fun LayoutNode.padding(direction: Direction,startEnd: StartEnd)=when(direction){
    Direction.x -> when(startEnd){
        StartEnd.start->paddingInlineStart
        StartEnd.end->paddingInlineEnd
    }
    Direction.y->when(startEnd){
        StartEnd.start->paddingBlockStart
        StartEnd.end->paddingBlockEnd
    }
}
fun LayoutNode.size(direction: Direction)=when(direction){
    Direction.x->argWidth
    Direction.y->argHeight
}
fun LayoutNode.outerSize(direction: Direction): Float {
    val s = size(direction)
    if (s.fromInside) {
        return s.value + padding(direction, StartEnd.start) + padding(direction, StartEnd.end)
    }
    return s.value
}

fun LayoutNode.innerSize(direction: Direction): Float {
    val s = size(direction)
    if (s.fromInside) {
        return s.value
    }
    return s.value - padding(direction, StartEnd.start) - padding(direction, StartEnd.end)
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
        val layout=when(direction){
            //延迟到运行时去取值
            Direction.x->layout.layoutX
            Direction.y->layout.layoutY
        }
        layout(insideObject)
    }
}