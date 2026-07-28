package org.wy.engine

import com.wy.mve.StateHolder

/**
 * RTL (Right-To-Left) 文字 demo。
 *
 * 演示阿拉伯文、希伯来文等从右向左书写的语言。
 * Paragraph API 自动处理 bidi 算法和 HarfBuzz shaping。
 */
fun demoRTLText(context: StateHolder<Node>) {

    // 阿拉伯文
    object : RichTextNode(context) {
        override val spans: List<RichTextSpan> get() = listOf(
            RichTextSpan(
                "مرحبا بالعالم",
                RichTextStyle(fontSize = 22f, fontWeight = 700, color = rgba(0, 100, 160))
            )
        )
    }

    // 希伯来文
    object : RichTextNode(context) {
        override val spans: List<RichTextSpan> get() = listOf(
            RichTextSpan(
                "שלום עולם",
                RichTextStyle(fontSize = 22f, fontWeight = 700, color = rgba(140, 0, 100))
            )
        )
    }

    // 混合 LTR + RTL：英文 + 阿拉伯文
    object : RichTextNode(context) {
        override val spans: List<RichTextSpan> get() = listOf(
            RichTextSpan("Hello ", RichTextStyle(fontSize = 18f, fontWeight = 700)),
            RichTextSpan(
                "مرحبا",
                RichTextStyle(fontSize = 18f, fontWeight = 700, color = rgba(0, 100, 160))
            ),
            RichTextSpan(" World", RichTextStyle(fontSize = 18f, fontWeight = 700))
        )
    }

    // 混合 LTR + RTL：中文 + 希伯来文
    object : RichTextNode(context) {
        override val spans: List<RichTextSpan> get() = listOf(
            RichTextSpan("你好 ", RichTextStyle(fontSize = 18f, color = rgba(200, 0, 0))),
            RichTextSpan(
                "שלום",
                RichTextStyle(fontSize = 18f, fontWeight = 700, color = rgba(140, 0, 100))
            ),
            RichTextSpan(" 世界", RichTextStyle(fontSize = 18f, color = rgba(200, 0, 0)))
        )
    }
}
