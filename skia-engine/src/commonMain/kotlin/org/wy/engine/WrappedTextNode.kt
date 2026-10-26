package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.lib.getValue
import org.wy.signal.createSignal
import org.wy.signal.memo
import org.wy.signal.setValue
import org.wy.signal.getValue
import kotlin.math.max
import kotlin.math.min

enum class WordBreak {
    PHRASE, BREAK_WORD, ANY_CHAR
}

open class WrappedTextNode(
    context: StateHolder<Node,List<Node>>
) : RichTextNode(context) {
    open val text: String = ""
    open val fontFamily: String? = null
    open val fontSize: Float = 16f
    open val fontWeight: Int = 400
    // 默认文字色不写死：走核心中性默认色（TextDefaults），组件包换主题时回写同步，昼/夜切换自动换色
    open val color: ColorInt get() = TextDefaults.text
    open val lineHeightMultiplier: Float =1.4f

    open val letterSpacing: Float=0f
    open val wordSpacing: Float=0f
    open val italic: Boolean = false

    override val maxLines: Int = Int.MAX_VALUE
    override val ellipsis: String = "\u2026"
    override val textAlign: TextAlign = TextAlign.START

    override val spans: List<RichTextSpan>
        get() = listOf(RichTextSpan(text, RichTextStyle(
            fontFamily,
            fontSize,
            fontWeight,
            italic,
            color,
            letterSpacing,
            wordSpacing,
            lineHeightMultiplier
        )))
}
