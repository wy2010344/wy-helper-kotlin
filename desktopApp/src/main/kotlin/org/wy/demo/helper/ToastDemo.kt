package org.wy.demo.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.helper.button
import org.wy.engine.helper.text
import org.wy.engine.helper.toast
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue

fun main() {
    object : SkiaApp(720, 460), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 8f

        var shown by createSignal(false)

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            page {
                sectionTitle("Toast 轻提示")
                hint("整层不拦截命中（可穿透点击主界面）；到时自动消失或点击内容立即关闭。")

                row {
                    button({ "显示 Toast (2s)" }, { shown = true })
                    button({ "显示 Toast (5s)" }, { shown = true }, variant = org.wy.engine.helper.ButtonVariant.Secondary)
                    hint("点击内容条可立即关闭")
                }
            }

            // 轻提示浮层：声明在渲染树最后
            toast({ shown }, { shown = false }, durationMs = 2000L) {
                text({ "操作成功！这是一条轻提示。" }, 13f, rgba(255, 255, 255))
            }
        }
    }
}
