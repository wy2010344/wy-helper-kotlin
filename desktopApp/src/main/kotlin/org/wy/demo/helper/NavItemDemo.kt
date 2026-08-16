package org.wy.demo.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.helper.navItem
import org.wy.engine.helper.text
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue

fun main() {
    object : SkiaApp(720, 480), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 8f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            page {
                sectionTitle("NavItem 侧栏导航项")
                hint("点击切换激活项；支持图标圆点与角标。")

                var current by createSignal("首页")

                // 侧栏容器：纵向 + 固定宽度
                object : RectNode(this), FlexParam {
                    override val layout: LayoutDirection = FlexObject(this)
                    override val direction: Direction get() = Direction.y
                    override val directionJustify: DirectionJustify get() = DirectionJustify.start
                    override val argWidth: LayoutSize get() = LayoutSize(200f, false)
                    override val alignFix: Boolean get() = true
                    override val alignItem: AlignItem get() = AlignItem.stretch
                    override val gap: Float get() = 2f

                    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                        navItem({ "首页" }, { current == "首页" }, { current = "首页" }, iconColor = rgba(80, 160, 80))
                        navItem({ "消息" }, { current == "消息" }, { current = "消息" }, iconColor = rgba(60, 120, 220), badge = { 5 })
                        navItem({ "设置" }, { current == "设置" }, { current = "设置" })
                        navItem({ "归档" }, { current == "归档" }, { current = "归档" }, badge = { 12 })
                    }
                }

                hint("当前选中：${current}")
            }
        }
    }
}
