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
    object : SkiaApp(820, 480), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 10f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            stackIgnoreMain()
        }
    }
}

/**
 * 交叉轴 ignore 演示：纵向 flex（alignItem=stretch）中，IgnoreFlex 子节点 ignore=true 时
 * 不被交叉轴 stretch 拉伸（保留自身宽度、靠左对齐），也不会撑大容器高度；
 * ignore=false 时退回普通流内子节点：被拉伸到整行宽、参与容器高度计算。
 */
fun StateHolder<Node, List<Node>>.stackIgnoreMain() {
    object : RectNode(this), FlexParam {
        override val direction: Direction = Direction.y
        override val layout: LayoutDirection = FlexObject(this)
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
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
                            "交叉轴 ignore：ignore=true 时不被 stretch 拉伸、不撑大容器"
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

            // 演示容器：纵向 stretch，高度由非 ignore 子节点撑起
            object : RectNode(this), FlexParam {
                override val direction: Direction = Direction.y
                override val layout: LayoutDirection = FlexObject(this)
                override val directionJustify: DirectionJustify get() = DirectionJustify.grow
                override val argWidth: LayoutSize get() = LayoutSize(760f, false)
                override val alignFix: Boolean get() = true
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val gap: Float get() = 10f

                override fun draw(canvas: PlatformCanvas) {
                    fillOuterRect(canvas, rgba(245, 245, 250))
                    super.draw(canvas)
                }

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    ignoreStackRow("普通子节点 1（stretch 到整行宽）", rgba(210, 230, 210))
                    ignoreStackRow("普通子节点 2（stretch 到整行宽）", rgba(210, 220, 245))

                    // IgnoreFlex 子节点：ignore=true 时不参与 stretch 与高度计算；false 时退回流内
                    object : RectNode(this), IgnoreFlex {
                        override val ignore: Boolean get() = ignoreOn
                        override fun argPosition(direction: Direction): Float =
                            if (ignoreOn && direction == Direction.x) 0f else super.argPosition(direction)

                        override val argWidth: LayoutSize
                            get() = if (ignoreOn) LayoutSize(180f, false) else LayoutSize(760f, false)
                        override val argHeight: LayoutSize get() = LayoutSize(80f, false)

                        override fun draw(canvas: PlatformCanvas) {
                            if (ignoreOn) {
                                fillOuterRoundRect(canvas, 8f, rgba(255, 200, 80))
                                canvas.save()
                                canvas.strokeRoundRect(0f, 0f, outerWidth, outerHeight, 8f, rgba(200, 140, 20), 2f)
                                canvas.restore()
                            } else {
                                fillOuterRoundRect(canvas, 8f, rgba(255, 140, 140))
                            }
                            super.draw(canvas)
                        }

                        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                            object : WrappedTextNode(this) {
                                override val autoWidth: Boolean get() = true
                                override val text: String get() =
                                    if (ignoreOn) "ignore=ON：不被拉伸、不撑高容器" else "ignore=OFF：拉伸整行、参与高度"
                                override val fontSize: Float get() = 12f
                                override val fontWeight: Int get() = 700
                                override val color: ColorInt get() =
                                    if (ignoreOn) rgba(120, 80, 10) else rgba(140, 30, 30)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun StateHolder<Node, List<Node>>.ignoreStackRow(
    text: String,
    color: ColorInt,
) {
    object : RectNode(this) {
        override val argHeight: LayoutSize get() = LayoutSize(50f, false)

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
