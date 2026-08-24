package org.wy.engine

import com.wy.mve.StateHolderWithNode
import org.wy.signal.batchSignalEnd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 输入法组合态（预编辑上屏）行为回归测试：
 * - 预编辑串以"窗口替换"方式写入逻辑文本，逐帧演进不残留旧帧内容；
 * - 提交（空串上报）仅移除窗口，已确认文本由平台经常规插入路径另行写入；
 * - 取消（restore）还原被预编辑替换的原选区文本；
 * - 组合进行中 undo / redo 被抑制。
 */
class EditableComposingTest {

    /** 单编辑器环境。 */
    private class Env {
        lateinit var renderer: Renderer
        lateinit var editor: EditableTextNode

        val g: EngineGlobal get() = renderer.engineGlobal

        init {
            renderer = object : Renderer(null) {
                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    editor = object : EditableTextNode(this) {}
                    editor.text = "abXY"
                    editor.moveCursorTo(2) // 光标落在 X 前，组合将替换 [2,4) 之外的零宽窗口
                }
            }
            renderer.children
            g.focused = editor
        }
    }

    @org.junit.After
    fun drainSignalBatch() {
        Thread.sleep(50)
        batchSignalEnd()
    }

    // ===== 多帧演进：后一帧整体替换前一帧的预编辑窗口 =====
    //
    // 模拟拼音输入："pin" → "ping"，窗口内内容应被整体换掉而非追加。
    // 提交时（空串上报）移除窗口；最终文本由平台经 keyPress 另行插入。
    @Test
    fun composingFramesReplaceWindowAndCommitRemovesIt() {
        val env = Env()
        val ed = env.editor

        ed.onComposing("pin", 3)
        assertEquals("abpinXY", ed.text, "首帧应在光标处插入预编辑串")
        assertEquals(5, ed.cursor(), "光标应跟随到预编辑尾")

        ed.onComposing("ping", 4)
        assertEquals("abpingXY", ed.text, "第二帧应整体替换第一帧窗口")

        // 平台提交序列：先清窗口（此时 committed 文本尚未插入），再逐字插入最终文本
        ed.onComposing("", 0)
        assertEquals("abXY", ed.text, "提交时应移除预编辑窗口、还原原文本")
        assertEquals(2, ed.cursor())
        ed.insertText("品")
        assertEquals("ab品XY", ed.text, "已确认文本经常规路径写入")
    }

    // ===== 组合起点带本地选区：预编辑替换选区；取消则还原原文 =====
    @Test
    fun composingOverSelectionReplacesAndCancelRestores() {
        val env = Env()
        val ed = env.editor
        ed.selectRange(2, 4) // 选中 "XY"

        ed.onComposing("x", 1)
        assertEquals("abx", ed.text, "预编辑应替换被选中的 XY")

        ed.endComposition(restore = true)
        assertEquals("abXY", ed.text, "取消组合应还原原选区文本")
        assertEquals(2, ed.cursor(), "光标回到基座插入点")
    }

    // ===== 组合中 undo / redo 必须被抑制 =====
    @Test
    fun undoRedoSuppressedDuringComposing() {
        val env = Env()
        val ed = env.editor
        ed.insertText("Z") // 制造可撤销历史 → "abXYZ"？注意光标在 2：实际为 "ZabXY"
        val afterInsert = ed.text
        ed.undo()
        assertEquals("abXY", ed.text, "撤销回初始文本")
        ed.redo()
        assertEquals(afterInsert, ed.text)

        // 进入组合态后再试 undo / redo
        ed.undo() // 回 "abXY"
        ed.onComposing("pin", 3)
        ed.undo()
        assertTrue(ed.text.contains("pin"), "组合中 undo 不得改写文本")
        ed.redo()
        assertTrue(ed.text.contains("pin"), "组合中 redo 不得改写文本")
    }
}
