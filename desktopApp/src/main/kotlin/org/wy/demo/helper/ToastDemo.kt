package org.wy.demo.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.helper.Button
import org.wy.engine.helper.ButtonVariant
import org.wy.engine.helper.text
import org.wy.engine.helper.toast
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection

fun main() {
    object : SkiaApp(720, 460), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 8f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            page {
                sectionTitle("Toast 轻提示")
                hint("全局最上层（在 Pop 之上）；整层不拦截命中；支持多个 toast 同时显示，超出可视区域可滚动。")

                row {
                    object : Button(this@row) {
                        override val label: String get() = "显示 Toast (2s)"
                        override fun onClick() {
                            toast(durationMs = 2000L) {
                                text({ "操作成功！这是一条轻提示。" }, 13f, rgba(255, 255, 255))
                            }
                        }
                    }
                    object : Button(this@row) {
                        override val label: String get() = "显示 Toast (5s)"
                        override val variant: ButtonVariant get() = ButtonVariant.Secondary
                        override fun onClick() {
                            toast(durationMs = 5000L) {
                                text({ "这是一条较长的提示（5 秒后消失）。" }, 13f, rgba(255, 255, 255))
                            }
                        }
                    }
                    hint("点击内容条可立即关闭")
                }

                row {
                    object : Button(this@row) {
                        override val label: String get() = "连续弹出 3 条 Toast"
                        override val variant: ButtonVariant get() = ButtonVariant.Secondary
                        override fun onClick() {
                            toast(durationMs = 2000L) {
                                text({ "Toast 1 — 2 秒后消失" }, 13f, rgba(255, 255, 255))
                            }
                            toast(durationMs = 3000L) {
                                text({ "Toast 2 — 3 秒后消失" }, 13f, rgba(255, 255, 255))
                            }
                            toast(durationMs = 4000L) {
                                text({ "Toast 3 — 4 秒后消失" }, 13f, rgba(255, 255, 255))
                            }
                        }
                    }
                    hint("多次调用 toast() 会累加显示，超出区域可滚动")
                }
            }
        }
    }
}
