package org.wy.demo.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.helper.ButtonBase
import org.wy.engine.helper.ButtonVariant
import org.wy.engine.helper.button
import org.wy.engine.helper.dropdown
import org.wy.engine.helper.navItem
import org.wy.engine.helper.text
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

        var open by createSignal(false)
        var selected by createSignal("未选择")
        lateinit var anchorBtn: ButtonBase

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            page {
                sectionTitle("Dropdown 下拉浮层")
                hint("面板锚定按钮下方；点击外部 / Esc 关闭，Tab 可从面板逃逸回主界面。")

                row {
                    anchorBtn = button({ "选择城市 ($selected)" }, { open = true }, variant = ButtonVariant.Secondary, width = 180f)
                    hint("点击按钮展开下拉列表")
                }
            }

            // 下拉浮层：铺满窗口、面板贴锚点底部显示
            dropdown({ open }, { anchorBtn }, { open = false }, width = 180f) {
                listOf("北京", "上海", "广州", "深圳").forEach { city ->
                    navItem({ city }, { selected == city }, onClick = { selected = city; open = false })
                }
            }
        }
    }
}
