package org.wy.engine

import com.wy.mve.StateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 单行输入框排版契约（对应搜索框两个历史缺陷）：
 * - 占位文本必须与正文走同一段落、同一套 padding → 位置/行高完全一致；
 * - singleLine 必须真的"不折行"（此前只挡了 Enter，长文本仍会软换行溢出输入框）；
 * - 溢出时靠水平滚动让光标可见，而不是把文字折到框外。
 */
class SingleLineLayoutTest {

    private class TestEditor(
        context: StateHolder<Node, List<Node>>,
        private val single: Boolean
    ) : EditableTextNode(context) {
        override val singleLine: Boolean get() = single
        override val argWidth: LayoutSize get() = LayoutSize(120f, false)

        fun lineCount(): Int = paragraph?.getLineMetrics()?.size ?: 0
        fun lineHeight(): Float = paragraph?.height ?: 0f

        /** 首字符字形矩形，用于比较"占位"与"正文"是否落在同一位置。 */
        fun firstGlyph(): TextRect? = paragraph?.getRectsForRange(0, 1, RectStyle.TIGHT)?.firstOrNull()
        fun offsetX(): Float = textOffsetX
    }

    private fun editor(single: Boolean, text: String = "", placeholder: String = ""): TestEditor {
        val holder = TestStateHolder<Node, List<Node>>()
        val global = TestEngineGlobal()
        holder.provide(engineGlobalContext, global)
        holder.provide(selectionManagerContext, global.selectionManager)
        return TestEditor(holder, single).also {
            it.text = text
            it.placeholder = placeholder
        }
    }

    private val longText = "搜索一个比较长的关键词看看会不会换行"

    @Test
    fun singleLineNeverWraps() {
        val multi = editor(single = false, text = longText)
        val single = editor(single = true, text = longText)

        assertTrue(multi.lineCount() > 1, "多行编辑器应折行，实际 ${multi.lineCount()} 行")
        assertEquals(1, single.lineCount(), "单行编辑器不允许软换行")
        assertTrue(
            single.lineHeight() < multi.lineHeight(),
            "单行高度应只有一行：${single.lineHeight()} vs ${multi.lineHeight()}"
        )
    }

    @Test
    fun placeholderSharesTextGeometry() {
        val holder = "搜索 · Ctrl+K"
        val withPlaceholder = editor(single = true, placeholder = holder)
        val withText = editor(single = true, text = holder)

        val a = withPlaceholder.firstGlyph()
        val b = withText.firstGlyph()
        assertTrue(a != null && b != null, "两种状态都应构建出段落")
        assertEquals(b!!.top, a!!.top, "占位与正文的首行顶部必须一致")
        assertEquals(b.bottom, a.bottom, "占位与正文的字形高度必须一致")
        assertEquals(b.left, a.left, "占位与正文的起点必须一致")
    }

    @Test
    fun overflowScrollsToKeepCaretVisible() {
        val ed = editor(single = true, text = longText)
        ed.moveCursorTo(0)
        assertEquals(0f, ed.offsetX(), "光标在行首时无偏移")

        ed.moveCursorTo(longText.length)
        assertTrue(ed.offsetX() > 0f, "光标超出盒宽后应水平滚动，实际 ${ed.offsetX()}")
    }
}
