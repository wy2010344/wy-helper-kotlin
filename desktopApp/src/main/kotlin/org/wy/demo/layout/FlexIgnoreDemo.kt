package org.wy.demo.layout

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.IgnoreFlex
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue

fun main() {
    object : SkiaApp(820, 460), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 10f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            flexIgnoreMain()
        }
    }
}

/**
 * 主轴 ignore 演示：横向 flex 中，IgnoreFlex 子节点 ignore=true 时不占主轴空间
 * （作为半透明浮层盖在容器左上角、不挤动其它子节点）；ignore=false 时退回普通流内子节点占位。
 */
fun StateHolder<Node, List<Node>>.flexIgnoreMain() {
    object : RectNode(this), FlexParam {
        override val direction: Direction = Direction.y
        override val layout: LayoutDirection = FlexObject(this)
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 16f

        override fun argPadding(direction: Direction, startEnd: StartEnd): Float {
            return if (direction == Direction.x) 20f else 0f
        }

        var ignoreOn by createSignal(true)

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            // 标题行 + 切换按钮
            object : RectNode(this), FlexParam {
                override val direction: Direction = Direction.x
                override val layout: LayoutDirection = FlexObject(this)
                override val alignItem: AlignItem get() = AlignItem.center
                override val gap: Float get() = 12f

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    object : WrappedTextNode(this) {
                        override val autoWidth: Boolean get() = true
                        override val text: String get() =
                            "Flex 主轴 ignore：ignore=true 时浮层不占主轴空间、不挤动其它子节点"
                        override val fontSize: Float get() = 14f
                        override val fontWeight: Int get() = 700
                        override val color: ColorInt get() = rgba(40, 40, 60)
                    }

                    object : RectNode(this) {
                        override val argWidth: LayoutSize get() = LayoutSize(120f, false)
                        override val argHeight: LayoutSize get() = LayoutSize(28f, false)
                        override val focusable: Boolean get() = true

                        override fun draw(canvas: PlatformCanvas) {
                            val color = if (ignoreOn) rgba(180, 210, 255) else rgba(230, 170, 170)
                            fillOuterRoundRect(canvas, 6f, color)
                            super.draw(canvas)
                        }

                        override fun onPointerClick(e: PointerEvent) {
                            ignoreOn = !ignoreOn
                        }

                        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                            object : WrappedTextNode(this) {
                                override val autoWidth: Boolean get() = true
                                override val text: String get() = "ignore=${if (ignoreOn) "ON" else "OFF"}"
                                override val fontSize: Float get() = 12f
                                override val color: ColorInt get() = rgba(0, 60, 160)
                            }
                        }
                    }
                }
            }

            // 演示容器：3 个普通 flex 子节点 + 1 个 IgnoreFlex 子节点
            object : RectNode(this), FlexParam {
                override val direction: Direction = Direction.x
                override val layout: LayoutDirection = FlexObject(this)
                override val directionJustify: DirectionJustify get() = DirectionJustify.start
                override val argHeight: LayoutSize get() = LayoutSize(240f, false)
                override val alignFix: Boolean get() = true
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val gap: Float get() = 10f

                override fun draw(canvas: PlatformCanvas) {
                    fillOuterRect(canvas, rgba(245, 245, 250))
                    super.draw(canvas)
                }

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    ignoreFlexBox("普通子节点 1", rgba(210, 230, 210))
                    ignoreFlexBox("普通子节点 2", rgba(210, 220, 245))
                    ignoreFlexBox("普通子节点 3", rgba(245, 225, 200))

                    // IgnoreFlex 子节点：ignore=true 时铺满容器但不占主轴空间；false 时退回流内占空间
                    object : RectNode(this), IgnoreFlex {
                        override val ignore: Boolean get() = ignoreOn
                        override fun argPosition(direction: Direction): Float =
                            if (ignoreOn) 0f else super.argPosition(direction)

                        override val argWidth: LayoutSize
                            get() = if (ignoreOn) LayoutSize(760f, false) else LayoutSize(100f, false)
                        override val argHeight: LayoutSize get() = LayoutSize(120f, false)

                        override fun draw(canvas: PlatformCanvas) {
                            if (ignoreOn) {
                                canvas.save()
                                canvas.fillRoundRect(0f, 0f, outerWidth, outerHeight, 8f, rgba(80, 140, 255, 60))
                                canvas.strokeRoundRect(0f, 0f, outerWidth, outerHeight, 8f, rgba(40, 100, 240), 2f)
                                canvas.restore()
                            } else {
                                fillOuterRect(canvas, rgba(255, 140, 140))
                            }
                            super.draw(canvas)
                        }

                        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                            object : WrappedTextNode(this) {
                                override val autoWidth: Boolean get() = true
                                override val text: String get() =
                                    if (ignoreOn) "ignore=ON：浮层不占主轴空间" else "ignore=OFF：退回流内占空间"
                                override val fontSize: Float get() = 12f
                                override val fontWeight: Int get() = 700
                                override val color: ColorInt get() =
                                    if (ignoreOn) rgba(20, 60, 180) else rgba(140, 30, 30)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun StateHolder<Node, List<Node>>.ignoreFlexBox(
    text: String,
    color: ColorInt,
) {
    object : RectNode(this) {
        override val argWidth: LayoutSize get() = LayoutSize(100f, false)
        override val argHeight: LayoutSize get() = LayoutSize(120f, false)

        override fun draw(canvas: PlatformCanvas) {
            fillOuterRect(canvas, color)
            super.draw(canvas)
        }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            object : WrappedTextNode(this) {
                override val autoWidth: Boolean get() = true
                override val text: String get() = text
                override val fontSize: Float get() = 12f
                override val color: ColorInt get() = rgba(60, 60, 80)
            }
        }
    }
}
