package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import org.wy.engine.helper.SimpleScrollNode
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.IgnoreFlex
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue
import java.util.Date

fun main() {
    object : SkiaApp(900, 640), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val directionJustify: DirectionJustify
            get() = DirectionJustify.center
        override val alignFix: Boolean
            get() = true
        override val alignItem: AlignItem
            get() = AlignItem.stretch
        override val gap: Float
            get() = 10f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
//            demoTitle()
//            demoEditable()
//            demoImage()
//            demoGraphics()
//            demoList()
            demoIgnore()
        }
    }
}

fun StateHolder<Node,List<Node>>.demoTitle() {
    object : WrappedTextNode(this) {
        override val autoWidth: Boolean get() = true
        override val text: String get() = "wy-helper engine demo"
        override val fontSize: Float get() = 24f
        override val fontWeight: Int get() = 700
    }
}

fun StateHolder<Node,List<Node>>.demoEditable() {
    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val argWidth: LayoutSize get() = LayoutSize(420f, true)
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            object : WrappedTextNode(this) {
                override val autoWidth: Boolean get() = true
                override val text: String get() = "Editable text: click to focus, Tab switches fields, Ctrl+C/V/X/Z/Y/A"
                override val fontSize: Float get() = 12f
                override val color: ColorInt get() = rgba(100, 100, 100)
            }

            object : EditableTextNode(this) {
                override var text by createSignal("hello")
                override val fontSize: Float get() = 15f
            }

            object : EditableTextNode(this) {
                override val fontSize: Float get() = 15f
            }

            object : EditableTextNode(this) {
                override val fontSize: Float get() = 15f
            }
        }
    }
}

fun StateHolder<Node,List<Node>>.demoImage() {
    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val alignItem: AlignItem get() = AlignItem.center
        override val alignFix: Boolean
            get() = true
        override val gap: Float get() = 10f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            val img = makeTestImage(64, 64)

            object : ImageNode(this) {
                override val image: PlatformImage? get() = img
                override val size: LayoutSizeDirection get() = LayoutSizeDirection(Direction.x, 96f, true)
                override val radius: Float get() = 12f
            }

            object : ImageNode(this) {
                override val image: PlatformImage? get() = img
                override val size: LayoutSizeDirection get() = LayoutSizeDirection(Direction.y, 64f, true)
                override fun argPadding(direction: Direction, startEnd: StartEnd) = 8f
            }

            object : ImageNode(this) {
                override val image: PlatformImage? get() = img
                override val size: LayoutSizeDirection get() = LayoutSizeDirection(Direction.x, 48f, true)

                override fun draw(canvas: PlatformCanvas) {
                    canvas.saveLayerAlpha(0.4f)
                    super.draw(canvas)
                    canvas.restore()
                }
            }
        }
    }
}

fun StateHolder<Node,List<Node>>.demoGraphics() {
    object : RectNode(this), FlexParam {
        override val direction: Direction = Direction.y
        override val layout: LayoutDirection = FlexObject(this)
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.center
        override val gap: Float get() = 10f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            object : RectNode(this) {
                override val argWidth: LayoutSize get() = LayoutSize(120f, false)
                override val argHeight: LayoutSize get() = LayoutSize(80f, false)

                override fun draw(canvas: PlatformCanvas) {
                    canvas.fillRoundRect(0f, 0f, 120f, 80f, 16f, rgba(220, 240, 220))
                    canvas.strokeRoundRect(0f, 0f, 120f, 80f, 16f, rgba(0, 150, 0), 2f)
                    super.draw(canvas)
                }
            }

            object : RectNode(this) {
                override val argWidth: LayoutSize get() = LayoutSize(120f, false)
                override val argHeight: LayoutSize get() = LayoutSize(80f, false)

                override fun draw(canvas: PlatformCanvas) {
                    canvas.fillOval(10f, 10f, 60f, 60f, rgba(255, 200, 0))
                    canvas.drawLine(10f, 70f, 110f, 10f, rgba(0, 0, 200), 3f)
                    super.draw(canvas)
                }
            }

            object : RectNode(this) {
                override val argWidth: LayoutSize get() = LayoutSize(120f, false)
                override val argHeight: LayoutSize get() = LayoutSize(80f, false)

                override fun draw(canvas: PlatformCanvas) {
                    canvas.save()
                    canvas.translate(60f, 40f)
                    canvas.rotate(30f)
                    canvas.translate(-60f, -40f)
                    canvas.fillRect(20f, 20f, 80f, 40f, rgba(120, 0, 200))
                    canvas.restore()
                    super.draw(canvas)
                }
            }
        }
    }
}

private class RowModal(val key: Long) {
    var hide by createSignal(false)
}fun StateHolder<Node,List<Node>>.demoList() {
    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val argWidth: LayoutSize get() = LayoutSize(420f, false)
        override val argHeight: LayoutSize get() = LayoutSize(220f, false)
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 6f

        var list by createSignal(emptyList<RowModal>())

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {

            object : WrappedTextNode(this) {
                override val autoWidth: Boolean get() = true
                override val text: String get() = "List (${list.size} rows)"
                override val fontSize: Float get() = 13f
            }

            object : SimpleScrollNode(this) {
                override val argWidth: LayoutSize get() = LayoutSize(420f, false)
                override val argHeight: LayoutSize get() = LayoutSize(170f, false)
                override val contentGap: Float get() = 6f

                override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
                    renderForEach({ callback ->
                        list.forEach { callback(it.key, it) }
                    }) { key, it ->
                        object : RectNode(this), FlexParam {
                            override val hide: Boolean get() = it.value.hide
                            override val direction: Direction = Direction.x
                            override val layout: LayoutDirection = FlexObject(this)
                            override val alignFix: Boolean get() = true
                            override val directionJustify: DirectionJustify = DirectionJustify.between
                            override val argWidth: LayoutSize get() = LayoutSize(410f, false)
                            override val argHeight: LayoutSize get() = LayoutSize(26f, false)

                            override fun draw(canvas: PlatformCanvas) {
                                fillOuterRect(canvas, rgba(235, 235, 245))
                                super.draw(canvas)
                            }

                            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                                object : WrappedTextNode(this) {
                                    override val autoWidth: Boolean get() = true
                                    override val text: String get() = "row $key"
                                }

                                object : WrappedTextNode(this) {
                                    override val autoWidth: Boolean get() = true
                                    override val text: String get() = "hide"

                                    override fun onPointerClick(e: PointerEvent) {
                                        it.value.hide = true
                                    }
                                }

                                object : WrappedTextNode(this) {
                                    override val autoWidth: Boolean get() = true
                                    override val text: String get() = "delete"

                                    override fun onPointerClick(e: PointerEvent) {
                                        list = list.filter { r -> r.key != key }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            object : RectNode(this) {
                override val argWidth: LayoutSize get() = LayoutSize(120f, false)
                override val argHeight: LayoutSize get() = LayoutSize(30f, false)

                override fun onPointerClick(e: PointerEvent) {
                    list = list + RowModal(Date().time)
                }

                override fun draw(canvas: PlatformCanvas) {
                    fillOuterRoundRect(canvas, 8f, rgba(180, 210, 255))
                    super.draw(canvas)
                }

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    object : WrappedTextNode(this) {
                        override val autoWidth: Boolean get() = true
                        override val text: String get() = "+ add row"
                        override val color: ColorInt get() = rgba(0, 60, 160)
                    }
                }
            }
        }
    }
}

/**
 * IgnoreFlex 演示：一个"浮层"子节点，ignore=true 时不占 flex 主轴空间（铺满容器、不挤动其它子节点），
 * ignore=false 时退回普通流内子节点（重新占空间）。点击按钮切换对比。
 */
fun StateHolder<Node, List<Node>>.demoIgnore() {
    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction = Direction.y
        override val directionJustify: DirectionJustify = DirectionJustify.start
        override val argWidth: LayoutSize get() = LayoutSize(420f, false)
        override val argHeight: LayoutSize get() = LayoutSize(280f, false)
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 8f

        var ignoreOn by createSignal(true)

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            // 提示 + 切换按钮
            object : RectNode(this), FlexParam {
                override val direction: Direction = Direction.x
                override val layout: LayoutDirection = FlexObject(this)
                override val alignItem: AlignItem get() = AlignItem.center
                override val gap: Float get() = 8f

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    object : WrappedTextNode(this) {
                        override val autoWidth: Boolean get() = true
                        override val text: String get() = "IgnoreFlex: 浮层子节点不占 flex 空间"
                        override val fontSize: Float get() = 13f
                        override val color: ColorInt get() = rgba(80, 80, 100)
                    }

                    object : RectNode(this) {
                        override val argWidth: LayoutSize get() = LayoutSize(90f, false)
                        override val argHeight: LayoutSize get() = LayoutSize(26f, false)
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
                                override val text: String get() = if (ignoreOn) "ignore=ON" else "ignore=OFF"
                                override val fontSize: Float get() = 12f
                                override val color: ColorInt get() = rgba(0, 60, 160)
                            }
                        }
                    }
                }
            }

            // 演示容器：3 个普通 flex 子节点 + 1 个 IgnoreFlex 子节点
            object : RectNode(this), FlexParam {
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction = Direction.y
                override val directionJustify: DirectionJustify = DirectionJustify.start
                override val argWidth: LayoutSize get() = LayoutSize(420f, false)
                override val argHeight: LayoutSize get() = LayoutSize(200f, false)
                override val alignFix: Boolean get() = true
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val gap: Float get() = 6f

                override fun draw(canvas: PlatformCanvas) {
                    fillOuterRect(canvas, rgba(245, 245, 250))
                    super.draw(canvas)
                }

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    ignoreDemoRow("普通子节点 1", 40f, rgba(210, 230, 210))
                    ignoreDemoRow("普通子节点 2", 40f, rgba(210, 220, 245))
                    ignoreDemoRow("普通子节点 3", 40f, rgba(245, 225, 200))

                    // IgnoreFlex 子节点：ignore=true 时铺满容器但不占空间；false 时退回流内占空间
                    object : RectNode(this), IgnoreFlex {
                        override val ignore: Boolean get() = ignoreOn
                        override fun argPosition(direction: Direction): Float = if(ignoreOn)0f else super.argPosition(direction)

                        override val argWidth: LayoutSize
                            get() = if (ignoreOn) LayoutSize(420f, false) else LayoutSize(420f, false)
                        override val argHeight: LayoutSize
                            get() = if (ignoreOn) LayoutSize(200f, false) else LayoutSize(40f, false)

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
                                    if (ignoreOn) "ignore=ON：铺满但不占空间" else "ignore=OFF：回到流内占空间"
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

private fun StateHolder<Node, List<Node>>.ignoreDemoRow(
    text: String,
    height: Float,
    color: ColorInt,
) {
    object : RectNode(this) {
        override val argWidth: LayoutSize get() = LayoutSize(420f, false)
        override val argHeight: LayoutSize get() = LayoutSize(height, false)

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

/**
 * 运行时用 Skia 生成一张测试位图并�?decodeImage 解码路径�? */
private fun makeTestImage(w: Int, h: Int): PlatformImage? {
    return try {
        val surface = Surface.makeRasterN32Premul(w, h)
        val c = surface.canvas
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), org.jetbrains.skia.Paint().apply {
            color = rgba(40, 120, 220)
        })
        c.drawCircle(w / 2f, h / 2f, w / 3f, org.jetbrains.skia.Paint().apply {
            color = rgba(250, 210, 60)
        })
        val data = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)
        decodeImage(data!!.bytes)
    } catch (e: Throwable) {
        println("生成测试图片失败--$e")
        null
    }
}
