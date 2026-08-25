package org.wy.engine

import com.wy.mve.StateHolderWithNode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 只读富文本（RichTextNode）tab 显示回归测试。
 *
 * markdown 解析出的 spans 常含行首 / 行内 tab；
 * 裸 tab 直通 Skia Paragraph 会整形为 notdef 方块。
 * 修复方向：在段落构建入口统一展开 + 索引映射计入展开宽度。
 */
class RichTextNodeTabTest {

    // ---------- 只读探针节点 ----------

    class ProbeNode(context: StateHolderWithNode<Node, List<Node>>) : RichTextNode(context) {
        /** 业务侧覆盖：模拟 markdown 解析器注入的分段。 */
        var testSpans: List<RichTextSpan> = emptyList()

        override val spans: List<RichTextSpan> get() = testSpans
        override val autoWidth = true // 段落布局用 Float.MAX_VALUE，免去 innerWidth 依赖

        /** 暴露 protected 成员供测试断言。 */
        fun pubParagraph(): PlatformParagraph? = paragraph
        fun pubFullText(): String = fullText
    }

    private class Env {
        lateinit var probeNode: ProbeNode
        val renderer: Renderer = object : Renderer(null) {
            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                probeNode = ProbeNode(this)
            }
        }
        init { renderer.children }
    }

    // ===== 只读富文本 spans 含 tab 时，段落应成功构建且展开后矩形有效 =====

    @Test
    fun readOnlyRichNodeExpandsTabsInParagraph() {
        val env = Env()
        env.probeNode.testSpans = listOf(RichTextSpan("\tX"))
        val p = env.probeNode.pubParagraph()
        assertNotNull(p, "段落应成功构建")
        // '\t' 展开为 4 空格，'X' 在显示位 4，取 [4,5) 矩形应有效
        val rects = p.getRectsForRange(4, 5, RectStyle.TIGHT)
        assertTrue(rects.isNotEmpty(), "展开后 X 的矩形应存在")
    }

    @Test
    fun multipleLeadingTabsExpand() {
        val env = Env()
        env.probeNode.testSpans = listOf(RichTextSpan("\t\tx"))
        val p = env.probeNode.pubParagraph() ?: return
        // 两个 tab 各 4 空格，'x' 在显示位 8
        val rects = p.getRectsForRange(8, 9, RectStyle.TIGHT)
        assertTrue(rects.isNotEmpty(), "两个 tab 后的字符应有有效矩形")
    }

    // ===== fullText（spans 拼接）应已展开 tab =====

    @Test
    fun fullTextExcludesRawTabs() {
        val env = Env()
        env.probeNode.testSpans = listOf(RichTextSpan("a\tb"))
        assertFalse(env.probeNode.pubFullText().contains('\t'), "fullText 应已展开 tab")
        assertTrue(env.probeNode.pubFullText() == "a    b")
    }
}
