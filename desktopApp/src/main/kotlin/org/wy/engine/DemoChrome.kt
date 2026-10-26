package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.helper.ButtonBase
import org.wy.engine.helper.DropdownBase
import org.wy.engine.gallery.G_MUTED
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.GrowChild
import org.wy.engine.layout.LayoutDirection
import org.wy.lib.StoreRef
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.memo

// ════════════════════════════════════════════════════
// Chrome 式动态多 tab（状态机 + 标签条，自 DemoGrand 拆出）
// ════════════════════════════════════════════════════

/** 关闭按钮 hover 底色（Chrome 式红）。 */
private val CHROME_RED = rgba(232, 17, 35)

/**
 * 窗口控制接口：无边框（frameless）窗口由 SkiaApp 包装实现，
 * tab 条空白拖拽 / 双击最大化 / 右上三键经此驱动；
 * shot / audit 等无窗口场景传 null——tab 条不渲染拖拽热区与三键（截图布局不变）。
 */
internal interface WindowControls {
    /** 空白处按下：开始移动窗口（最大化中按下先还原）。 */
    fun beginDrag()

    /** 双击空白：最大化 / 还原切换。 */
    fun toggleMaximize()

    fun minimize()

    fun close()
}

/** 一个标签页：同一页型可开多个实例，id 唯一标识实例。 */
data class ChromeTab(val id: Long, val type: DemoTab)

/**
 * 标签页状态机：[tabs] 持列表、[activeId] 持激活 id，[active] 同步激活页型
 * （页面 hide / 状态栏仍读它，签名不变）。列表操作满足 Chrome 语义：
 * 最后一个 tab 不可关、关激活取左邻（无则右邻）、页型允许重复。
 */
internal class ChromeTabs(
    initial: List<ChromeTab>,
    val activeId: StoreRef<Long>,
    val active: StoreRef<DemoTab>,
) {
    val tabs: StoreRef<List<ChromeTab>> = createSignal(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

    /** 聚焦已存在的 tab；id 不存在时忽略（如点击已关闭 tab 的 × 后事件冒泡）。 */
    fun focusTab(id: Long) {
        val t = tabs.value.firstOrNull { it.id == id } ?: return
        activeId.value = t.id
        active.value = t.type
    }

    /** 页型已有 tab 则聚焦、没有才新建（侧栏导航入口）。 */
    fun openTab(type: DemoTab) {
        val exist = tabs.value.firstOrNull { it.type == type }
        if (exist != null) focusTab(exist.id) else newTab(type)
    }

    /** 总是新建并激活（「+」菜单 / Ctrl+T，允许重复页型）。 */
    fun newTab(type: DemoTab) {
        val t = ChromeTab(nextId++, type)
        tabs.value = tabs.value + t
        activeId.value = t.id
        active.value = t.type
    }

    /** 关闭 tab：最后一个不关；关掉激活时取左邻、无则右邻。 */
    fun closeTab(id: Long) {
        val list = tabs.value
        if (list.size <= 1) return
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return
        val remain = list.filter { it.id != id }
        tabs.value = remain
        if (activeId.value == id) {
            val next = remain.getOrNull(i - 1) ?: remain.first()
            activeId.value = next.id
            active.value = next.type
        }
    }

    /** 聚焦第 index 个 tab（Ctrl+1..9 越界忽略）。 */
    fun focusNth(index: Int) {
        tabs.value.getOrNull(index)?.let { focusTab(it.id) }
    }
}

/**
 * 顶部标签条（约 36f 高，插在 buildGrandDemo 首位）：
 * tab 增删走 renderForEach（key=id），激活 tab 高亮为卡片底，
 * 末尾「+」经 dropdown（复用 8 方向浮层）列出全部页型新建。
 * [win] 非空（无边框窗口）时，「+」右侧接空白拖拽热区 + 右上三键。
 */
internal fun StateHolder<Node, List<Node>>.dTabBar(
    chrome: ChromeTabs,
    win: WindowControls? = null
): RectNode =
    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.x
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignItem: AlignItem get() = AlignItem.center
        override val alignFix: Boolean get() = true
        override val argHeight: LayoutSize get() = LayoutSize(36f, false)
        override val gap: Float get() = 4f

        override fun draw(canvas: PlatformCanvas) {
            fillOuterRect(canvas, BG)
            canvas.drawLine(0f, outerHeight - 1f, outerWidth, outerHeight - 1f, BORDER, 1f)
            super.draw(canvas)
        }

        override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
            if (direction == Direction.x) 8f else 0f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            // 列表在 memo 内现读登记依赖：增删 / 激活切换按 key=id 重建对应项
            renderForEach({ callback ->
                chrome.tabs.value.forEach { t -> callback(t.id, t) }
            }) { _, each ->
                dTab(chrome, each.value)
            }
            dNewTabBtn(chrome)
            if (win != null) {
                val ctl = win
                // 空白拖拽热区：撑满「+」之后的剩余宽度（tab / 「+」是其左侧兄弟，
                // 按下不会冒泡进来），按下移动窗口、双击最大化
                object : RectNode(this), GrowChild {
                    override fun argGrow(direction: Direction): Float =
                        if (direction == Direction.x) 1f else 0f
                    override val argHeight: LayoutSize get() = LayoutSize(30f, false)

                    override fun onPointerDown(e: PointerEvent) = ctl.beginDrag()
                    override fun onPointerDoubleClick(e: PointerEvent) = ctl.toggleMaximize()
                }
                // 右上三键：最小化 / 最大化 / 关闭（关闭 hover 红底）
                dWinBtn(onClick = { ctl.minimize() }) { canvas, w, h, fg ->
                    canvas.drawLine(w / 2f - 6f, h / 2f, w / 2f + 6f, h / 2f, fg, 1f)
                }
                dWinBtn(onClick = { ctl.toggleMaximize() }) { canvas, w, h, fg ->
                    val l = w / 2f - 5f
                    val t = h / 2f - 5f
                    val r = w / 2f + 5f
                    val b = h / 2f + 5f
                    canvas.drawLine(l, t, r, t, fg, 1f)
                    canvas.drawLine(r, t, r, b, fg, 1f)
                    canvas.drawLine(r, b, l, b, fg, 1f)
                    canvas.drawLine(l, b, l, t, fg, 1f)
                }
                dWinBtn(hoverRed = true, onClick = { ctl.close() }) { canvas, w, h, fg ->
                    canvas.drawLine(w / 2f - 5f, h / 2f - 5f, w / 2f + 5f, h / 2f + 5f, fg, 1.2f)
                    canvas.drawLine(w / 2f + 5f, h / 2f - 5f, w / 2f - 5f, h / 2f + 5f, fg, 1.2f)
                }
            }
        }
    }

/** 窗口控制钮（右上三键）：普通 hover 灰底、关闭 hover 红底（红底时字白），[glyph] 画在钮心。 */
private fun StateHolder<Node, List<Node>>.dWinBtn(
    hoverRed: Boolean = false,
    onClick: () -> Unit,
    glyph: (canvas: PlatformCanvas, w: Float, h: Float, fg: ColorInt) -> Unit
): ButtonBase = object : ButtonBase(this) {
    override val focusOrder: Int? = null
    override val argWidth: LayoutSize get() = LayoutSize(44f, false)
    override val argHeight: LayoutSize get() = LayoutSize(28f, false)

    override fun onClick() = onClick()

    override fun drawContent(canvas: PlatformCanvas) {
        val red = hoverRed && hovered
        if (hovered) fillOuterRoundRect(canvas, 4f, if (red) CHROME_RED else G_MUTED)
        glyph(canvas, outerWidth, outerHeight, if (red) rgba(255, 255, 255) else TEXT2)
    }
}

/** 单个标签页：标题 + × 关闭热区，激活 / hover 两态底色。 */
private fun StateHolder<Node, List<Node>>.dTab(chrome: ChromeTabs, tab: ChromeTab): ButtonBase =
    object : ButtonBase(this) {
        override val focusOrder: Int? = null
        override val argWidth: LayoutSize get() = LayoutSize(136f, false)
        override val argHeight: LayoutSize get() = LayoutSize(30f, false)
        override val gap: Float get() = 6f
        // 标题贴左、关闭钮贴右（between），而非整体居中
        override val directionJustify: DirectionJustify get() = DirectionJustify.between

        /** 激活态：绘制底色与标题样式共用（memo 信号变化触发重绘）。 */
        private val on by memo { chrome.activeId.value == tab.id }

        override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
            if (direction == Direction.x) 12f else 0f

        override fun onClick() = chrome.focusTab(tab.id)

        override fun drawContent(canvas: PlatformCanvas) {
            when {
                on -> fillOuterRoundRect(canvas, 8f, CARD)
                hovered -> fillOuterRoundRect(canvas, 8f, G_MUTED)
            }
        }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            // 标题色 / 字重在 getter 内读 on：argChildren 一次性，激活切换靠 getter 响应
            object : WrappedTextNode(this) {
                override val autoWidth: Boolean get() = true
                override val text: String get() = tab.type.label
                override val fontSize: Float get() = 12f
                override val color: ColorInt get() = if (on) TEXT else TEXT2
                override val fontWeight: Int get() = if (on) 600 else 450
            }
            dTabClose(chrome, tab)
        }
    }

/** 关闭热区：拦截冒泡，点 × 不再触发父 tab 的聚焦。 */
private fun StateHolder<Node, List<Node>>.dTabClose(chrome: ChromeTabs, tab: ChromeTab): ButtonBase =
    object : ButtonBase(this) {
        override val focusOrder: Int? = null
        override val argWidth: LayoutSize get() = LayoutSize(18f, false)
        override val argHeight: LayoutSize get() = LayoutSize(18f, false)

        override fun onPointerClick(e: PointerEvent) {
            e.stopPropagation()
            chrome.closeTab(tab.id)
        }

        override fun drawContent(canvas: PlatformCanvas) {
            if (hovered) fillOuterRoundRect(canvas, 5f, BORDER)
        }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            // × 两条斜线
            object : RectNode(this) {
                override val argWidth: LayoutSize get() = LayoutSize(8f, false)
                override val argHeight: LayoutSize get() = LayoutSize(8f, false)

                override fun draw(canvas: PlatformCanvas) {
                    canvas.drawLine(0f, 0f, 8f, 8f, TEXT2, 1.4f)
                    canvas.drawLine(8f, 0f, 0f, 8f, TEXT2, 1.4f)
                }
            }
        }
    }

/** 「+」新建按钮：下拉列出全部页型，允许与已开 tab 重复。 */
private fun StateHolder<Node, List<Node>>.dNewTabBtn(chrome: ChromeTabs): ButtonBase =
    object : ButtonBase(this) {
        override val focusOrder: Int? = null
        override val argWidth: LayoutSize get() = LayoutSize(24f, false)
        override val argHeight: LayoutSize get() = LayoutSize(24f, false)

        override fun onClick() {
            val anchor = this
            g.appendPop { pop ->
                object : DropdownBase(this, anchor) {
                    override fun onDismiss() {
                        g.removePop(pop)
                    }

                    override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
                        DemoTab.values().forEach { type ->
                            object : ButtonBase(this) {
                                override val focusOrder: Int? = null
                                override val argHeight: LayoutSize get() = LayoutSize(30f, false)

                                override fun onClick() {
                                    chrome.newTab(type)
                                    g.removePop(pop)
                                }

                                override fun drawContent(canvas: PlatformCanvas) {
                                    if (hovered) fillOuterRoundRect(canvas, 6f, G_MUTED)
                                }

                                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                                    dLabel({ type.label }, 12f, { TEXT }, 500)
                                }
                            }
                        }
                    }
                }
            }
        }

        override fun drawContent(canvas: PlatformCanvas) {
            if (hovered) fillOuterRoundRect(canvas, 6f, G_MUTED)
        }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            dLabel({ "+" }, 16f, { TEXT2 }, 500)
        }
    }
