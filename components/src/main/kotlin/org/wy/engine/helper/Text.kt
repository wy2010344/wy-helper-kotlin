package org.wy.engine.helper

import com.wy.mve.StateHolder
import org.wy.engine.ColorInt
import org.wy.engine.Node
import org.wy.engine.WrappedTextNode

/**
 * 单行文本节点工厂：宽度自适应内容，常用于组件 label。
 *
 * 样式值读 [Theme] 或由调用方直接指定；若需换行 / 省略 / 对齐等能力，直接用 [WrappedTextNode]。
 *
 * [color] 必须是 lambda：argChildren 只构建一次，直接传值会把构建那一刻的主题色
 * 冻结进节点；lambda 在段落 memo 里求值，主题翻转时注册的依赖触发段落重算换色。
 */
fun StateHolder<Node, List<Node>>.text(
    text: () -> String,
    size: Float = 13f,
    color: () -> ColorInt,
    weight: Int = 400,
): WrappedTextNode = object : WrappedTextNode(this) {
    override val autoWidth: Boolean get() = true
    override val text: String get() = text()
    override val fontSize: Float get() = size
    override val color: ColorInt get() = color()
    override val fontWeight: Int get() = weight
}
