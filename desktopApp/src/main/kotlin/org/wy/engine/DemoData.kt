package org.wy.engine

// ════════════════════════════════════════════════════
// 数据模型
// ════════════════════════════════════════════════════
internal data class Activity(
    val id: Long,
    val title: String,
    val done: Boolean,
    val time: String
)

internal val CHART_DATA = listOf(42f, 68f, 55f, 90f, 74f, 62f, 81f)
internal val CHART_DAYS = listOf("一", "二", "三", "四", "五", "六", "日")

internal fun defaultActivities() = listOf(
    Activity(1, "设计焦点系统", true, "09:12"),
    Activity(2, "合并 drawChildren", true, "昨天"),
    Activity(3, "重写 ImageNode 尺寸", true, "昨天"),
    Activity(4, "写 04 图片与绘制 文档", false, "周二"),
    Activity(5, "调研 Flutter 焦点模型", false, "周一"),
    Activity(6, "验证 Tab 遍历 focusOrder", true, "周一"),
    Activity(7, "修 EditableText 光标定位", false, "周日"),
    Activity(8, "跑性能测试", false, "周日"),
    Activity(9, "给 ScrollBar 加拖拽", true, "周六"),
    Activity(10, "接入剪贴板", true, "周六"),
    Activity(11, "字体回退测试", false, "周五"),
    Activity(12, "做复杂 demo", false, "今天"),
    Activity(13, "写 07 多平台 文档", true, "周四"),
    Activity(14, "RTL 混排验证", false, "周三")
)

internal fun defaultNotes() =
    "# 复杂 Demo 说明\n" +
        "\n" +
        "用这个区域验证多行编辑：\n" +
        "- 按 Enter 换行，光标自动跟随\n" +
        "- 首行以 # 开头，预览里变成标题\n" +
        "- 以 TODO 开头的行会高亮\n" +
        "\n" +
        "TODO: 再添加一些示例\n" +
        "Tab 键可以切走焦点，Ctrl+Z / Y 撤销重做。"

internal fun previewSpans(text: String): List<RichTextSpan> {
    val out = mutableListOf<RichTextSpan>()
    text.lines().forEach { line ->
        val style = when {
            line.startsWith("# ") -> RichTextStyle(fontSize = 16f, fontWeight = 700, color = ACCENT)
            line.trimStart().startsWith("TODO") -> RichTextStyle(fontSize = 14f, fontWeight = 600, color = RED)
            else -> RichTextStyle(fontSize = 14f, color = TEXT)
        }
        if (line.isEmpty()) {
            out.add(RichTextSpan("\n", style))
        } else {
            out.add(RichTextSpan(line, style))
            out.add(RichTextSpan("\n", RichTextStyle(fontSize = 14f, color = TEXT)))
        }
    }
    return out
}
