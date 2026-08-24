package org.wy.demo.animation

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolderWithNode
import com.wy.mve.StateHolder
import org.wy.demo.helper.hint
import org.wy.demo.helper.page
import org.wy.demo.helper.row
import org.wy.demo.helper.sectionTitle
import org.wy.engine.*
import org.wy.engine.animation.AnimateSignal
import org.wy.engine.animation.EaseFn
import org.wy.engine.animation.EaseFns
import org.wy.engine.animation.SpringAnimationArg
import org.wy.engine.animation.SpringBaseArg
import org.wy.engine.animation.easeOutFn
import org.wy.engine.animation.spring
import org.wy.engine.animation.tween
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection

/** 缓动对比用的小圆点 */
private class Dot(val name: String, val fn: EaseFn, val color: ColorInt) {
    val x = AnimateSignal(0f)
}

fun main() {
    object : SkiaApp(720, 470), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 8f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            page {
                sectionTitle("AnimateSignal 动画信号")
                hint("动画每帧写信号并自动触发重绘；连续点击可打断、重定向（Deferred 回报是否自然完成）")

                // ── ① 欠阻尼弹簧：点击切换宽度，可见过冲振荡 ──
                val barWidth = AnimateSignal(140f)
                row {
                    object : RectNode(this@row) {
                        override val argWidth: LayoutSize get() = LayoutSize(barWidth.value, false)
                        override val argHeight: LayoutSize get() = LayoutSize(40f, false)

                        override fun onPointerClick(e: PointerEvent) {
                            val target = if (barWidth.getTarget() > 240f) 140f else 340f
                            barWidth.animateTo(
                                target,
                                spring(SpringAnimationArg(config = SpringBaseArg(omega0 = 10f, zeta = 0.5f))),
                            )
                        }

                        override fun draw(canvas: PlatformCanvas) {
                            fillOuterRoundRect(canvas, 8f, rgba(130, 175, 255))
                            super.draw(canvas)
                        }

                        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                            object : WrappedTextNode(this) {
                                override val autoWidth: Boolean get() = true
                                override val text: String get() = "点击我"
                                override val fontSize: Float get() = 12f
                                override val color: ColorInt get() = rgba(255, 255, 255)
                            }
                        }
                    }

                    object : WrappedTextNode(this@row) {
                        override val autoWidth: Boolean get() = true
                        override val text: String
                            get() = "width=${barWidth.value.toInt()}px · ${if (barWidth.onAnimation) "动画中" else "空闲"}"
                        override val fontSize: Float get() = 12f
                        override val color: ColorInt get() = rgba(90, 90, 110)
                    }
                }

                // ── ② 缓动曲线对比：同一时长不同 ease ──
                val dots = listOf(
                    Dot("linear", EaseFns.linear, rgba(120, 170, 250)),
                    Dot("easeOut(back)", easeOutFn(EaseFns.back()), rgba(120, 200, 150)),
                    Dot("bounceOut", { EaseFns.bounceOut(it) }, rgba(240, 170, 100)),
                )

                row {
                    object : RectNode(this@row) {
                        override val argWidth: LayoutSize get() = LayoutSize(72f, false)
                        override val argHeight: LayoutSize get() = LayoutSize(26f, false)

                        override fun onPointerClick(e: PointerEvent) {
                            dots.forEach { it.x.animateTo(400f, tween(900f, it.fn)) }
                        }

                        override fun draw(canvas: PlatformCanvas) {
                            fillOuterRoundRect(canvas, 6f, rgba(180, 210, 255))
                            super.draw(canvas)
                        }

                        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                            object : WrappedTextNode(this) {
                                override val autoWidth: Boolean get() = true
                                override val text: String get() = "开始"
                                override val fontSize: Float get() = 12f
                                override val color: ColorInt get() = rgba(0, 60, 160)
                            }
                        }
                    }

                    object : RectNode(this@row) {
                        override val argWidth: LayoutSize get() = LayoutSize(72f, false)
                        override val argHeight: LayoutSize get() = LayoutSize(26f, false)

                        override fun onPointerClick(e: PointerEvent) {
                            // set 直接写值并打断动画（对比 stop 冻结）
                            dots.forEach { it.x.set(0f) }
                        }

                        override fun draw(canvas: PlatformCanvas) {
                            fillOuterRoundRect(canvas, 6f, rgba(235, 190, 190))
                            super.draw(canvas)
                        }

                        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                            object : WrappedTextNode(this) {
                                override val autoWidth: Boolean get() = true
                                override val text: String get() = "重置"
                                override val fontSize: Float get() = 12f
                                override val color: ColorInt get() = rgba(140, 30, 30)
                            }
                        }
                    }

                    hint("tween(900ms)：linear / easeOut(back) / bounceOut")
                }

                dots.forEach { d ->
                    row {
                        object : WrappedTextNode(this@row) {
                            override val autoWidth: Boolean get() = true
                            override val text: String get() = d.name
                            override val fontSize: Float get() = 11f
                            override val color: ColorInt get() = rgba(130, 130, 150)
                        }

                        object : RectNode(this@row) {
                            override val argWidth: LayoutSize get() = LayoutSize(440f, false)
                            override val argHeight: LayoutSize get() = LayoutSize(22f, false)

                            override fun draw(canvas: PlatformCanvas) {
                                fillOuterRoundRect(canvas, 11f, rgba(238, 238, 245))
                                val x = d.x.value
                                canvas.fillOval(x.coerceAtMost(440f - 14f), 3f, 16f, 16f, d.color)
                                super.draw(canvas)
                            }
                        }
                    }
                }
            }
        }
    }
}
