package org.wy.engine

import com.wy.mve.StateHolderWithNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 显示层 tab 展开回归测试：
 *
 * SkParagraph 无 tabstop 实现，字体通常没有 '\t' 字形——裸 tab 直通段落
 * 会被整形为 notdef 方块。修复策略：显示文本中把 '\t' 展开为固定空格串，
 * 同时升级双向索引映射（逻辑 ⇄ 显示），保证光标定位、选区、组合区间
 * 在展开后的坐标空间中仍然正确。
 */
class EditableTabTest {

    /** 单编辑器环境（桥接 protected 的显示变换与索引映射）。 */
    private class Env(val obscure: Boolean = false) {
        lateinit var editor: EditorProbe

        val renderer: Renderer = object : Renderer(null) {
            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                editor = EditorProbe(this)
            }
        }

        /** 暴露被测契约的测试探针。 */
        open class EditorProbe(context: StateHolderWithNode<Node, List<Node>>) : EditableTextNode(context) {
            fun spansText(): String = displaySpans().joinToString("") { it.text }
            fun dispToLogic(p: Int): Int = displayToLogicIndex(p)
            fun logicToDisp(p: Int): Int = logicToDisplayIndex(p)
        }

        init {
            renderer.children
            editor.obscureText = obscure
            if (editor.text.isNotEmpty()) editor.moveCursorTo(editor.text.length)
        }

        /** 以业务身份写入逻辑文本（绕过键盘路由）。 */
        fun put(text: String) {
            editor.text = text
        }

        fun spansText(): String = editor.spansText()
    }

    // ===== 显示文本不含裸 tab：行首 / 行中均展开 =====

    @Test
    fun displaySpansExpandTabsEverywhere() {
        val env = Env()
        env.put("\tabc")
        assertFalse(spansOf(env).contains('\t'), "行首 tab 应被展开")
        assertTrue(spansOf(env).startsWith("    "), "行首 tab 应展开为固定空格")

        env.put("ab\tcd")
        assertFalse(spansOf(env).contains('\t'), "行中 tab 同样展开")

        env.put("no tabs")
        assertEquals("no tabs", spansOf(env), "无 tab 文本原样通过")
    }

    private fun spansOf(env: Env): String = env.spansText()

    // ===== 双向索引映射：tab 计入展开宽度 =====

    @Test
    fun indexMappingRoundTrip() {
        val env = Env()
        val ed = env.editor
        env.put("a\tb") // 显示 "a␣␣␣␣b"（tab 展开 4 空格）

        assertEquals(0, ed.logicToDisp(0))
        assertEquals(1, ed.logicToDisp(1), "'a' 之后")
        assertEquals(5, ed.logicToDisp(2), "tab 占 4 格，'b' 前显示位为 5")
        assertEquals(6, ed.logicToDisp(3), "末尾")

        assertEquals(0, ed.dispToLogic(0))
        assertEquals(1, ed.dispToLogic(1))
        assertEquals(2, ed.dispToLogic(2), "落在展开区中间 → 吸附到 tab 之后")
        assertEquals(2, ed.dispToLogic(5), "展开区末尾同样是 tab 之后")
        assertEquals(3, ed.dispToLogic(6))

        // 往返一致：每个逻辑位置经显示空间折返后不变
        for (logic in 0..3) {
            assertEquals(logic, ed.dispToLogic(ed.logicToDisp(logic)))
        }
    }

    @Test
    fun indexMappingMultipleTabs() {
        val env = Env()
        val ed = env.editor
        env.put("\t\tx") // 每个前导 tab 贡献 4 显示格 → 'x' 前显示位 8

        assertEquals(8, ed.logicToDisp(2))
        assertEquals(2, ed.dispToLogic(7), "第二个展开区内吸附")
        assertEquals(2, ed.dispToLogic(8))
        assertEquals(3, ed.dispToLogic(9))
    }

    // ===== 掩码模式不受影响：逐簇圆点、映射保持 1:1 =====

    @Test
    fun obscureModeUnaffected() {
        val env = Env(obscure = true)
        val ed = env.editor
        env.put("a\tb")

        val shown = env.spansText()
        assertFalse(shown.contains('\t'))
        assertEquals("•••", shown.take(3), "掩码按字素簇替换，不展开空格")
        assertEquals(3, ed.logicToDisp(3), "掩码下映射仍为簇计数")
        assertEquals(2, ed.dispToLogic(2))
    }

    // ===== 富文本子类：分段切片后同样整体展开 =====

    @Test
    fun richSubclassExpandsAcrossSegments() {
        lateinit var rich: RichProbe
        val renderer = object : Renderer(null) {
            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                rich = RichProbe(this)
            }
        }
        renderer.children
        rich.text = "ab\tcd"
        rich.styleRange(0, 2, RichTextStyle(fontSize = 24f)) // 分两段："ab" | "\tcd"

        assertFalse(rich.spansText().contains('\t'), "富文本分段内 tab 也必须展开")
        assertTrue(rich.spansText().startsWith("ab    "), "段边界处展开保持拼接顺序")

        // 映射基于全文计算，分段不影响（tab 在索引 2，贡献 +3 显示格）
        assertEquals(8, rich.logicToDisp(5))
        assertEquals(5, rich.dispToLogic(8))
    }

    /** 富文本探针：桥接 protected 成员。 */
    open class RichProbe(context: StateHolderWithNode<Node, List<Node>>) : RichEditableTextNode(context) {
        fun spansText(): String = displaySpans().joinToString("") { it.text }
        fun dispToLogic(p: Int): Int = displayToLogicIndex(p)
        fun logicToDisp(p: Int): Int = logicToDisplayIndex(p)
    }
}

