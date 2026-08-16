package org.wy.demo.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.helper.switch
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue

fun main() {
    object : SkiaApp(720, 400), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 8f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            page {
                sectionTitle("Switch 开关")
                hint("点击 / Enter / Space 切换；disabled 时不可用。")

                var a by createSignal(false)
                var b by createSignal(true)
                var locked by createSignal(true)

                row { switch({ a }, { a = it }); hint("默认开关") }
                row { switch({ b }, { b = it }); hint("默认开启") }
                row { switch({ locked }, { locked = it }); hint("默认开启，可关闭") }
                row { switch({ true }, {}, enabled = false); hint("不可用（开启态）") }
                row { switch({ false }, {}, enabled = false); hint("不可用（关闭态）") }
            }
        }
    }
}
