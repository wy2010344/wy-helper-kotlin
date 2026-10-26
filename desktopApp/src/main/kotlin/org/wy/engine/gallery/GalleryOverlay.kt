package org.wy.engine.gallery

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.helper.MenuItem
import org.wy.engine.helper.Placement
import org.wy.engine.helper.attachTooltip
import org.wy.engine.helper.showContextMenu
import org.wy.engine.helper.toast
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.getValue
import org.wy.signal.memo

// ════════════════════════════════════════════════════
// 展示页第六行卡片（自 DemoGallery.galleryContent 拆出）：
// 右键菜单（ContextMenu）演示——表格行右键、卡片区域右键。
// ════════════════════════════════════════════════════

/** 右键菜单演示动作反馈：把执行结果显示为轻提示。 */
private fun StateHolder<Node, List<Node>>.gMenuToast(msg: String) {
    toast(durationMs = 1800L) {
        gText(msg, 12.5f, { rgba(255, 255, 255) })
    }
}

/** 演示用右键菜单项：含快捷键提示、分隔线、危险项与禁用项。 */
private fun demoMenuItems(name: String): List<MenuItem> = listOf(
    MenuItem("打开「$name」", onClick = null, shortcut = null),
    MenuItem("复制", onClick = null, shortcut = "Ctrl+C"),
    MenuItem("移动到…", divided = true),
    MenuItem("重命名", shortcut = "F2"),
    MenuItem("删除「$name」", danger = true),
    MenuItem("归档", enabled = false, divided = true),
)

/**
 * 表格数据行：悬停高亮，右键在取点弹出菜单（动作经 Toast 反馈）。
 * 宽度经 stretch 拉伸到卡片内宽（父级为纵向卡片，安全）。
 */
private fun StateHolder<Node, List<Node>>.gContextMenuRow(
    name: String,
    status: String,
    statusColor: () -> ColorInt,
    size: String,
) {
    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.x
        // between（非 grow）→ 行宽从父级拉伸；标签居左、状态与数值居右
        override val directionJustify: DirectionJustify get() = DirectionJustify.between
        override val alignItem: AlignItem get() = AlignItem.center
        override val alignFix: Boolean get() = true
        override val gap: Float get() = 8f
        override val argHeight: LayoutSize get() = LayoutSize(30f, false)

        private val hovered by memo {
            engineGlobal.moveHitTest?.include(this) == true
        }

        override fun onPointerContextMenu(e: PointerEvent) {
            // 动作统一 Toast 反馈（菜单项点击时面板已拦截冒泡，由项自身关闭菜单）
            val items = demoMenuItems(name).map { item ->
                if (item.onClick == null && item.enabled) {
                    item.copy(onClick = { gMenuToast("「${item.label}」已执行（演示）") })
                } else if (item.danger) {
                    item.copy(onClick = { gMenuToast("已删除「$name」（演示）") })
                } else {
                    item
                }
            }
            showContextMenu(e, items)
        }

        override fun draw(canvas: PlatformCanvas) {
            if (hovered) fillOuterRoundRect(canvas, 6f, G_SUBTLE)
            super.draw(canvas)
        }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            gText(name, 12f, { TEXT }, 500)
            gText(status, 11.5f, statusColor, 500)
            gText(size, 11.5f, { TEXT2 }, 400)
        }
    }
}

/**
 * 卡片内的右键演示区域：圆角占位块，整块右键弹出菜单。
 * 高度固定、宽度拉伸（父级为纵向卡片，安全）。
 */
private fun StateHolder<Node, List<Node>>.gContextMenuArea() {
    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.y
        // 有定高 → justify=start（子节点顶端堆放），宽度拉伸到父级
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val alignFix: Boolean get() = true
        override val gap: Float get() = 6f
        override val argHeight: LayoutSize get() = LayoutSize(92f, false)

        override fun argPadding(direction: Direction, startEnd: StartEnd): Float = 12f

        private val hovered by memo {
            engineGlobal.moveHitTest?.include(this) == true
        }

        override fun onPointerContextMenu(e: PointerEvent) {
            showContextMenu(
                e,
                listOf(
                    MenuItem("刷新面板", onClick = { gMenuToast("面板已刷新（演示）") }),
                    MenuItem("复制链接", onClick = { gMenuToast("链接已复制（演示）") }, shortcut = "Ctrl+C"),
                    MenuItem("删除面板", divided = true, danger = true, onClick = { gMenuToast("面板已删除（演示）") }),
                )
            )
        }

        override fun draw(canvas: PlatformCanvas) {
            fillOuterRoundRect(canvas, 10f, if (hovered) G_MUTED else CARD)
            super.draw(canvas)
        }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            gRow(gap = 6f, justify = DirectionJustify.center) {
                gText("在此区域右键", 12f, { TEXT }, 600)
            }
            gRow(gap = 6f, justify = DirectionJustify.center) {
                gText("菜单锚定取点矩形，空间不足自动翻转 / 贴边", 10.5f, { PLACEHOLDER })
            }
        }
    }
}

/**
 * 第六行卡片。[narrow] 走 lambda 通道（断点现读），与 galleryPanels 同契约。
 */
internal fun StateHolderWithNode<Node, List<Node>>.galleryOverlay(narrow: () -> Boolean) {
    gCardRow(narrow,
        {
            gCard(
                "Context Menu 右键菜单",
                "表格行右键：分隔线分组、危险项红字、禁用项置灰、快捷键居右"
            ) {
                // 表头（与 Table 卡片同构）
                gRow(gap = 8f, height = 24f, align = AlignItem.stretch) {
                    gTableCell("名称", 96f, { TEXT2 }, 600)
                    gTableCell("状态", 56f, { TEXT2 }, 600)
                    gTableCell("大小", 68f, { TEXT2 }, 600, TextAlign.END)
                }
                gSeparator()
                gContextMenuRow("设计稿.sketch", "已同步", { GREEN }, "24.6 MB")
                gContextMenuRow("组件库.fig", "编辑中", { AMBER }, "8.1 MB")
                gContextMenuRow("素材归档.zip", "只读", { TEXT2 }, "112 MB")
                gText(
                    "右键任意一行试一试：点项执行并关闭，点空白 / Esc 关闭。",
                    10.5f, { PLACEHOLDER }, wrap = true
                )
            }
        },
        {
            gCard(
                "Card 区域右键",
                "整块区域右键，浮层按取点自动避让"
            ) {
                gContextMenuArea()
                gRow(gap = 6f) {
                    gBadge("点击外部关闭", BadgeKind.Outline)
                    gBadge("Esc 关闭", BadgeKind.Outline)
                }
            }
        }
    )

    // ── 第七行（Tooltip 悬浮提示）──
    gCardRow(narrow,
        {
            gCard(
                "Tooltip 悬浮提示",
                "悬停 400ms 显示提示，移出立即隐藏；整层命中穿透，hover 不抖动"
            ) {
                gRow(gap = 8f) {
                    gButton("悬停我", BtnKind.Secondary).attachTooltip { "保存草稿（Ctrl+S）" }
                    gBadge("徽标", BadgeKind.Secondary).attachTooltip { "徽标也能挂提示" }
                    gButton("次级按钮", BtnKind.Outline).attachTooltip {
                        "提示内容惰性读取，昼 / 夜样式跟随主题"
                    }
                }
                gText(
                    "按钮、徽标等任意 LayoutNode 都可挂载，可链式调用。",
                    10.5f, { PLACEHOLDER }, wrap = true
                )
            }
        },
        {
            gCard(
                "Tooltip 贴边避让",
                "面板复用 resolvePlacement：贴边放不下自动翻转，8 方向可指定"
            ) {
                gRow(gap = 8f) {
                    gButton("默认下方", BtnKind.Secondary)
                        .attachTooltip { "默认 BottomStart：锚点下方，放不下自动翻到上方" }
                    gButton("右侧放置", BtnKind.Outline)
                        .attachTooltip(placement = Placement.RightStart) {
                            "指定 RightStart：贴右缘放不下自动翻到左侧"
                        }
                }
                gText(
                    "与 Dropdown / Popover 同一套 8 方向 + 空间不足回退逻辑。",
                    10.5f, { PLACEHOLDER }, wrap = true
                )
            }
        }
    )
}
