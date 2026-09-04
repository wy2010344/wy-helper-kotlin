package org.wy.engine

import com.wy.mve.DuplicateInfo
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.helper.DropdownBase
import org.wy.engine.helper.drag
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 离树节点的引用一致性（与选区"集合即树"同哲学）：
 * - 焦点指向已销毁编辑器时，活性由消费端派生：不再是活跃编辑器、不再收键；
 * - Dropdown 锚离树（隐藏/销毁）后浮层自动隐藏，不对锚做过期几何换算；
 * - 拖拽捕获随宿主销毁自动释放，死节点不再收到拖拽回调。
 */
class StaleNodeReferenceTest : SkiaTestBase() {

    // ---------- A. activeEditor 活性派生 ----------

    @Test
    fun destroyedEditorIsNoLongerActiveAndReceivesNoKeys() {
        class Item(val key: Long)

        lateinit var editor1: EditableTextNode
        var items by createSignal(listOf(Item(1), Item(2)))

        val renderer = object : Renderer(null) {
            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                renderForEach(
                    { callback -> items.forEach { callback(it.key, it) } },
                    DuplicateInfo.WARN
                ) { key, _ ->
                    object : EditableTextNode(this) {
                        override var text by createSignal("e$key")
                    }.also { if (key == 1L) editor1 = it }
                }
            }
        }
        renderer.children

        val g = renderer.engineGlobal
        g.focused = editor1
        assertEquals(editor1, g.activeEditor)

        // 删除聚焦编辑器所在行 → 结构重算销毁旧 holder
        items = listOf(Item(2))
        renderer.children
        flushBatches()

        assertNull(g.activeEditor, "焦点指向已销毁编辑器时应派生为无活跃编辑器")

        // 键盘不得再写进死编辑器（此前 focused 过期引用直达 handleKey）
        renderer.keyPress('x', KeyCode.Unknown, false, false, false)
        assertEquals("e1", editor1.text, "已销毁的编辑器不应再处理键盘输入")
    }

    // ---------- B. Dropdown 锚活性 ----------

    private class TestAnchor(context: StateHolder<Node, List<Node>>) : RectNode(context) {
        var hidden by createSignal(false)
        override val hide: Boolean get() = hidden
    }

    @Test
    fun dropdownHidesWhenAnchorLeavesTree() {
        val holder = TestStateHolder<Node, List<Node>>()
        val g = TestEngineGlobal()
        holder.provide(engineGlobalContext, g)

        val anchor = TestAnchor(holder)
        val dropdown = object : DropdownBase(holder, anchor) {}

        assertFalse(dropdown.hide)

        // 锚隐藏：浮层随之隐藏（purifyList 过滤后不再对锚做几何换算）
        anchor.hidden = true
        flushBatches()
        assertTrue(dropdown.hide, "锚离树（hide）时下拉应自动隐藏")

        // 销毁同理
        anchor.hidden = false
        flushBatches()
        assertFalse(dropdown.hide)
        holder.destroy()
        assertTrue(anchor.destroyed)
        assertTrue(dropdown.hide, "锚离树（destroyed）时下拉应自动隐藏")
    }

    // ---------- C. drag 捕获随宿主销毁释放 ----------

    @Test
    fun dragCaptureReleasesWhenHostDestroyed() {
        val holder = TestStateHolder<Node, List<Node>>()
        val g = TestEngineGlobal()
        holder.provide(engineGlobalContext, g)

        var moved = 0
        val node = object : Node(holder) {
            override fun onPointerDown(e: PointerEvent) {
                context?.drag(e) { moved++ }
            }
        }

        node.onPointerDown(PointerEvent(id = 7, type = PointerType.Down, x = 0f, y = 0f))
        g.simulatePointerMove(1f, 1f)
        assertEquals(1, moved, "拖拽中 move 正常投递")

        // 宿主销毁 → 捕获自动释放，死节点不再收到回调
        holder.destroy()
        g.simulatePointerMove(2f, 2f)
        assertEquals(1, moved, "宿主销毁后捕获应自动释放，不再投递")
    }
}
