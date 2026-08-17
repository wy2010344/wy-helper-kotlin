package org.wy.demo.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.helper.Button
import org.wy.engine.helper.ButtonVariant
import org.wy.engine.helper.dialog
import org.wy.engine.helper.text
import org.wy.engine.helper.textField
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
        var input by createSignal("")

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            page {
                sectionTitle("Dialog 模态对话框")
                hint("打开期间圈定焦点（Tab 只在框内循环）；Esc 或点击遮罩关闭。")

                row {
                    object : Button(this@row) {
                        override val label: String get() = "打开对话框"

                        override fun onClick() {
                            engineGlobal.appendPop { pop ->
                                dialog({
                                    engineGlobal.removePop(pop)
                                }, width = 340f) {
                                    text({ "登录" }, 15f, rgba(40, 40, 60), 700)
                                    textField({ input }, { input = it }, width = 300f)
                                    row(gap = 8f) {
                                        object : Button(this@row) {
                                            override val label: String get() = "取消"
                                            override val variant: ButtonVariant get() = ButtonVariant.Secondary

                                            override fun onClick() {
                                                engineGlobal.removePop(pop)
                                            }
                                        }
                                        object : Button(this@row) {
                                            override val label: String get() = "确定"

                                            override fun onClick() {
                                                engineGlobal.removePop(pop)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    hint("打开后焦点自动移入对话框，关闭时还原。")
                }
            }
        }
    }
}
