package org.wy.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectionManagerTest {

    private class MockSelectable(
        private var text: String = "",
        private var selected: Boolean = false
    ) : Selectable {
        override val hasSelection: Boolean get() = selected && text.isNotEmpty()

        override fun selectionText(): String? = if (selected) text else null

        override fun selectionRect(): RectF? = if (selected) RectF(0f, 0f, 100f, 20f) else null

        override fun setSelected(selected: Boolean) {
            this.selected = selected
        }

        override fun selectAll() {
            selected = text.isNotEmpty()
        }
    }

    @Test
    fun testSelectAndClear() {
        val manager = SelectionManager()
        val item1 = MockSelectable("Hello")
        val item2 = MockSelectable("World")

        assertNull(manager.current)
        assertFalse(manager.hasSelection)

        manager.select(item1)
        assertEquals(item1, manager.current)
        assertFalse(manager.hasSelection)

        manager.select(item2)
        assertEquals(item2, manager.current)
        assertFalse(manager.hasSelection)

        manager.selectAll()
        assertTrue(item2.hasSelection)
        assertFalse(item1.hasSelection)

        manager.clear()
        assertNull(manager.current)
        assertFalse(manager.hasSelection)
    }

    @Test
    fun testSelectSameNoOp() {
        val manager = SelectionManager()
        val item = MockSelectable("Test")

        manager.select(item)
        manager.select(item)
        assertEquals(item, manager.current)
    }

    @Test
    fun testSelectAll() {
        val manager = SelectionManager()
        val item = MockSelectable("Hello World")

        manager.select(item)
        assertFalse(item.hasSelection)

        manager.selectAll()
        assertTrue(item.hasSelection)
        assertEquals("Hello World", manager.selectedText)
    }

    @Test
    fun testSelectedRect() {
        val manager = SelectionManager()
        val item = MockSelectable("Hello")

        assertNull(manager.selectedRect)

        manager.select(item)
        manager.selectAll()
        val rect = manager.selectedRect
        assertTrue(rect != null)
        assertEquals(0f, rect.left)
        assertEquals(100f, rect.right)
    }
}