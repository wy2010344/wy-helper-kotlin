package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.signal.createSignal

/**
 * 测试 \n 强制换行在多行文本中的表现。
 */
fun demoForcedBreak(context: StateHolder<Node>) {
    val baseText = createSignal(
        "Hello World! This is a wrapped text demo. " +
        "You can click and drag to select text across multiple lines. " +
        "LongWordWithoutSpacesWillAlsoBreak at character boundaries. " +
        "中文字符也可以正常换行和选择。"
    )
    val feedback = createSignal("")

    object : WrappedTextNode(context) {
        override val text: String get() = "Forced line break demo:"
        override val fontSize: Float get() = 14f
        override val color: ColorInt get() = rgba(100, 100, 100)
    }

    object : WrappedTextNode(context) {
        override val text: String
            get() = baseText.value + "\n--- forced ---\nLine after forced break."
        override val fontSize: Float get() = 15f
        override fun mouseUp(e: MouseEvent) {
            val sel = selectionText
            feedback.value = if (sel != null) "Selected: \"$sel\"" else ""
        }
    }

    object : WrappedTextNode(context) {
        override val text: String get() = feedback.value
        override val fontSize: Float get() = 13f
        override val color: ColorInt get() = rgba(0, 128, 0)
    }
}
