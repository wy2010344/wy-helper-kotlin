package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.signal.createSignal

/**
 * 对比三种 WordBreak 模式的换行效果。
 */
fun demoWordBreak(context: StateHolder<Node>) {
    val text = createSignal(
        "Hello World! This is a wrapped text demo. " +
        "You can click and drag to select text across multiple lines. " +
        "LongWordWithoutSpacesWillAlsoBreak at character boundaries. " +
        "中文字符也可以正常换行和选择。"
    )
    val feedback = createSignal("")

    // BREAK_WORD
    object : WrappedTextNode(context) {
        override val text: String get() = "[BREAK_WORD]  " + text.value
        override val fontSize: Float get() = 14f
        override val wrappingWidth: Float get() = 400f
        override val wordBreak: WordBreak get() = WordBreak.BREAK_WORD
        override fun mouseUp(e: MouseEvent) {
            val sel = selectionText
            feedback.value = if (sel != null) "BREAK_WORD Sel: \"$sel\"" else ""
        }
    }

    // PHRASE
    object : WrappedTextNode(context) {
        override val text: String get() = "[PHRASE]  " + text.value
        override val fontSize: Float get() = 14f
        override val wrappingWidth: Float get() = 400f
        override val wordBreak: WordBreak get() = WordBreak.PHRASE
        override fun mouseUp(e: MouseEvent) {
            val sel = selectionText
            feedback.value = if (sel != null) "PHRASE Sel: \"$sel\"" else ""
        }
    }

    // ANY_CHAR
    object : WrappedTextNode(context) {
        override val text: String get() = "[ANY_CHAR]  " + text.value
        override val fontSize: Float get() = 14f
        override val wrappingWidth: Float get() = 400f
        override val wordBreak: WordBreak get() = WordBreak.ANY_CHAR
        override fun mouseUp(e: MouseEvent) {
            val sel = selectionText
            feedback.value = if (sel != null) "ANY_CHAR Sel: \"$sel\"" else ""
        }
    }

    // Selection feedback
    object : WrappedTextNode(context) {
        override val text: String get() = feedback.value
        override val fontSize: Float get() = 13f
        override val color: ColorInt get() = rgba(0, 128, 0)
        override val wrappingWidth: Float get() = 400f
    }
}
