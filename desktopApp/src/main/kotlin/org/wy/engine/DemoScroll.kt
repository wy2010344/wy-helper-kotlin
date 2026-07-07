package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.engine.layout.Flex

/**
 * 演示 ScrollNode 滚动容器。
 *
 * 在固定大小的视口内放置多行文本，
 * 超出视口的部分被裁剪，可通过鼠标滚轮滚动查看。
 */
fun demoScroll(context: StateHolder<Node>) {
    val eg = context.consume(engineGlobalContext)!!
    object : ScrollNode(context) {
        override val viewportWidth: Float get() = 400f
        override val viewportHeight: Float get() = 150f
        override val showScrollbar: Boolean get() = true

        override fun buildContent(holder: StateHolder<Node>) {
            holder.provide(engineGlobalContext, eg)
            object : WrappedTextNode(holder) {
                override val text: String
                    get() = (1..20).joinToString(" ") { "Line$it: Hello World! 中文测试。" }
                override val fontSize: Float get() = 16f
                override val wrappingWidth: Float get() = 380f
            }
        }
    }
}
