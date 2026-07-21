package org.wy.engine

import com.wy.layout.DirectionJustify
import com.wy.layout.Layout
import com.wy.mve.StateHolder
import org.wy.engine.layout.Flex
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.lib.GetValue

/**
 * 所有 Demo 的入口。
 *
 * 如需单独测试某个 Demo，可以在 main 函数中只调用对应的函数。
 */
fun main() {
    object : SkiaApp(), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val directionJustify: DirectionJustify
            get() = DirectionJustify.center
        override val alignFix: Boolean
            get() = true
        override val gap: Float
            get() = 10f
        override fun StateHolder<Node>.argChildren() {
//            demoWordBreak(this)
//            demoForcedBreak(this)
//            demoScroll(this)
//            demoRichText(this)
//            demoEditableText(this)
            demoList()
        }
    }
}
