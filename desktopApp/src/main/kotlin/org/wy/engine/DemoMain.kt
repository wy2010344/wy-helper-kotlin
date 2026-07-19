package org.wy.engine

import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import org.wy.engine.layout.Flex

/**
 * 所有 Demo 的入口。
 *
 * 如需单独测试某个 Demo，可以在 main 函数中只调用对应的函数。
 */
fun main() {
    val flex = object : Flex() {
        override val direction: Direction
            get() = Direction.y
        override val gap: Float
            get() = 10f
        override val directionJustify: DirectionJustify
            get() = DirectionJustify.center
        override val alignFix: Boolean
            get() = true
    }

    object : SkiaApp() {
        override fun layout(direction: Direction) = flex.layout(direction)

        override fun StateHolder<Node>.buildChildren() {
//            demoWordBreak(this)
//            demoForcedBreak(this)
//            demoScroll(this)
//            demoRichText(this)
            demoEditableText(this)
        }
    }
}
