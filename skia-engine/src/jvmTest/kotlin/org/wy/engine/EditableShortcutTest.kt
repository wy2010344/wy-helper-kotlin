package org.wy.engine

import com.wy.mve.StateHolderWithNode
import org.wy.signal.batchSignalEnd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 编辑器快捷键与撤销 / 重做的输入回归测试：
 * - CapsLock / Shift 导致平台上报大写字符时，Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z 必须照常生效；
 * - 输入法组合进行中（composingLength > 0）redo 与 undo 同样必须被抑制，
 *   否则会把组合文本整体改写、破坏 IME 会话。
 */
class EditableShortcutTest {

    @org.junit.After
    fun drainSignalBatch() {
        Thread.sleep(50)
        batchSignalEnd()
    }

    /** 暴露受保护三段式组合入口的测试编辑器（生产由 IME 桥接驱动）。 */
    private class TestEditor(context: StateHolderWithNode<Node, List<Node>>) : EditableTextNode(context) {
        fun compose(committed: String, composing: String, cursorInComposing: Int) {
            super.onComposing(committed, composing, cursorInComposing)
        }
    }

    /** 单编辑器环境：真渲染树挂一个编辑器。 */
    private class Env {
        lateinit var renderer: Renderer
        lateinit var editor: TestEditor

        val g: EngineGlobal get() = renderer.engineGlobal

        init {
            renderer = object : Renderer(null) {
                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    editor = TestEditor(this)
                    editor.text = ""
                }
            }
            renderer.children
            g.focused = editor
        }
    }

    // ===== 组合进行中 redo 必须被抑制（undo 已有同款守卫，redo 此前缺失） =====
    //
    // 流程：插入 "Hello" → 撤销（redo 栈备好 "Hello"）→ 进入组合态（预编辑 "pin"）
    //       → 触发 redo。
    // 期望：组合中文本保持 "pin"；若守卫缺失，applyState 会把文本改写为 "Hello"，
    //       光标与组合区间全部错乱。
    @Test
    fun redoSuppressedDuringComposing() {
        val env = Env()
        env.editor.insertText("Hello")
        env.editor.undo()
        assertEquals("", env.editor.text, "撤销后应为空文本")

        env.editor.compose("", "pin", 3)
        assertEquals("pin", env.editor.text, "组合文本应已上屏")

        env.editor.redo()
        assertEquals("pin", env.editor.text, "组合进行中 redo 不得改写文本")
    }

    // ===== CapsLock / Shift 大写不破坏撤销重做快捷键 =====
    //
    // CapsLock 开启或 Shift 参与时平台上报的 key 是大写（如 'Z'），
    // 快捷键匹配必须做大小写归一：
    // - Ctrl+'Z'（CapsLock）→ 撤销；
    // - Ctrl+Shift+'Z'（Shift 天然产生大写）→ 重做；
    // - Ctrl+'Y'（CapsLock）→ 重做。
    @Test
    fun capsLockDoesNotBreakUndoRedoShortcuts() {
        val env = Env()
        env.editor.insertText("A")
        env.editor.insertText("B")
        assertEquals("AB", env.editor.text)

        // Ctrl + 'Z'（CapsLock 大写）应撤销
        assertTrue(
            env.editor.handleKey(KeyEvent('Z', KeyCode.Unknown, ctrl = true, shift = false, alt = false, meta = false)),
            "Ctrl+Z（大写）应由编辑器消费"
        )
        assertEquals("A", env.editor.text, "大写 Z 的 Ctrl+Z 应触发撤销")

        // Ctrl + Shift + 'Z'（Shift 本身就产生大写）应重做
        assertTrue(
            env.editor.handleKey(KeyEvent('Z', KeyCode.Unknown, ctrl = true, shift = true, alt = false, meta = false)),
            "Ctrl+Shift+Z 应由编辑器消费"
        )
        assertEquals("AB", env.editor.text, "Ctrl+Shift+Z 应触发重做")

        // 再撤一步后用 Ctrl + 'Y'（CapsLock 大写）重做
        assertTrue(
            env.editor.handleKey(KeyEvent('Z', KeyCode.Unknown, ctrl = true, shift = false, alt = false, meta = false)),
            "Ctrl+Z（大写）应由编辑器消费"
        )
        assertEquals("A", env.editor.text)
        assertTrue(
            env.editor.handleKey(KeyEvent('Y', KeyCode.Unknown, ctrl = true, shift = false, alt = false, meta = false)),
            "Ctrl+Y（大写）应由编辑器消费"
        )
        assertEquals("AB", env.editor.text, "Ctrl+Y（大写）应触发重做")
    }
}
