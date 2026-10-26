package org.wy.engine

import org.wy.signal.createSignal

/**
 * 核心中性文本默认色：[WrappedTextNode.color] / [EditableTextNode.cursorColor] /
 * [EditableTextNode.placeholderColor] 的取值来源。
 *
 * 核心包不感知具体组件主题（Theme 属组件包）：默认值取原浅色主题数值，与迁移前逐像素等价；
 * 组件包切换主题时经 setter 回写同步，昼/夜切换自动换肤。
 */
object TextDefaults {
    /** 正文默认色（组件主题 text）。 */
    var text: ColorInt
        get() = textSignal.get()
        set(value) { textSignal.set(value) }

    /** 占位符默认色（组件主题 placeholder）。 */
    var placeholder: ColorInt
        get() = placeholderSignal.get()
        set(value) { placeholderSignal.set(value) }

    private val textSignal = createSignal(rgba(30, 41, 59))
    private val placeholderSignal = createSignal(rgba(148, 163, 184))
}
