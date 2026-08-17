package org.wy.demo.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.helper.Button
import org.wy.engine.helper.ButtonVariant
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue

fun main() {
    object : SkiaApp(720, 420), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 8f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            page {
                sectionTitle("Button 按钮")
                hint("点击 / Enter / Space 触发 onClick；disabled 时不聚焦、不响应。Tab 遍历观察焦点环。")

                row {
                    object : Button(this@row) {
                        override val label: String get() = "主按钮"
                    }
                    object : Button(this@row) {
                        override val label: String get() = "次按钮"
                        override val variant: ButtonVariant get() = ButtonVariant.Secondary
                    }
                    object : Button(this@row) {
                        override val label: String get() = "不可用"
                        override val enabled: Boolean get() = false
                    }
                    object : Button(this@row) {
                        override val label: String get() = "次·不可用"
                        override val variant: ButtonVariant get() = ButtonVariant.Secondary
                        override val enabled: Boolean get() = false
                    }
                }

                var count by createSignal(0)
                row {
                    object : Button(this@row) {
                        override val label: String get() = "点击计数 ($count)"
                        override fun onClick() { count += 1 }
                    }
                    hint("点击上面按钮观察计数变化")
                }
            }
        }
    }
}
