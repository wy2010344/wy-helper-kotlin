package org.wy.demo.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.helper.segTabs
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
                sectionTitle("SegTabs 分段控件")
                hint("点击分段切换选中项；Enter / Space 同样可触发。")

                var tab by createSignal("概述")
                segTabs({ tab }, { tab = it }, listOf("概述" to "概述", "详情" to "详情", "设置" to "设置"))

                var mode by createSignal("日")
                segTabs(
                    { mode }, { mode = it },
                    listOf("日" to "日", "周" to "周", "月" to "月", "年" to "年"),
                )

                segTabs({ "禁用" }, {}, listOf("禁用" to "禁用", "不可点" to "不可点"), enabled = false)
            }
        }
    }
}
