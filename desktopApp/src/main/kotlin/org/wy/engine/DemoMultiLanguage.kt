package org.wy.engine

import com.wy.mve.StateHolder

/**
 * 多语言横排混排 demo。
 *
 * 英文、中文、日文、韩文在同一行内混排，
 * 展示 Paragraph API 的跨语言 shaping 能力。
 */
fun demoMultiLanguage(context: StateHolder<Node>) {

    object : RichTextNode(context) {
        override val spans: List<RichTextSpan> get() = listOf(
            RichTextSpan("English ", RichTextStyle(fontSize = 18f, fontWeight = 700, color = rgba(0, 0, 180))),
            RichTextSpan("Hello World! ", RichTextStyle(fontSize = 16f)),
            RichTextSpan("中文 ", RichTextStyle(fontSize = 18f, fontWeight = 700, color = rgba(200, 0, 0))),
            RichTextSpan("你好世界！ ", RichTextStyle(fontSize = 16f)),
            RichTextSpan("日本語 ", RichTextStyle(fontSize = 18f, fontWeight = 700, color = rgba(0, 140, 0))),
            RichTextSpan("こんにちは世界！ ", RichTextStyle(fontSize = 16f)),
            RichTextSpan("한국어 ", RichTextStyle(fontSize = 18f, fontWeight = 700, color = rgba(160, 0, 160))),
            RichTextSpan("안녕하세요 세계!", RichTextStyle(fontSize = 16f))
        )
    }

    object : RichTextNode(context) {
        override val spans: List<RichTextSpan> get() = listOf(
            RichTextSpan(
                "Each language with its own color and weight. " +
                "中文混排测试，可以跨越多种文字系统。 " +
                "日本語も問題ありません。 " +
                "한국어도 잘 보입니다.",
                RichTextStyle(fontSize = 15f)
            )
        )
    }
}
