package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.signal.createSignal

/**
 * 多语言横排混排 demo。
 *
 * 英文、中文、日文、韩文在同一行内混排，展示 RichTextNode 的跨语言渲染能力。
 */
fun demoMultiLanguage(context: StateHolder<Node>) {
    val feedback = createSignal("")

    object : RichTextNode(context) {
        override val spans: List<RichTextSpan> get() = listOf(
            RichTextSpan("English ", fontSize = 18f, fontWeight = 700, color = rgba(0, 0, 180)),
            RichTextSpan("Hello World! ", fontSize = 16f),
            RichTextSpan("中文 ", fontSize = 18f, fontWeight = 700, color = rgba(200, 0, 0)),
            RichTextSpan("你好世界！ ", fontSize = 16f),
            RichTextSpan("日本語 ", fontSize = 18f, fontWeight = 700, color = rgba(0, 140, 0)),
            RichTextSpan("こんにちは世界！ ", fontSize = 16f),
            RichTextSpan("한국어 ", fontSize = 18f, fontWeight = 700, color = rgba(160, 0, 160)),
            RichTextSpan("안녕하세요 세계!", fontSize = 16f)
        )
        override fun mouseUp(e: MouseEvent) {
            val sel = selectionText
            feedback.value = if (sel != null) "Selected: \"$sel\"" else ""
        }
    }

    object : RichTextNode(context) {
        override val spans: List<RichTextSpan> get() = listOf(
            RichTextSpan(
                "Each language with its own color and weight. " +
                "中文混排测试，可以跨越多种文字系统。 " +
                "日本語も問題ありません。 " +
                "한국어도 잘 보입니다.",
                fontSize = 15f
            )
        )
    }

    object : WrappedTextNode(context) {
        override val text: String get() = feedback.value
        override val fontSize: Float get() = 13f
        override val color: ColorInt get() = rgba(0, 128, 0)
    }
}
