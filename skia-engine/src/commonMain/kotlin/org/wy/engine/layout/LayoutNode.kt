package org.wy.engine.layout

import com.wy.layout.Align
import com.wy.layout.Layout
import com.wy.layout.LayoutFun
import com.wy.layout.LayoutInsideObject
import com.wy.layout.absoluteLayout
import org.wy.engine.Direction
import org.wy.lib.GetValue
import org.wy.signal.memo

enum class StartEnd {
    start, end
}

data class LayoutSize(
    val value: Float,
    val fromInside: Boolean
)

enum class SizeFrom {
    inside, outside
}

interface LayoutNode {

    ///

    val grow: Float
        get() = 0f

    val align: Align?
        get() = null

    ////
    val layoutParent: LayoutNode?

    val layoutIndex: Int

    fun layout(direction: Direction): LayoutFun<LayoutNode> {
        return absoluteLayout()
    }

    val layoutX: GetValue<Layout>
    val layoutY: GetValue<Layout>

    fun layoutValue(direction: Direction) = when (direction) {
        Direction.x -> layoutX()
        Direction.y -> layoutY()
    }


    fun padding(direction: Direction, startEnd: StartEnd): Float {
        return 0f
    }

    fun innerStart(direction: Direction): Float {
        return padding(direction, StartEnd.start)
    }

    val layoutChildren: List<LayoutNode>

    fun sizeFromParent(direction: Direction): LayoutSize {
        val lp = layoutParent
        if (lp != null) {
            return LayoutSize(
                lp.layoutValue(direction).childSize(layoutIndex),
                false
            )
        }
        throw Error("未找到父节点")
    }

    fun sizeFromChildren(direction: Direction): LayoutSize {
        return LayoutSize(
            layoutValue(direction).sizeFromChildren,
            true
        )
    }

    /**
     * 尺寸，可能用户介入手动重写
     * 必须手动指定从哪里来
     */
    fun size(direction: Direction): LayoutSize
    fun outerSize(direction: Direction): Float {
        val s = size(direction)
        if (s.fromInside) {
            return s.value + padding(direction, StartEnd.start) + padding(direction, StartEnd.end)
        }
        return s.value
    }

    fun innerSize(direction: Direction): Float {
        val s = size(direction)
        if (s.fromInside) {
            return s.value
        }
        return s.value - padding(direction, StartEnd.start) - padding(direction, StartEnd.end)
    }

    fun createLayout(direction: Direction): GetValue<Layout> {
        val insideObject: LayoutInsideObject<LayoutNode> = object : LayoutInsideObject<LayoutNode> {
            override val children: List<LayoutNode>
                get() = layoutChildren
            override val innerSize: Float
                get() = innerSize(direction)
        }
        return memo {
            layout(direction).createLayout(insideObject)
        }
    }
}