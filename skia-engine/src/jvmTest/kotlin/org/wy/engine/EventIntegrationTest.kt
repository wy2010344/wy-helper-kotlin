package org.wy.engine

import com.wy.mve.Context
import com.wy.mve.StateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
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
        stateHolder.provide(gestureArenaContext, GestureArena())

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

        class TestNode(context: StateHolder<*, *>?) : Node(context) {
            override val focusable: Boolean = true
        }

        val node1 = TestNode(null)
        val node2 = TestNode(null)

        engineGlobal.focused = node1
        assertEquals(node1, engineGlobal.focused)

        engineGlobal.focused = node2
        assertEquals(node2, engineGlobal.focused)
    }

    @Test
    fun testMouseCallbackRegistration() {
        val (_, engineGlobal, _) = createTestEnv()

        var downX = -1f
        var downY = -1f
        var upX = -1f
        var upY = -1f

        val downHandle = engineGlobal.registerMouseDown { e ->
            downX = e.x
            downY = e.y
        }
        val upHandle = engineGlobal.registerMouseUp { e ->
            upX = e.x
            upY = e.y
        }

        engineGlobal.simulateMouseDown(100f, 200f)
        assertEquals(100f, downX)
        assertEquals(200f, downY)

        engineGlobal.simulateMouseUp(300f, 400f)
        assertEquals(300f, upX)
        assertEquals(400f, upY)

        downHandle()
        engineGlobal.simulateMouseDown(500f, 600f)
        assertEquals(100f, downX)
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
        class TestNode(context: StateHolder<*, *>?) : Node(context) {
            override val focusable: Boolean = false
            override fun acceptHit(x: Float, y: Float): Boolean = true
        }

        val node = TestNode(null)
        assertNull(node.parent)
    }

    @Test
    fun testNodeHidDoesNotThrow() {
        class TestNode(context: StateHolder<*, *>?) : Node(context) {
            override val hide: Boolean = true
        }

        val node = TestNode(null)
        assertEquals(-1, node.index)
    }

    @Test
    fun testMultiNodeHierarchy() {
        val stateHolder = TestStateHolder<Node, List<Node>>()
        val engineGlobal = TestEngineGlobal()
        stateHolder.provide(engineGlobalContext, engineGlobal)

        class ChildNode(context: StateHolder<*, *>?) : Node(context) {
            override val hide: Boolean = false
            override fun acceptHit(x: Float, y: Float): Boolean = true
        }

        val child1 = ChildNode(stateHolder)
        val child2 = ChildNode(stateHolder)

        assertEquals(2, stateHolder.getNodes().size)
    }
}