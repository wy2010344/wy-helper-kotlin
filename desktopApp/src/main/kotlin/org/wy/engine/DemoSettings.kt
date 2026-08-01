package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.GrowChild
import org.wy.engine.layout.LayoutDirection
import org.wy.lib.StoreRef
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue

// ════════════════════════════════════════════════════
// 设置页
// ════════════════════════════════════════════════════
internal fun StateHolder<Node>.dSettingsPage(active: StoreRef<DemoTab>, toast: StoreRef<String>): RectNode {
    val name = createSignal("Ada")
    val email = createSignal("ada@wy.helper")
    val notify = createSignal(true)
    val dark = createSignal(false)
    val autosave = createSignal(true)
    val fontSize = createSignal(0.5f)
    val volume = createSignal(0.7f)

    return object : RectNode(this), FlexParam, GrowChild {
        override val hide: Boolean get() = active.value != DemoTab.SETTINGS

        override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f

        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.y
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val alignFix: Boolean get() = true
        override val gap: Float get() = 12f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            dLabel({ "设置" }, 14f, TEXT, 600)

            object : RectNode(this), FlexParam, GrowChild {
                override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.y
                override val directionJustify: DirectionJustify get() = DirectionJustify.start
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val alignFix: Boolean get() = true
                override val gap: Float get() = 14f

                override fun draw(canvas: PlatformCanvas) {
                    canvas.fillRoundRect(0f, 0f, outerWidth, outerHeight, 10f, CARD)
                    canvas.strokeRoundRect(0f, 0f, outerWidth, outerHeight, 10f, BORDER, 1f)
                    super.draw(canvas)
                }

                override fun argPadding(direction: Direction, startEnd: StartEnd): Float = 16f

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    object : RectNode(this), FlexParam {
                        override val layout: LayoutDirection = FlexObject(this)
                        override val direction: Direction get() = Direction.x
                        override val alignItem: AlignItem get() = AlignItem.center
                        override val gap: Float get() = 12f

                        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                            dLabel({ "名称" }, 13f, TEXT2, 500, width = 80f)
                            dTextField(name, focusOrder = 20)
                        }
                    }

                    object : RectNode(this), FlexParam {
                        override val layout: LayoutDirection = FlexObject(this)
                        override val direction: Direction get() = Direction.x
                        override val alignItem: AlignItem get() = AlignItem.center
                        override val gap: Float get() = 12f

                        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                            dLabel({ "邮箱" }, 13f, TEXT2, 500, width = 80f)
                            dTextField(email, focusOrder = 21)
                        }
                    }

                    dToggleRow("开启通知", notify, focusOrder = 22)
                    dToggleRow("深色模式", dark, focusOrder = 23)
                    dToggleRow("自动保存", autosave, focusOrder = 24)
                    dSlider("字体大小", fontSize, focusOrder = 25)
                    dSlider("音量", volume, focusOrder = 26)

                    object : RectNode(this), FlexParam {
                        override val layout: LayoutDirection = FlexObject(this)
                        override val direction: Direction get() = Direction.x
                        override val directionJustify: DirectionJustify get() = DirectionJustify.end
                        override val alignItem: AlignItem get() = AlignItem.center
                        override val gap: Float get() = 8f

                        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                            dButton(
                                "重置",
                                {
                                    name.value = ""
                                    email.value = ""
                                    fontSize.value = 0.5f
                                    volume.value = 0.7f
                                    toast.value = "已重置设置"
                                },
                                primary = false, width = 72f, height = 30f
                            )
                            dButton(
                                "保存",
                                { toast.value = "已保存 ✓" },
                                primary = true, width = 80f, height = 30f
                            )
                        }
                    }
                }
            }
        }
    }
}
