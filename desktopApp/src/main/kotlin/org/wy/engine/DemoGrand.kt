package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import org.wy.engine.dashboard.DASH_ACCENT
import org.wy.engine.dashboard.dDashboardPage
import org.wy.engine.helper.navItem
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
    COMPONENTS("组件"),
    DASHBOARD("看板"),
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
                    dLabel({ if (toast.value.isEmpty()) "就绪" else toast.value }, 11f, { TEXT2 })
                }
            }
            dLabel(
                { "当前：${active.value.label}  ·  Ctrl+1..9 切换  ·  Ctrl+T 新建  ·  Ctrl+W 关闭  ·  Ctrl+N 动态  ·  Ctrl+K 搜索" },
                11f, { TEXT2 }
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

internal fun StateHolder<Node,List<Node>>.buildGrandDemo(
    initialSearch: String = "",
    initialTab: DemoTab = DemoTab.OVERVIEW,
    /** 当前窗口宽度（响应式读取）：组件库页据此在窄窗口拆分卡片行。 */
    windowWidth: () -> Float = { 0f },
    /** 当前窗口高度（响应式读取）：金融看板据此做盘口行数预算。 */
    windowHeight: () -> Float = { 0f },
    /** 窗口控制（无边框窗口专用）：null = 有边框 / shot / audit，tab 条不带拖拽与三键。 */
    win: WindowControls? = null,
) {
    val active = createSignal(initialTab)
    val activities = createSignal(defaultActivities())
    val notes = createSignal(defaultNotes())
    val toast = createSignal("")
    lateinit var searchField: EditableTextNode

    // Chrome 式动态多 tab：初始把目标页型排在首位（侧栏只属于 tabs[0]），
    // 其余按枚举序全开；active 始终同步激活 tab 的页型（页面 hide / 状态栏读它）
    val initialTabs = (listOf(initialTab) + DemoTab.values().filter { it != initialTab })
        .mapIndexed { i, t -> ChromeTab((i + 1).toLong(), t) }
    val chrome = ChromeTabs(initialTabs, createSignal(initialTabs[0].id), active)

    val g = consume(engineGlobalContext)!!
    val dKey = g.registerKeyPress { e ->
        when {
            e.ctrl && e.key in '1'..'9' -> chrome.focusNth(e.key - '1')
            e.ctrl && e.key == 't' -> chrome.newTab(
                DemoTab.values()[chrome.tabs.value.size % DemoTab.values().size]
            )
            e.ctrl && e.key == 'w' -> chrome.closeTab(chrome.activeId.value)
            e.ctrl && e.key == 'n' -> {
                activities.value = activities.value +
                    Activity(Date().time, "新任务 ${activities.value.size + 1}", false, "刚刚")
                toast.value = "已新增一条动态"
            }
            e.ctrl && e.key == 'k' -> searchField.requestFocus()
        }
    }
    addDestroy { dKey() }

    // 标签条（最首位，约 36f；无边框窗口带空白拖拽 + 右上三键）
    dTabBar(chrome, win)

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
                    dLabel({ "WY Engine" }, 15f, { TEXT }, 700)
                    dLabel({ "Desktop Framework Demo" }, 10f, { TEXT2 })
                }
            }

            object : RectNode(this), GrowChild {
                override fun argGrow(direction: Direction): Float =
                    if (direction == Direction.x) 1f else 0f
                override val argHeight: LayoutSize get() = LayoutSize(0f, false)
            }

            val searchText = createSignal(initialSearch)
            searchField = object : EditableTextNode(this) {
                override var text: String
                    get() = searchText.value
                    set(v) {
                        searchText.value = v
                    }

                override val fontSize: Float = 13f
                override val focusOrder: Int? = 0
                override val singleLine: Boolean = true
                override val placeholderColor: ColorInt get() = PLACEHOLDER
                override val argWidth: LayoutSize = LayoutSize(220f, false)
                override val argHeight: LayoutSize = LayoutSize(32f, false)

                // 占位文本交给编辑器自身排版：和正文同一个段落、同一套 padding / 字号，
                // 不再挂一个绝对定位的子节点去"猜"坐标（字号差 1px 就会错行）。
                init {
                    placeholder = "搜索 · Ctrl+K"
                }

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
            }

            // 主题快捷开关：label 走 lambda 活读 demoDark（argChildren 只构建一次），
            // 切换时段落重算出带新标签的按钮
            dButton(
                { if (demoDark.value) "浅色" else "深色" },
                { setDarkTheme(!demoDark.value) },
                primary = false, width = 64f, height = 30f
            )

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
                    dLabel({ "在线" }, 12f, { TEXT2 })
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
            // 侧栏（只属于第一个 tab：激活页不是 tabs[0] 时隐藏，内容区吃满宽度）
            object : RectNode(this), FlexParam {
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.y
                override val directionJustify: DirectionJustify get() = DirectionJustify.start
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val alignFix: Boolean get() = true
                override val argWidth: LayoutSize get() = LayoutSize(208f, false)
                override val gap: Float get() = 4f
                override val hide: Boolean
                    get() = chrome.activeId.value != chrome.tabs.value.firstOrNull()?.id

                override fun draw(canvas: PlatformCanvas) {
                    fillOuterRect(canvas, CARD)
                    canvas.drawLine(outerWidth - 1f, 0f, outerWidth - 1f, outerHeight, BORDER, 1f)
                    super.draw(canvas)
                }

                override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                    if (direction == Direction.x) 12f else 14f

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    dLabel({ "导航" }, 11f, { TEXT2 }, 600)
                    navItem(
                        label = { "概览" },
                        active = { active.value == DemoTab.OVERVIEW },
                        onClick = { chrome.openTab(DemoTab.OVERVIEW) },
                        focusOrder = 1,
                        iconColor = { ACCENT }
                    )
                    navItem(
                        label = { "笔记" },
                        active = { active.value == DemoTab.NOTES },
                        onClick = { chrome.openTab(DemoTab.NOTES) },
                        focusOrder = 2,
                        iconColor = { GREEN }
                    )
                    navItem(
                        label = { "组件" },
                        active = { active.value == DemoTab.COMPONENTS },
                        onClick = { chrome.openTab(DemoTab.COMPONENTS) },
                        focusOrder = 3,
                        iconColor = { ACCENT_HOVER }
                    )
                    navItem(
                        label = { "看板" },
                        active = { active.value == DemoTab.DASHBOARD },
                        onClick = { chrome.openTab(DemoTab.DASHBOARD) },
                        focusOrder = 4,
                        iconColor = { DASH_ACCENT }
                    )
                    navItem(
                        label = { "设置" },
                        active = { active.value == DemoTab.SETTINGS },
                        onClick = { chrome.openTab(DemoTab.SETTINGS) },
                        focusOrder = 5,
                        iconColor = { AMBER },
                        badge = { 3 }
                    )

                    object : RectNode(this), GrowChild {
                        override fun argGrow(direction: Direction): Float =
                            if (direction == Direction.y) 1f else 0f
                    }

                    dLabel({ "wy-engine · v0.1" }, 11f, { TEXT2 })
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
                    // 页面切换已由顶部标签条承担：页面仍自带 hide = active.value != 类型
                    dOverviewPage(active, activities) {
                        activities.value = activities.value +
                            Activity(Date().time, "新任务 ${activities.value.size + 1}", false, "刚刚")
                        toast.value = "已新增一条动态"
                    }
                    dNotesPage(active, notes)
                    dGalleryPage(active, "", windowWidth)
                    dDashboardPage(active, windowWidth, windowHeight)
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
        /** 完整 Chrome 式：无边框，tab 条承担移动 / 缩放 / 三键。 */
        override val frameless: Boolean get() = true

        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.y
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val alignFix: Boolean get() = true

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            buildGrandDemo(
                windowWidth = { windowWidth },
                windowHeight = { windowHeight },
                win = object : WindowControls {
                    override fun beginDrag() = beginWindowDrag()
                    override fun toggleMaximize() = toggleMaximizeWindow()
                    override fun minimize() = minimizeWindow()
                    override fun close() = closeWindow()
                }
            )
        }
    }
}
