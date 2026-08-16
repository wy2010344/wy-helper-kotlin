package org.wy.demo.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.helper.textField
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
                sectionTitle("TextField 单行输入框")
                hint("点击聚焦输入；支持光标、选区、IME 与 Tab 切换。")

                var name by createSignal("")
                var email by createSignal("")
                var note by createSignal("只读示例")

                row {
                    textField({ name }, { name = it })
                    hint(if (name.isEmpty()) "输入姓名" else "姓名：$name")
                }
                row {
                    textField({ email }, { email = it }, width = 300f)
                    hint(if (email.isEmpty()) "输入邮箱" else "邮箱：$email")
                }
                row {
                    textField({ note }, { note = it }, width = 300f)
                    hint("普通输入框")
                }
            }
        }
    }
}
