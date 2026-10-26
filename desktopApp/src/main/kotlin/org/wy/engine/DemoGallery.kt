package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.helper.ButtonBase
import org.wy.engine.helper.DropdownBase
import org.wy.engine.helper.SimpleScrollNode
import org.wy.engine.helper.SwitchBase
import org.wy.engine.helper.dialog
import org.wy.engine.helper.slider
import org.wy.engine.helper.switch
import org.wy.engine.helper.textField
import org.wy.engine.helper.toast
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.GrowChild
import org.wy.engine.layout.LayoutDirection
import org.wy.lib.StoreRef
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.engine.gallery.*
import org.wy.signal.memo

// ════════════════════════════════════════════════════
// 页面
// ════════════════════════════════════════════════════
/** 卡片行断点：窗口内宽小于此值时两张卡片拆成上下两行（各自拉满宽度）。 */
private const val G_CARD_ROW_BREAKPOINT = 880f

/**
 * 一对卡片的排放：宽窗口同行（grow 平分宽度），窄窗口拆两行。
 *
 * 两行各只有一个 grow 子节点（卡片），remaing = 整行内宽 → 卡片拉满。
 */
internal fun StateHolderWithNode<Node, List<Node>>.gCardRow(
    narrow: () -> Boolean,
    first: StateHolderWithNode<Node, List<Node>>.() -> Unit,
    second: StateHolderWithNode<Node, List<Node>>.() -> Unit
) {
    // 拆行结构走 renderForEach：结构 if 若在 argChildren 里现读，是启动
    // 快照，跨断点缩放窗口不再重排；renderForEach 的 memo 内现读登记依赖，
    // 断点翻转时 key（Boolean）变化 → 销毁旧 gRow、按新结构重建
    renderForEach({ callback -> callback(narrow(), Unit) }) { isNarrow, _ ->
        if (!isNarrow) {
            gRow(gap = 14f, align = AlignItem.start) {
                first()
                second()
            }
        } else {
            gRow(gap = 14f, align = AlignItem.start) { first() }
            gRow(gap = 14f, align = AlignItem.start) { second() }
        }
    }
}

internal fun StateHolder<Node, List<Node>>.dGalleryPage(
    active: StoreRef<DemoTab>,
    searchSeed: String,
    windowWidth: () -> Float,
): RectNode = object : RectNode(this), FlexParam, GrowChild {
    override val hide: Boolean get() = active.value != DemoTab.COMPONENTS
    override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f

    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.y
    override val directionJustify: DirectionJustify get() = DirectionJustify.start
    override val alignItem: AlignItem get() = AlignItem.stretch
    override val alignFix: Boolean get() = true

    // ── 交互状态提升到页面级 ──
    // 内容子树会在窗口宽度变化时整体重建（galleryContent 里读了宽度信号），
    // createSignal 若放在子树里就会随重建丢失（输入框文字 / 勾选态归零）。
    // 提升到这里：节点只构造一次，信号跨重建存活。
    private val textValue = createSignal(searchSeed)
    private val noteValue = createSignal("")
    private val checkedA = createSignal(true)
    private val checkedB = createSignal(false)
    private val radio = createSignal(1)
    private val notif = createSignal(true)
    private val marketing = createSignal(false)
    private val level = createSignal(0.62f)
    private val plan = createSignal("专业版")
    private val tab = createSignal(0)
    private val placementIdx = createSignal(0)

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        // 页面自己滚动：正好用来体验浮层滚动条
        object : SimpleScrollNode(this) {
            override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f
            override val contentGap: Float get() = 14f

            override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
                galleryContent(
                    narrow = { windowWidth() < G_CARD_ROW_BREAKPOINT },
                    textValue = textValue,
                    noteValue = noteValue,
                    checkedA = checkedA,
                    checkedB = checkedB,
                    radio = radio,
                    notif = notif,
                    marketing = marketing,
                    level = level,
                    plan = plan,
                    tab = tab,
                    placementIdx = placementIdx,
                )
            }
        }
    }
}

private fun StateHolderWithNode<Node, List<Node>>.galleryContent(
    narrow: () -> Boolean,
    textValue: StoreRef<String>,
    noteValue: StoreRef<String>,
    checkedA: StoreRef<Boolean>,
    checkedB: StoreRef<Boolean>,
    radio: StoreRef<Int>,
    notif: StoreRef<Boolean>,
    marketing: StoreRef<Boolean>,
    level: StoreRef<Float>,
    plan: StoreRef<String>,
    tab: StoreRef<Int>,
    placementIdx: StoreRef<Int>,
) {
    // 页头
    gCol(gap = 4f) {
        gRow(gap = 8f) {
            gText("组件库", 19f, { TEXT }, 700)
            gBadge("shadcn/ui 风格", BadgeKind.Secondary)
            gBadge("27 个组件", BadgeKind.Outline)
        }
        gText(
            "交互基类在 skia-engine，样式与变体在 demo 层——按下 / 悬停 / 聚焦 / 禁用四态齐全，Tab 可逐项遍历。",
            11.5f, { TEXT2 }, wrap = true
        )
    }

    // ── 第一行：按钮 / 徽标 ──────────────────────────
    gCardRow(narrow,
        {
            gCard(
                "Button 按钮",
                "5 个变体 × 2 个尺寸，宽度随内容自适应"
            ) {
                gRow(gap = 8f) {
                    gButton("主要按钮", BtnKind.Default, focusOrder = 101)
                    gButton("次要", BtnKind.Secondary, focusOrder = 102)
                    gButton("描边", BtnKind.Outline, focusOrder = 103)
                }
                gRow(gap = 8f) {
                    gButton("幽灵", BtnKind.Ghost, focusOrder = 104)
                    gButton("危险操作", BtnKind.Destructive, focusOrder = 105)
                    gButton("不可用", BtnKind.Default, enabled = false)
                }
                gRow(gap = 8f) {
                    gButton("小尺寸", BtnKind.Secondary, small = true, focusOrder = 106)
                    gButton("间距一致", BtnKind.Outline, small = true, focusOrder = 107)
                    gText("hover / 按下 / 焦点环", 10.5f, { PLACEHOLDER })
                }
            }
        },
        {
            gCard(
                "Badge & Avatar 徽标与头像",
                "徽标宽度由文本撑起，头像支持叠状态点"
            ) {
                gRow(gap = 6f) {
                    gBadge("默认", BadgeKind.Default)
                    gBadge("次要", BadgeKind.Secondary)
                    gBadge("描边", BadgeKind.Outline)
                }
                gRow(gap = 6f) {
                    gBadge("已完成", BadgeKind.Success)
                    gBadge("待处理", BadgeKind.Warning)
                    gBadge("已逾期", BadgeKind.Danger)
                }
                gRow(gap = 8f) {
                    gAvatar("WY", { ACCENT })
                    gAvatar("KT", { GREEN })
                    gAvatar("Z", { AMBER }, withDot = true)
                    gText("30px · 圆形裁切", 10.5f, { PLACEHOLDER })
                }
            }
        }
    )

    // ── 第二行：输入 / 选择 ──────────────────────────
    gCardRow(narrow,
        {
            gCard(
                "Input & Textarea 输入",
                "单行不折行、多行自动折行；聚焦时外扩焦点环"
            ) {
                gRow(gap = 8f) {
                    gInput(textValue, "请输入关键词…", focusOrder = 111)
                    gButton("搜索", BtnKind.Default, small = true, focusOrder = 112)
                }
                gTextarea(noteValue, "写点什么…（多行，可换行）", focusOrder = 113, height = 66f)
            }
        },
        {
            gCard(
                "Checkbox & Radio & Switch 选择",
                "三个基类共用 ButtonBase 的键盘与焦点语义"
            ) {
                gField("接收站内通知") {
                    gCheckbox({ checkedA.value }, { checkedA.value = it }, focusOrder = 121)
                }
                gField("订阅产品更新") {
                    gCheckbox({ checkedB.value }, { checkedB.value = it }, focusOrder = 122)
                }
                gRow(gap = 14f, height = 22f) {
                    gField("月度") { gRadio({ radio.value == 0 }, { radio.value = 0 }) }
                    gField("季度") { gRadio({ radio.value == 1 }, { radio.value = 1 }) }
                    gField("年度") { gRadio({ radio.value == 2 }, { radio.value = 2 }) }
                }
                gField("通知推送") {
                    gSwitch({ notif.value }, { notif.value = it }, focusOrder = 123)
                }
                gField("营销邮件") {
                    gSwitch({ marketing.value }, { marketing.value = it }, focusOrder = 124)
                }
            }
        }
    )

    // ── 第三行：滑杆 / 进度 / 下拉 ───────────────────
    gCardRow(narrow,
        {
            gCard(
                "Slider & Progress 滑杆与进度",
                "方向键可微调 5%，拖拽按像素换算"
            ) {
                gRow(gap = 10f) {
                    gText("专注强度", 12f, { TEXT }, 500)
                    slider({ level.value }, { level.value = it }, focusOrder = 131, grow = true)
                    gText("${(level.value * 100).toInt()}%", 11.5f, { TEXT2 })
                }
                gRow(gap = 8f, justify = DirectionJustify.between) {
                    gText("本周目标", 11.5f, { TEXT2 })
                    gText("${(level.value * 40).toInt()} / 40h", 11.5f, { TEXT2 }, 550)
                }
                gProgress({ level.value })
                gProgress({ level.value * 0.6f }, color = { GREEN })
                gProgress({ level.value * 0.3f }, color = { AMBER })
            }
        },
        {
            gCard(
                "Select 下拉选择",
                "8 方向放置，空间不足自动回退到对侧；点击外部 / Esc 关闭"
            ) {
                gRow(gap = 8f) {
                    gText("当前套餐", 12f, { TEXT2 })
                    gSelect(
                        plan,
                        listOf("免费版", "专业版", "团队版", "企业版"),
                        focusOrder = 141,
                        placement = { G_PLACEMENTS[placementIdx.value] },
                    )
                }
                // 8 方向切换（两行各 4 个）：renderForEach 读 placementIdx，
                // 点选后按键态重建按钮（选中态 Default，其余 Outline）
                listOf(0, 4).forEach { rowStart ->
                    renderForEach({ callback -> callback(placementIdx.value, Unit) }) { idx, _ ->
                        gRow(gap = 6f) {
                            for (i in rowStart until rowStart + 4) {
                                gButton(
                                    G_PLACEMENT_LABELS[i],
                                    if (idx == i) BtnKind.Default else BtnKind.Outline,
                                    small = true,
                                    focusOrder = 161 + i,
                                ) { placementIdx.value = i }
                            }
                        }
                    }
                }
                gRow(gap = 6f) {
                    gBadge("按 Esc 关闭", BadgeKind.Outline)
                    gBadge("点击外部关闭", BadgeKind.Outline)
                }
                gText("面板内可 Tab 遍历，关闭后焦点回到触发器。", 10.5f, { PLACEHOLDER }, wrap = true)
            }
        }
    )

    // ── 第四、五行（弹层 / 标签页 + 表格 / 骨架屏）──
    // 拆至 gallery/GalleryPanels.kt：仅依赖 narrow 与 tab，页面只做组装
    galleryPanels(narrow, tab)

    // ── 第六行（右键菜单 ContextMenu）──
    // 拆至 gallery/GalleryOverlay.kt：仅依赖 narrow，页面只做组装
    galleryOverlay(narrow)
}

