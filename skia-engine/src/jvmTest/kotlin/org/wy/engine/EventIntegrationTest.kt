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
        val (_, _, _) = createTestEnv()

        class TestSelectable(initialText: String) : Selectable {
            var text: String = initialText
            override fun selectionRect(): RectF? = null
            override val textLength: Int get() = text.length
            override fun positionForPoint(globalX: Float, globalY: Float): Int = 0
            override fun textInRange(start: Int, end: Int): String =
                if (end > start) text.substring(start, end.coerceAtMost(text.length)) else ""
        }

        // headless 无渲染树：经补充清单提供可选集合（等价于挂树）
        lateinit var item1: TestSelectable
        val selectionManager = SelectionManager { listOf(item1) }
        item1 = TestSelectable("Hello")

        // 初始无选区
        assertFalse(selectionManager.hasSelection)

        // 全选覆盖集合内所有节点，可聚合读取
        selectionManager.selectAll()
        assertEquals("Hello", selectionManager.selectedText)
        assertTrue(selectionManager.hasSelection)

        // 清除程序化会话
        selectionManager.clear()
        assertFalse(selectionManager.hasSelection)
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