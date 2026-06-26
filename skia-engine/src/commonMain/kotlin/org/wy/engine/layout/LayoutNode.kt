package org.wy.engine.layout

import com.wy.layout.Align
import com.wy.layout.Layout
import com.wy.layout.LayoutFun
import com.wy.layout.absoluteLayout
import org.wy.engine.Direction
import org.wy.lib.GetValue

enum class StartEnd {
    start, end
}

data class LayoutSize(
    val value: Float,
    val fromInside: Boolean
)

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

    val layoutChildren: List<LayoutNode>

    /**
     * 尺寸，可能用户介入手动重写
     */
    fun size(direction: Direction): LayoutSize {
        val lp = layoutParent
        if (lp != null) {
            try {
                return LayoutSize(
                    lp.layoutValue(direction).childSize(layoutIndex),
                    false
                )
            } catch (e: Throwable) {
            }
        }
        return LayoutSize(
            layoutValue(direction).sizeFromChildren,
            true
        )
    }

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
}