package org.wy.engine

import com.wy.mve.Context
import com.wy.mve.StateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventIntegrationTest {

    /**
     * 创建一个最小化的测试环境，包含 Mock EngineGlobal 和 SelectionManager。
     */
    private fun createTestEnv(): Triple<TestStateHolder<Node, List<Node>>, TestEngineGlobal, SelectionManager> {
        val stateHolder = TestStateHolder<Node, List<Node>>()
        val engineGlobal = TestEngineGlobal()
        val selectionManager = SelectionManager()

        stateHolder.provide(engineGlobalContext, engineGlobal)
        stateHolder.provide(selectionManagerContext, selectionManager)

        return Triple(stateHolder, engineGlobal, selectionManager)
    }

    @Test
    fun testSelectionManagerIntegration() {
        val (_, _, selectionManager) = createTestEnv()

        class TestSelectable(initialText: String) : Selectable {
            var text: String = initialText
            private var _selected: Boolean = false
            override val hasSelection: Boolean get() = _selected && text.isNotEmpty()
            override fun selectionText(): String? = if (_selected) text else null
            override fun selectionRect(): RectF? = if (_selected) RectF(0f, 0f, 50f, 16f) else null
            override fun setSelected(selected: Boolean) { _selected = selected }
            override fun selectAll() { _selected = text.isNotEmpty() }
        }

        val item1 = TestSelectable("Hello")
        val item2 = TestSelectable("World")

        selectionManager.select(item1)
        assertEquals(item1, selectionManager.current)
        assertFalse(item1.hasSelection)

        selectionManager.selectAll()
        assertTrue(item1.hasSelection)

        selectionManager.select(item2)
        assertEquals(item2, selectionManager.current)
        assertFalse(item2.hasSelection)
        assertFalse(item1.hasSelection)

        selectionManager.selectAll()
        assertTrue(item2.hasSelection)

        selectionManager.clear()
        assertNull(selectionManager.current)
    }

    @Test
    fun testEngineGlobalFocus() {
        val (_, engineGlobal, _) = createTestEnv()

        class TestNode(context: StateHolder<*, *>?, engineGlobal: EngineGlobal? = null) : Node(context, engineGlobal) {
            override val focusable: Boolean = true
        }

        val node1 = TestNode(null, engineGlobal)
        val node2 = TestNode(null, engineGlobal)

        engineGlobal.focused = node1
        assertEquals(node1, engineGlobal.focused)

        engineGlobal.focused = node2
        assertEquals(node2, engineGlobal.focused)
    }

    @Test
    fun testPointerCapture() {
        val (_, engineGlobal, _) = createTestEnv()

        var movedX = -1f
        var movedY = -1f
        var upX = -1f
        var upY = -1f

        engineGlobal.capturePointer(
            id = 0,
            onMove = { e ->
                movedX = e.rootX
                movedY = e.rootY
            },
            onUp = { e ->
                upX = e.rootX
                upY = e.rootY
            }
        )

        engineGlobal.simulatePointerMove(100f, 200f)
        assertEquals(100f, movedX)
        assertEquals(200f, movedY)

        engineGlobal.simulatePointerUp(300f, 400f)
        assertEquals(300f, upX)
        assertEquals(400f, upY)

        // up 后捕获结束，再 move 不应触发
        engineGlobal.simulatePointerMove(500f, 600f)
        assertEquals(300f, upX)
    }

    @Test
    fun testKeyCallbackRegistration() {
        val (_, engineGlobal, _) = createTestEnv()

        var receivedKey = ' '
        var receivedCtrl = false

        val handle = engineGlobal.registerKeyPress { e ->
            receivedKey = e.key
            receivedCtrl = e.ctrl
        }

        engineGlobal.simulateKeyPress('a')
        assertEquals('a', receivedKey)
        assertEquals(false, receivedCtrl)

        engineGlobal.simulateKeyPress('c', ctrl = true)
        assertEquals('c', receivedKey)
        assertEquals(true, receivedCtrl)

        handle()
        engineGlobal.simulateKeyPress('x')
        assertEquals('c', receivedKey)
    }

    @Test
    fun testNodeCreationWithNullContext() {
        val (_, engineGlobal, _) = createTestEnv()

        class TestNode(context: StateHolder<*, *>?, engineGlobal: EngineGlobal? = null) : Node(context, engineGlobal) {
            override val focusable: Boolean = false
            override fun acceptHit(x: Float, y: Float): Boolean = true
        }

        val node = TestNode(null, engineGlobal)
        assertNull(node.parent)
    }

    @Test
    fun testNodeHidIndexThrows() {
        val (_, engineGlobal, _) = createTestEnv()

        class TestNode(context: StateHolder<*, *>?, engineGlobal: EngineGlobal? = null) : Node(context, engineGlobal) {
            override val hide: Boolean = true
        }

        val node = TestNode(null, engineGlobal)
        // 隐藏节点不应访问 index：访问即 bug，应抛错暴露
        assertFailsWith<Error>("已经隐藏不再显示") { node.index }
    }

    @Test
    fun testModifiersOnlyMaintainedByKeyboard() {
        val renderer = Renderer(null)
        try {
            assertFalse(renderer.engineGlobal.ctrl)
            assertFalse(renderer.engineGlobal.shift)

            // 鼠标事件不携带也不写入修饰键
            renderer.mouseDown(0f, 0f)
            renderer.mouseMove(5f, 5f)
            renderer.mouseWheel(5f, 5f, 10f)
            assertFalse(renderer.engineGlobal.shift, "鼠标事件不应写入修饰键")

            // keyPress 只做按键分发，不应污染修饰键
            renderer.keyPress('a', KeyCode.Unknown, false, true, false)
            assertFalse(renderer.engineGlobal.shift, "keyPress 不维护修饰键")

            // 键盘按下上报权威快照
            renderer.updateModifiers(false, true, false, false)
            assertTrue(renderer.engineGlobal.shift)
            assertFalse(renderer.engineGlobal.ctrl)

            // 键盘释放上报
            renderer.updateModifiers(false, false, false, false)
            assertFalse(renderer.engineGlobal.shift)

            // 窗口失焦清空
            renderer.updateModifiers(false, true, false, false)
            renderer.clearModifiers()
            assertFalse(renderer.engineGlobal.shift)
        } finally {
            renderer.destroy()
        }
    }

    @Test
    fun testMultiNodeHierarchy() {
        val stateHolder = TestStateHolder<Node, List<Node>>()
        val engineGlobal = TestEngineGlobal()
        stateHolder.provide(engineGlobalContext, engineGlobal)

        class ChildNode(context: StateHolder<*, *>?, engineGlobal: EngineGlobal? = null) : Node(context, engineGlobal) {
            override val hide: Boolean = false
            override fun acceptHit(x: Float, y: Float): Boolean = true
        }

        val child1 = ChildNode(stateHolder, engineGlobal)
        val child2 = ChildNode(stateHolder, engineGlobal)

        assertEquals(2, stateHolder.getNodes().size)
    }
}