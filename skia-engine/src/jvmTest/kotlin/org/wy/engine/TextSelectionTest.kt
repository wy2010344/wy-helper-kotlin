package org.wy.engine

import com.wy.mve.StateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 文本选择与中文输入的行为约束测试。
 *
 * 保护两条关键回归：
 * 1. 所有文本区域（RichTextNode 及其子类）都支持点击定位与拖拽选择，
 *    而不是只有编辑器（EditableTextNode）能选。
 * 2. 中文（IME）输入能精确定位光标，且非焦点编辑器不得抢占输入框焦点
 *    打断输入法。
 */
class TextSelectionTest {

    private class TestText(
        context: StateHolder<Node, List<Node>>,
        override val text: String
    ) : WrappedTextNode(context) {
        override val autoWidth: Boolean get() = true
    }

    private fun createEnv(): Triple<TestStateHolder<Node, List<Node>>, TestEngineGlobal, Renderer> {
        val stateHolder = TestStateHolder<Node, List<Node>>()
        val engineGlobal = TestEngineGlobal()
        val renderer = Renderer(null)
        stateHolder.provide(engineGlobalContext, engineGlobal)
        stateHolder.provide(selectionManagerContext, engineGlobal.selectionManager)
        return Triple(stateHolder, engineGlobal, renderer)
    }

    private fun mouseDownCapture(node: Node, x: Float, y: Float, engineGlobal: TestEngineGlobal, shift: Boolean = false) {
        engineGlobal.shift = shift
        val e = PointerEvent(type = PointerType.Down, x = x, y = y)
        node.onPointerDownCapture(e)
    }

    // ---------- 1. 所有文本区域可选择 ----------

    @Test
    fun testPlainTextSupportsClickAndDragSelection() {
        val (sh, g, _) = createEnv()
        val node = TestText(sh, "Hello World")

        // 点击文本起点
        mouseDownCapture(node, 0f, 0f, g)
        assertTrue(node.hasSelection == false, "刚点击（无拖动）不应产生选区")

        // 按住拖到文字末尾：通过捕获的 move 回调驱动拖拽选择
        g.simulatePointerMove(9999f, 0f)
        assertTrue(node.hasSelection, "拖动后应产生选区")
        assertEquals("Hello World", node.selectionText())
    }

    @Test
    fun testPlainTextSupportsShiftClickExtend() {
        val (sh, g, _) = createEnv()
        val node = TestText(sh, "Hello World")

        mouseDownCapture(node, 0f, 0f, g)
        assertNull(node.selectionText())

        // Shift+点击第 6 个字符附近扩展选区
        mouseDownCapture(node, 100f, 0f, g, shift = true)
        val text = node.selectionText()
        assertTrue(text != null && text.isNotEmpty(), "Shift 点击应从锚点扩展到点击处")
        assertTrue(node.hasSelection)
    }

    @Test
    fun testPlainTextSelectAllAndReadSelection() {
        val (sh, g, _) = createEnv()
        val node = TestText(sh, "Hello World")

        // 普通文本实现 Selectable：可被 SelectionManager 登记并全选
        g.selectionManager.select(node)
        g.selectionManager.selectAll()
        assertTrue(node.hasSelection)
        assertEquals("Hello World", g.selectionManager.selectedText)
    }

    // ---------- 2. 中文输入精确定位 ----------

    @Test
    fun testChineseCommittedInputPrecisePosition() {
        val (sh, _, renderer) = createEnv()
        val ed = EditableTextNode(sh)

        // 模拟平台输入法提交的 committed 字符（逐字走 keyPress）
        ed.handleKey(KeyEvent('中', KeyCode.Unknown, false, false, false, false))
        ed.handleKey(KeyEvent('文', KeyCode.Unknown, false, false, false, false))
        assertEquals("中文", ed.text)

        // 精确回到中间再插入，验证光标定位不是简单 append
        ed.moveLeft()
        ed.handleKey(KeyEvent('间', KeyCode.Unknown, false, false, false, false))
        assertEquals("中间文", ed.text)

        // 中文支持撤销
        ed.handleKey(KeyEvent('z', KeyCode.Unknown, true, false, false, false))
        assertEquals("中文", ed.text)
    }

    // ---------- 3. 非焦点编辑器不得打断输入法 ----------

    @Test
    fun testNonFocusedEditorDoesNotHideActiveOverlay() {
        val (sh, g, _) = createEnv()
        val a = EditableTextNode(sh)
        val b = EditableTextNode(sh)

        // 焦点在 a，a 激活输入法输入框（声明式：activeEditor + inputOverlay 数据就绪）
        g.focused = a
        a.updateFocusOverlay()
        assertEquals(a, g.activeEditor, "a 聚焦后应成为活跃编辑器")
        assertTrue(a.inputOverlay() != null, "活跃编辑器的 inputOverlay 应返回输入框数据")

        // b 非焦点，重绘时不得抢走 a 的输入法输入框
        b.updateFocusOverlay()
        assertEquals(a, g.activeEditor, "非焦点编辑器不得抢占活跃输入框")
        assertTrue(a.inputOverlay() != null, "a 的输入框数据不应被 b 清掉")

        // a 失焦时才隐藏输入法输入框
        g.focused = null
        a.updateFocusOverlay()
        assertEquals(null, g.activeEditor, "a 失焦后应清空活跃编辑器")
        assertTrue(a.inputOverlay() == null, "a 失焦后 inputOverlay 应为空")

        // 清理全局 activeEditor，避免污染其他测试
        g.activeEditor = null
    }

    // ---------- 4. 输入框位置是源状态的纯派生（无中间信号） ----------

    private class TestEditable(
        context: StateHolder<Node, List<Node>>
    ) : EditableTextNode(context) {
        override val autoWidth: Boolean get() = true
    }

    @Test
    fun testOverlayPositionDerivesFromCursorMovement() {
        val (sh, g, _) = createEnv()
        val ed = TestEditable(sh)

        // 聚焦并激活输入框，输入文本后光标在末尾
        g.focused = ed
        ed.updateFocusOverlay()
        ed.handleKey(KeyEvent('H', KeyCode.Unknown, false, false, false, false))
        ed.handleKey(KeyEvent('e', KeyCode.Unknown, false, false, false, false))
        val end = ed.inputOverlay()
        assertTrue(end != null, "活跃编辑器的 inputOverlay 应返回输入框数据")

        // 光标移到开头：位置应立刻派生变化，无需任何手动推送
        ed.moveHome()
        val start = ed.inputOverlay()
        assertTrue(start != null, "光标移动后 inputOverlay 仍应有数据")
        assertTrue(start.x < end.x, "光标在开头时 overlay 应比末尾更靠左")

        // 插入文字改变内容：位置继续派生更新
        ed.handleKey(KeyEvent('X', KeyCode.Unknown, false, false, false, false))
        val after = ed.inputOverlay()
        assertTrue(after != null)
        assertTrue(after.x > start.x, "插入字符后 overlay 应随内容右移")

        // 清理全局 activeEditor，避免污染其他测试
        g.activeEditor = null
    }
}
