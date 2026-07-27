package org.wy.engine

import com.wy.mve.StateHolder

/**
 * RTL (Right-To-Left) 文字 demo。
 *
 * 演示阿拉伯文、希伯来文等从右向左书写的语言。
 * 每个 span 通过 direction 属性标记 RTL，
 * drawText 时从节点右边缘向左排列。
 *
 * 注意：当前 demo 中的阿拉伯文/希伯来文为示例文本，
 * 实际排版还需 Unicode BiDi 算法支持连字和字形替换。
 */
fun demoRTLText(context: StateHolder<Node>) {

    // 阿拉伯文
    object : RichTextNode(context) {
        override val spans: List<RichTextSpan> get() = listOf(
            RichTextSpan(
                "مرحبا بالعالم",
                fontSize = 22f, fontWeight = 700,
                color = rgba(0, 100, 160),
                direction = TextDirection.RTL
            )
        )
    }

    // 希伯来文
    object : RichTextNode(context) {
        override val spans: List<RichTextSpan> get() = listOf(
            RichTextSpan(
                "שלום עולם",
                fontSize = 22f, fontWeight = 700,
                color = rgba(140, 0, 100),
                direction = TextDirection.RTL
            )
        )
    }

    // 混合 LTR + RTL：英文 + 阿拉伯文
    object : RichTextNode(context) {
        override val spans: List<RichTextSpan> get() = listOf(
            RichTextSpan("Hello ", fontSize = 18f, fontWeight = 700),
            RichTextSpan(
                "مرحبا",
                fontSize = 18f, fontWeight = 700,
                color = rgba(0, 100, 160),
                direction = TextDirection.RTL
            ),
            RichTextSpan(" World", fontSize = 18f, fontWeight = 700)
        )
    }

    // 混合 LTR + RTL：中文 + 希伯来文
    object : RichTextNode(context) {
        override val spans: List<RichTextSpan> get() = listOf(
            RichTextSpan("你好 ", fontSize = 18f, color = rgba(200, 0, 0)),
            RichTextSpan(
                "שלום",
                fontSize = 18f, fontWeight = 700,
                color = rgba(140, 0, 100),
                direction = TextDirection.RTL
            ),
            RichTextSpan(" 世界", fontSize = 18f, color = rgba(200, 0, 0))
        )
    }
}
