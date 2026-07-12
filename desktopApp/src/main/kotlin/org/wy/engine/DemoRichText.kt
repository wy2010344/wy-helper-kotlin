package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.mve.StateHolder
import org.wy.engine.helper.flex
import org.wy.engine.helper.rect
import org.wy.engine.layout.LayoutSize
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue

/**
 * 演示 RichTextNode 富文本渲染。
 *
 * 多个 Span 具有不同的 fontSize / fontWeight / color，
 * 并支持点击拖拽选中文本。
 */
fun demoRichText(context: StateHolder<Node>) {
    context.flex(
        direction = Direction.y,
        gap = 10f,
        alignItem = AlignItem.stretch,
        size = {
            when (it) {
                Direction.x -> LayoutSize(300f,false)
                else -> sizeFromChildren(it)
            }
        },
        provideSize = {
            when(it){
                Direction.x -> true
                Direction.y -> false
            }
        }
    ) {
        rect()
        rect()
        var text by createSignal("")
      object : WrappedTextNode(this) {
            override val text: String get() = text
            override val fontSize: Float get() = 13f
            override val color: ColorInt get() = rgba(0, 128, 0)
        }
        object : RichTextNode(this) {
            override val spans: List<RichTextSpan>
                get() = listOf(
                    RichTextSpan("Hello ", fontSize = 22f, fontWeight = 700),
                    RichTextSpan("World! ", fontSize = 16f, color = rgba(200, 0, 0)),
                    RichTextSpan("This is ", fontSize = 14f),
                    RichTextSpan(
                        "rich text",
                        fontSize = 18f,
                        fontWeight = 700,
                        color = rgba(0, 0, 200)
                    ),
                    RichTextSpan(
                        " demo. You can click and drag to select text across different styles.\n",
                        fontSize = 14f
                    ),
                    RichTextSpan(
                        "标题 ",
                        fontSize = 28f,
                        fontWeight = 700,
                        color = rgba(180, 60, 0)
                    ),
                    RichTextSpan("正文内容 ", fontSize = 14f),
                    RichTextSpan(
                        "强调 ",
                        fontSize = 16f,
                        fontWeight = 700,
                        color = rgba(0, 140, 0)
                    ),
                    RichTextSpan("斜体提示", fontSize = 14f, color = rgba(120, 120, 120))
                )

            override fun mouseUp(e: MouseEvent) {
                val sel = selectionText
                text = if (sel != null) "Selected: \"$sel\"" else ""
            }
        }

    }
}
