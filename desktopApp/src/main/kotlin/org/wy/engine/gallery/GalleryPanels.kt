package org.wy.engine.gallery

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
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
import org.wy.signal.memo

// ════════════════════════════════════════════════════
// 展示页后两行卡片（自 DemoGallery.galleryContent 拆出）：
// 第四行 弹层 / 标签页；第五行 表格 / 分隔线与骨架屏。
// ════════════════════════════════════════════════════

/**
 * 第四、五行卡片。[narrow] 走 lambda 通道（断点现读），[tab] 为
 * 标签页选中态（页面级信号，跨子树重建存活）。
 */
internal fun StateHolderWithNode<Node, List<Node>>.galleryPanels(
    narrow: () -> Boolean,
    tab: StoreRef<Int>
) {
    // ── 第四行：弹层 / 标签页 ────────────────────────
    gCardRow(narrow,
        {
            gCard(
                "Dialog & Toast 弹层",
                "模态圈定焦点，轻提示整层不拦截命中"
            ) {
                gRow(gap = 8f) {
                    gButton("打开对话框", BtnKind.Outline, small = true, focusOrder = 151) {
                        val g = consume(engineGlobalContext)!!
                        g.appendPop { pop ->
                            dialog(onClose = { g.removePop(pop) }, width = 330f) {
                                gText("确认删除？", 15f, TEXT, 700)
                                gText("删除后不可恢复，该操作会同时移除关联的 12 条记录。", 12f, TEXT2, wrap = true)
                                gRow(gap = 8f, justify = DirectionJustify.end) {
                                    gButton("取消", BtnKind.Secondary, small = true) {
                                        g.removePop(pop)
                                    }
                                    gButton("确认删除", BtnKind.Destructive, small = true) {
                                        g.removePop(pop)
                                    }
                                }
                            }
                        }
                    }
                    gButton("显示 Toast", BtnKind.Secondary, small = true, focusOrder = 152) {
                        toast(durationMs = 2200L) {
                            gText("已保存到本地 · 2 秒后自动消失", 12.5f, rgba(255, 255, 255))
                        }
                    }
                }
                gText("Esc / 点遮罩关闭；对话框打开期间 Tab 不会跑出面板。", 10.5f, PLACEHOLDER, wrap = true)
            }
        },
        {
            gCard(
                "Tabs 标签页",
                "下划线指示器随选中项移动"
            ) {
                gTabs(
                    selected = { tab.value },
                    onSelect = { tab.value = it },
                    items = listOf("概览", "成员", "计费"),
                    focusOrderOffset = 161
                )
                gText(
                    when (tab.value) {
                        0 -> "概览：项目进度 68%，本周新增 12 条记录。"
                        1 -> "成员：3 人协作中，2 个待处理邀请。"
                        else -> "计费：专业版 · 下次扣费 11 月 12 日。"
                    },
                    11.5f, TEXT2, wrap = true
                )
            }
        }
    )

    // ── 第五行：表格 / 分隔线与骨架屏 ──────────────────
    gCardRow(narrow,
        {
            gCard(
                "Table 表格",
                "定宽单元格右对齐数值，分隔线划分表头与行"
            ) {
                // 表头
                gRow(gap = 8f, height = 24f, align = AlignItem.stretch) {
                    gTableCell("项目", 96f, TEXT2, 600)
                    gTableCell("状态", 56f, TEXT2, 600)
                    gTableCell("数值", 68f, TEXT2, 600, TextAlign.END)
                }
                gSeparator()
                // 数据行
                gRow(gap = 8f, height = 26f, align = AlignItem.stretch) {
                    gTableCell("项目 A", 96f)
                    gTableCell("已上线", 56f, GREEN, 500)
                    gTableCell("12", 68f, TEXT, 550, TextAlign.END)
                }
                gSeparator()
                gRow(gap = 8f, height = 26f, align = AlignItem.stretch) {
                    gTableCell("项目 B", 96f)
                    gTableCell("灰度中", 56f, AMBER, 500)
                    gTableCell("48", 68f, TEXT, 550, TextAlign.END)
                }
                gSeparator()
                gRow(gap = 8f, height = 26f, align = AlignItem.stretch) {
                    gTableCell("项目 C", 96f)
                    gTableCell("规划中", 56f, TEXT2, 500)
                    gTableCell("7", 68f, TEXT, 550, TextAlign.END)
                }
            }
        },
        {
            gCard(
                "Separator & Skeleton 分隔线与骨架屏",
                "分隔线横向拉伸；骨架屏模拟加载占位"
            ) {
                gText("账户设置", 12f, TEXT, 600)
                gSeparator()
                gText("资料与安全", 11.5f, TEXT2)
                gSeparator()
                // 骨架屏：头像 + 标题 + 多行文本占位
                gRow(gap = 10f, height = 34f) {
                    gSkeleton(34f, 34f, 17f)
                    gSkeleton(12f, 110f)
                }
                gSkeleton(11f)
                gSkeleton(11f)
                gSkeleton(11f, 180f)
            }
        }
    )
}
