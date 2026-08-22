package org.wy.demo.text

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.demo.helper.row
import org.wy.engine.*
import org.wy.engine.helper.Button
import org.wy.engine.helper.ButtonVariant
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue

/**
 * 跨组件文本选择 Demo：
 * 相邻的多个文本节点由 SelectionManager 组织成一份逻辑文档，
 * 支持跨节点拖选 / Ctrl+A 全选 / Ctrl+C 聚合复制（详见 docs/06）。
 */
fun main() {
    val paras = mutableListOf<WrappedTextNode>()

    object : SkiaApp(900, 720), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val directionJustify: DirectionJustify get() = DirectionJustify.center
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 8f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            sectionTitle("跨组件文本选择 Demo")
            hint("拖动跨段选择 · 双击选词 · Ctrl+A 全选 · Ctrl+C 复制")
            separator()
            article(paras)
            separator()
            selectionPanel(paras)
        }
    }
}

/** 一篇"文档"：标题 + 多段正文 + 混排富文本，全部是相邻的可选节点。 */
private fun StateHolder<Node, List<Node>>.article(paras: MutableList<WrappedTextNode>) {
    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val argWidth: LayoutSize get() = LayoutSize(560f, true)
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 6f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            object : WrappedTextNode(this) {
                override val autoWidth: Boolean get() = true
                override val text: String get() = "引擎发布说明 v0.9"
                override val fontSize: Float get() = 20f
                override val fontWeight: Int get() = 700
                override val color: ColorInt get() = rgba(30, 30, 50)
            }
            object : WrappedTextNode(this) {
                override val text: String get() = "本次更新引入了跨组件文本选择能力。所有文本节点由 SelectionManager 统一协调，组织成一份逻辑文档，选区不再局限于单个节点内部。"
                override val fontSize: Float get() = 14f
                override val color: ColorInt get() = rgba(60, 60, 75)
                override val lineHeightMultiplier: Float get() = 1.5f
            }.also { paras.add(it) }
            paragraph(
                "在任一段落上按下鼠标并拖动，即可从当前位置连续选中到其他段落；配合 Ctrl+A 可以一键全选整篇文档，Ctrl+C 会按文档序聚合各节点的选中片段并写入剪贴板。",
                paras
            )
            paragraph("只读文本与输入框共享同一套选择模型：编辑器聚焦时其内部选区会同步到全局会话，失焦后交还，两种形态可以无缝互相拖选。", paras)
            object : RichTextNode(this) {
                override val spans: List<RichTextSpan> get() = listOf(
                    RichTextSpan("提示：", RichTextStyle(fontSize = 13f, fontWeight = 700, color = rgba(0, 90, 180))),
                    RichTextSpan("本段是 RichTextNode 混排样式，同样参与跨节点选择。", RichTextStyle(fontSize = 13f, color = rgba(80, 80, 100))),
                )
            }
        }
    }
}

private fun StateHolder<Node, List<Node>>.paragraph(content: String, paras: MutableList<WrappedTextNode>) {
    object : WrappedTextNode(this) {
        override val text: String get() = content
        override val fontSize: Float get() = 14f
        override val color: ColorInt get() = rgba(60, 60, 75)
        override val lineHeightMultiplier: Float get() = 1.5f
    }.also { paras.add(it) }
}

/** 选中内容查看与操作面板：复制 / 全选 / 清除 / 编程式选区。 */
private fun StateHolder<Node, List<Node>>.selectionPanel(paras: List<WrappedTextNode>) {
    var display by createSignal("（尚未选中）")

    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.y
        override val argWidth: LayoutSize get() = LayoutSize(560f, true)
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 8f

        /** 编程式选中 [target] 中第一个 [word] 出现的位置（模拟搜索高亮）。 */
        fun selectWord(target: WrappedTextNode?, word: String): String {
            if (target == null) return "（段落未就绪）"
            val idx = target.text.indexOf(word)
            if (idx < 0) return "（未找到「$word」）"
            engineGlobal.selectionManager.select(target, idx, target, idx + word.length)
            return engineGlobal.selectionManager.selectedText ?: "（选中为空）"
        }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            row {
                object : Button(this@row) {
                    override val label: String get() = "复制选中内容"
                    override fun onClick() {
                        val sel = engineGlobal.selectionManager.selectedText
                        if (sel != null) {
                            clipboardSetText(sel)
                            display = sel
                        }
                    }
                }
                object : Button(this@row) {
                    override val label: String get() = "全选"
                    override val variant: ButtonVariant get() = ButtonVariant.Secondary
                    override fun onClick() { engineGlobal.selectionManager.selectAll() }
                }
                object : Button(this@row) {
                    override val label: String get() = "清除"
                    override val variant: ButtonVariant get() = ButtonVariant.Secondary
                    override fun onClick() {
                        engineGlobal.selectionManager.clear()
                        display = "（已清除）"
                    }
                }
            }
            row {
                hint("编程式选区（select API）：")
                object : Button(this@row) {
                    // 模拟"搜索定位"：编程式选中第二段中的指定文字
                    override val label: String get() = "定位「逻辑文档」"
                    override val variant: ButtonVariant get() = ButtonVariant.Secondary
                    override fun onClick() { display = selectWord(paras.getOrNull(0), "逻辑文档") }
                }
                object : Button(this@row) {
                    override val label: String get() = "定位「剪贴板」"
                    override val variant: ButtonVariant get() = ButtonVariant.Secondary
                    override fun onClick() { display = selectWord(paras.getOrNull(1), "剪贴板") }
                }
            }

            object : WrappedTextNode(this) {
                override val text: String get() = "最近操作结果：\n$display"
                override val fontSize: Float get() = 12f
                override val color: ColorInt get() = rgba(90, 90, 110)
            }
        }
    }
}
