package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import org.wy.engine.helper.navItem
import org.wy.engine.helper.segTabs
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.GrowChild
import org.wy.engine.layout.LayoutDirection
import org.wy.lib.StoreRef
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.memo
import java.util.Date

// ════════════════════════════════════════════════════
// 状态栏
// ════════════════════════════════════════════════════
enum class DemoTab(val label: String) {
    OVERVIEW("概览"),
    NOTES("笔记"),
    SETTINGS("设置")
}

internal fun StateHolder<Node,List<Node>>.dStatusBar(active: StoreRef<DemoTab>, toast: StoreRef<String>): RectNode =
    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.x
        override val directionJustify: DirectionJustify get() = DirectionJustify.between
        override val alignItem: AlignItem get() = AlignItem.center
        override val alignFix: Boolean get() = true
        override val argHeight: LayoutSize get() = LayoutSize(30f, false)

        override fun draw(canvas: PlatformCanvas) {
            fillOuterRect(canvas, CARD)
            canvas.drawLine(0f, 0f, outerWidth, 0f, BORDER, 1f)
            super.draw(canvas)
        }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            object : RectNode(this), FlexParam {
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.x
                override val alignItem: AlignItem get() = AlignItem.center
                override val gap: Float get() = 8f

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    object : RectNode(this) {
                        override val argWidth: LayoutSize = LayoutSize(8f, false)
                        override val argHeight: LayoutSize = LayoutSize(8f, false)

                        override fun draw(canvas: PlatformCanvas) {
                            fillOuterOval(canvas, if (toast.value.isEmpty()) GREEN else AMBER)
                        }
                    }
                    dLabel({ if (toast.value.isEmpty()) "就绪" else toast.value }, 11f, TEXT2)
                }
            }
            dLabel(
                { "当前：${active.value.label}  ·  Ctrl+1/2/3 切换  ·  Ctrl+N 新增  ·  Ctrl+K 搜索" },
                11f, TEXT2
            )
        }
    }

// ════════════════════════════════════════════════════
// 主布局
// ════════════════════════════════════════════════════
private fun makeLogo(): PlatformImage? {
    return try {
        val surface = Surface.makeRasterN32Premul(64, 64)
        val c = surface.canvas
        c.drawRRect(org.jetbrains.skia.RRect.makeXYWH(0f, 0f, 64f, 64f, 14f, 14f), org.jetbrains.skia.Paint().apply { color = ACCENT })
        c.drawCircle(32f, 24f, 12f, org.jetbrains.skia.Paint().apply { color = rgba(255, 255, 255) })
        c.drawRRect(org.jetbrains.skia.RRect.makeXYWH(16f, 42f, 32f, 10f, 5f, 5f), org.jetbrains.skia.Paint().apply { color = rgba(255, 255, 255) })
        val data = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)
        decodeImage(data!!.bytes)
    } catch (e: Throwable) {
        println("生成 logo 失败--$e")
        null
    }
}

private val logo = makeLogo()

private fun StateHolder<Node,List<Node>>.buildGrandDemo() {
    val active = createSignal(DemoTab.OVERVIEW)
    val activities = createSignal(defaultActivities())
    val notes = createSignal(defaultNotes())
    val toast = createSignal("")
    lateinit var searchField: EditableTextNode

    val g = consume(engineGlobalContext)!!
    val dKey = g.registerKeyPress { e ->
        when {
            e.ctrl && e.key == '1' -> active.value = DemoTab.OVERVIEW
            e.ctrl && e.key == '2' -> active.value = DemoTab.NOTES
            e.ctrl && e.key == '3' -> active.value = DemoTab.SETTINGS
            e.ctrl && e.key == 'n' -> {
                activities.value = activities.value +
                    Activity(Date().time, "新任务 ${activities.value.size + 1}", false, "刚刚")
                toast.value = "已新增一条动态"
            }
            e.ctrl && e.key == 'k' -> searchField.requestFocus()
        }
    }
    addDestroy { dKey() }

    // 顶栏
    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.x
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignItem: AlignItem get() = AlignItem.center
        override val alignFix: Boolean get() = true
        override val argHeight: LayoutSize get() = LayoutSize(56f, false)
        override val gap: Float get() = 12f

        override fun draw(canvas: PlatformCanvas) {
            fillOuterRect(canvas, CARD)
            canvas.drawLine(0f, outerHeight - 1f, outerWidth, outerHeight - 1f, BORDER, 1f)
            super.draw(canvas)
        }

        override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
            if (direction == Direction.x) 16f else 0f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            object : ImageNode(this) {
                override val image: PlatformImage? get() = logo
                override val size: LayoutSizeDirection get() = LayoutSizeDirection(Direction.x, 32f, true)
                override val radius: Float get() = 8f
            }

            object : RectNode(this), FlexParam {
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.y
                override val alignItem: AlignItem get() = AlignItem.start

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    dLabel({ "WY Engine" }, 15f, TEXT, 700)
                    dLabel({ "Desktop Framework Demo" }, 10f, TEXT2)
                }
            }

            object : RectNode(this), GrowChild {
                override fun argGrow(direction: Direction): Float =
                    if (direction == Direction.x) 1f else 0f
                override val argHeight: LayoutSize get() = LayoutSize(0f, false)
            }

            val searchText = createSignal("")
            searchField = object : EditableTextNode(this) {
                override var text: String
                    get() = searchText.value
                    set(v) {
                        searchText.value = v
                    }

                override val fontSize: Float = 13f
                override val focusOrder: Int? = 0
                override val argWidth: LayoutSize = LayoutSize(220f, false)
                override val argHeight: LayoutSize = LayoutSize(32f, false)

                private val g = engineGlobal
                private val hovered by memo { g.moveHitTest?.include(this) ?: false }

                override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                    if (direction == Direction.x) 10f else 5f

                override fun draw(canvas: PlatformCanvas) {
                    fillOuterRoundRect(canvas, 16f, BG)
                    strokeOuterRoundRect(
                        canvas, 16f,
                        when {
                            isFocused -> ACCENT
                            hovered -> BAR
                            else -> BORDER
                        }, 1f
                    )
                    super.draw(canvas)
                }

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    object : WrappedTextNode(this) {
                        override val hide: Boolean get() = searchText.value.isNotEmpty()
                        override val x: Float get() = paddingInlineStart
                        override val y: Float get() = paddingBlockStart
                        override val autoWidth: Boolean get() = true
                        override val text: String get() = "搜索 · Ctrl+K"
                        override val fontSize: Float get() = 12f
                        override val color: ColorInt get() = PLACEHOLDER
                    }
                }
            }

            object : RectNode(this), FlexParam {
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.x
                override val alignItem: AlignItem get() = AlignItem.center
                override val gap: Float get() = 6f

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    object : RectNode(this) {
                        override val argWidth: LayoutSize = LayoutSize(8f, false)
                        override val argHeight: LayoutSize = LayoutSize(8f, false)

                        override fun draw(canvas: PlatformCanvas) {
                            fillOuterOval(canvas, GREEN)
                        }
                    }
                    dLabel({ "在线" }, 12f, TEXT2)
                }
            }
        }
    }

    // 主体：侧栏 + 内容
    object : RectNode(this), FlexParam, GrowChild {
        override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f

        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.x
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val alignFix: Boolean get() = true

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            // 侧栏
            object : RectNode(this), FlexParam {
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.y
                override val directionJustify: DirectionJustify get() = DirectionJustify.start
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val alignFix: Boolean get() = true
                override val argWidth: LayoutSize get() = LayoutSize(208f, false)
                override val gap: Float get() = 4f

                override fun draw(canvas: PlatformCanvas) {
                    fillOuterRect(canvas, CARD)
                    canvas.drawLine(outerWidth - 1f, 0f, outerWidth - 1f, outerHeight, BORDER, 1f)
                    super.draw(canvas)
                }

                override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                    if (direction == Direction.x) 12f else 14f

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    dLabel({ "导航" }, 11f, TEXT2, 600)
                    navItem(
                        label = { "概览" },
                        active = { active.value == DemoTab.OVERVIEW },
                        onClick = { active.value = DemoTab.OVERVIEW },
                        focusOrder = 1,
                        iconColor = ACCENT
                    )
                    navItem(
                        label = { "笔记" },
                        active = { active.value == DemoTab.NOTES },
                        onClick = { active.value = DemoTab.NOTES },
                        focusOrder = 2,
                        iconColor = GREEN
                    )
                    navItem(
                        label = { "设置" },
                        active = { active.value == DemoTab.SETTINGS },
                        onClick = { active.value = DemoTab.SETTINGS },
                        focusOrder = 3,
                        iconColor = AMBER,
                        badge = { 3 }
                    )

                    object : RectNode(this), GrowChild {
                        override fun argGrow(direction: Direction): Float =
                            if (direction == Direction.y) 1f else 0f
                    }

                    dLabel({ "wy-engine · v0.1" }, 11f, TEXT2)
                }
            }

            // 内容区
            object : RectNode(this), FlexParam, GrowChild {
                override fun argGrow(direction: Direction): Float = if (direction == Direction.x) 1f else 0f

                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.y
                override val directionJustify: DirectionJustify get() = DirectionJustify.start
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val alignFix: Boolean get() = true
                override val gap: Float get() = 12f

                override fun draw(canvas: PlatformCanvas) {
                    fillOuterRect(canvas, BG)
                    super.draw(canvas)
                }

                override fun argPadding(direction: Direction, startEnd: StartEnd): Float = 16f

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    segTabs(
                        { active.value },
                        { active.value = it },
                        listOf(DemoTab.OVERVIEW to "概览", DemoTab.NOTES to "笔记", DemoTab.SETTINGS to "设置"),
                        focusOrderOffset = 10
                    )
                    dOverviewPage(active, activities) {
                        activities.value = activities.value +
                            Activity(Date().time, "新任务 ${activities.value.size + 1}", false, "刚刚")
                        toast.value = "已新增一条动态"
                    }
                    dNotesPage(active, notes)
                    dSettingsPage(active, toast)
                }
            }
        }
    }

    // 状态栏
    dStatusBar(active, toast)
}

fun main() {
    object : SkiaApp(1120, 740), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.y
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val alignFix: Boolean get() = true

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            buildGrandDemo()
        }
    }
}
