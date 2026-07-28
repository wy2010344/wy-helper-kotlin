package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue

/**
 * 演示 RichTextNode 富文本渲染。
 *
 * 多个 Span 具有不同的 fontSize / fontWeight / color，
 * 支持点击拖拽选中文本。
 * Paragraph API 自动处理 HarfBuzz shaping、bidi、换行、字体 fallback。
 */
fun demoRichText(context: StateHolder<Node>) {
    object : RectNode(context), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val argWidth: LayoutSize
            get() = LayoutSize(400f, true)
        override val alignFix: Boolean
            get() = true
        override val alignItem: AlignItem
            get() = AlignItem.stretch

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {

            val feedback = createSignal("")

            object : RichTextNode(this) {
                override val spans: List<RichTextSpan> get() = listOf(
                    RichTextSpan("Hello ", RichTextStyle(fontSize = 22f, fontWeight = 700)),
                    RichTextSpan("World! ", RichTextStyle(fontSize = 16f, color = rgba(200, 0, 0))),
                    RichTextSpan("This is ", RichTextStyle(fontSize = 14f)),
                    RichTextSpan(
                        "rich text",
                        RichTextStyle(fontSize = 18f, fontWeight = 700, color = rgba(0, 0, 200))
                    ),
                    RichTextSpan(" demo.\n", RichTextStyle(fontSize = 14f)),
                    RichTextSpan(
                        "مرحبا بالعالم ",
                        RichTextStyle(fontFamily = "Arial", fontSize = 20f, color = rgba(180, 0, 120))
                    ),
                    RichTextSpan(
                        "שלום עולם ",
                        RichTextStyle(fontFamily = "Arial", fontSize = 20f, color = rgba(0, 120, 180))
                    ),
                    RichTextSpan(
                        "标题 ",
                        RichTextStyle(fontSize = 28f, fontWeight = 700, color = rgba(180, 60, 0))
                    ),
                    RichTextSpan("正文内容 ", RichTextStyle(fontSize = 14f)),
                    RichTextSpan(
                        "强调 ",
                        RichTextStyle(fontSize = 16f, fontWeight = 700, color = rgba(0, 140, 0))
                    ),
                    RichTextSpan(
                        "斜体提示",
                        RichTextStyle(fontSize = 14f, color = rgba(120, 120, 120))
                    )
                )

                override fun mouseUp(e: MouseEvent) {
                    val sel = selectionText
                    feedback.value = if (sel != null) "Selected: \"$sel\"" else ""
                }
            }

            object : WrappedTextNode(this) {
                override val text: String get() = feedback.value
                override val fontSize: Float get() = 13f
                override val color: ColorInt get() = rgba(0, 128, 0)
            }
        }
    }
}
