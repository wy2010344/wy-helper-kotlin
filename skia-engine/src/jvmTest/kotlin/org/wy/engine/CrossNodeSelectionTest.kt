package org.wy.engine

import com.wy.mve.StateHolderWithNode
import org.wy.signal.batchSignalEnd
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 跨组件文本选择集成测试（真 Renderer 渲染树 + WrappedText/Editable 真节点）：
 * 验证 SelectionManager 纯派生与真实节点的注册、rangeOf 读取、selectedText 聚合、
 * 编辑器光标自动接管与注销收缩。
 *
 * 注意：跨节点端点经指针推导的精确偏移用例放在 common 层（SelectionManagerTest，
 * MockText 可编程换算坐标）；本文件的真实 WrappedTextNode 在无布局的测试环境下
 * positionForPoint 无法构建段落，故指针路径仅使用空白命中（空链，不触发定位）。
 */
class CrossNodeSelectionTest {

    /**
     * 每个用例结束后排干信号批队列：
     * 本测试大量写信号会触发自动批（异步协程消费），若放任跨测试残留，
     * 迟到的后台协程会与后续测试的手动 batchSignalEnd 并发竞争全局状态。
     * 先 sleep 让已提交的消费协程跑完，再 flush 兜底未提交的部分，
     * 保证进入下一用例时批状态干净、无并发方。
     */
    private class TestEditor(
        context: StateHolderWithNode<Node, List<Node>>
    ) : EditableTextNode(context) {
        override var text by createSignal("Editor")

        /** 模拟"从树上摘除"：置 true 后由 children 的 purifyList 过滤。 */
        var hidden: Boolean by createSignal(false)
        override val hide: Boolean get() = hidden
    }

    @org.junit.After
    fun drainSignalBatch() {
        Thread.sleep(50)
        batchSignalEnd()
    }

    private class Env {
        lateinit var renderer: Renderer
        lateinit var a: WrappedTextNode
        lateinit var b: WrappedTextNode
        lateinit var c: WrappedTextNode
        lateinit var editor: TestEditor

        val g: EngineGlobal get() = renderer.engineGlobal
        val manager: SelectionManager get() = renderer.engineGlobal.selectionManager

        fun build() {
            renderer = object : Renderer(null) {
                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    a = object : WrappedTextNode(this) {
                        override val text: String get() = "Hello world"
                    }
                    b = object : WrappedTextNode(this) {
                        override val text: String get() = "Kotlin engine"
                    }
                    c = object : WrappedTextNode(this) {
                        override val text: String get() = "Third block"
                    }
                    editor = TestEditor(this)
                }
            }
            renderer.children
        }

        /** 空白命中（无可选节点的空链），不触发 positionForPoint，可在无布局环境使用。 */
        fun blankHit(): HitestResult = HitestResult(emptyList(), 0L, 0f, 0f)
    }

    @Test
    fun pressingEmptyAreaClearsWholeSelection() {
        val env = Env()
        env.build()
        env.manager.selectAll()
        assertTrue(env.manager.hasSelection)

        // 按下落在无可选节点处：派生链返回空，整个全局选区立即清除（浏览器语义）
        env.g.pointerSelect = PointerSelect(env.blankHit(), null)

        assertFalse(env.manager.hasSelection)
        assertNull(env.manager.selectedText)
    }

    @Test
    fun keyboardSelectionDerivedFromEditorCursor() {
        val env = Env()
        env.build()
        env.renderer.engineGlobal.focused = env.editor

        // Shift+Right ×3：编辑器内键盘扩选写本地光标信号，Manager 无同步调用即感知
        repeat(3) {
            env.editor.handleKey(
                KeyEvent('\u0000', KeyCode.Right, ctrl = false, shift = true, alt = false, meta = false)
            )
        }
        assertEquals("Edi", env.manager.selectedText, "键盘选区应从编辑器光标直接派生")
    }

    @Test
    fun editorCursorTakesOverProgrammaticSelection() {
        val env = Env()
        env.build()
        env.manager.selectAll()
        assertTrue(env.manager.hasSelection)

        // 聚焦编辑器并输入字符：首次键盘交互吸收全局分配为本地光标，
        // 替换选中内容后光标塌缩对接管派生链，全局选区清除
        env.renderer.engineGlobal.focused = env.editor
        env.editor.handleKey(KeyEvent('X', KeyCode.Unknown, false, false, false, false))

        assertFalse(env.manager.hasSelection, "编辑器输入后全局选区应被光标塌缩接管清除")
        assertEquals("X", env.editor.text)
    }

    @Test
    fun selectRoutesToFocusedEditor() {
        val env = Env()
        env.build()
        env.renderer.engineGlobal.focused = env.editor

        // 聚焦编辑器时，select 分流为编辑器内本地选区（写本地光标信号）
        assertTrue(env.manager.select(env.editor, 1, 4))
        assertEquals("dit", env.manager.selectedText)

        // 目标不是聚焦编辑器：全局唯一选区会话无第二语境，拒绝
        assertFalse(env.manager.select(env.a, 0, 5))
        assertEquals("dit", env.manager.selectedText, "拒绝后原选区不变")
    }

    @Test
    fun editorProgrammaticCursorAndRange() {
        val env = Env()
        env.build()
        env.renderer.engineGlobal.focused = env.editor

        // 光标定位到任意偏移：塌缩对接管派生链
        env.editor.moveCursorTo(3)
        assertNull(env.manager.selectedText)
        env.editor.handleKey(KeyEvent('X', KeyCode.Unknown, false, false, false, false))
        assertEquals("EdiXtor", env.editor.text)

        // 编辑器内任意区间选区
        env.editor.selectRange(1, 4)
        assertEquals("diX", env.manager.selectedText)
    }

    @Test
    fun selectAllSpansAllRegisteredNodes() {
        val env = Env()
        env.build()
        env.manager.selectAll()

        assertTrue(env.manager.rangeOf(env.a) != null)
        assertTrue(env.manager.rangeOf(env.b) != null)
        assertTrue(env.manager.rangeOf(env.c) != null)
        assertTrue(env.manager.rangeOf(env.editor) != null)
        assertEquals("Hello worldKotlin engineThird blockEditor", env.manager.selectedText)
    }

    @Test
    fun hidingShrinksAssignmentWithoutCleanup() {
        val env = Env()
        env.build()
        env.manager.selectAll()

        // 隐藏参与节点：无需清理命令，其片段自然退出分配与聚合（树遍历不再发现它）
        env.editor.hidden = true

        assertNull(env.manager.rangeOf(env.editor))
        assertEquals("Hello worldKotlin engineThird block", env.manager.selectedText)
        assertTrue(env.manager.rangeOf(env.b) != null, "其余节点选区不受隐藏影响")
    }
}
