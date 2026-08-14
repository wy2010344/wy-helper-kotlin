package org.wy.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RectFTest {
    @Test
    fun testRectProperties() {
        val rect = RectF(10f, 20f, 110f, 220f)
        assertEquals(100f, rect.width)
        assertEquals(200f, rect.height)
        assertEquals(60f, rect.centerX)
        assertEquals(120f, rect.centerY)
    }

    @Test
    fun testContains() {
        val rect = RectF(0f, 0f, 100f, 100f)
        assertTrue(rect.contains(50f, 50f))
        assertTrue(rect.contains(0f, 0f))
        assertTrue(rect.contains(100f, 100f))
        assertFalse(rect.contains(-1f, 50f))
        assertFalse(rect.contains(101f, 50f))
    }

    @Test
    fun testIntersects() {
        val r1 = RectF(0f, 0f, 100f, 100f)
        val r2 = RectF(50f, 50f, 150f, 150f)
        val r3 = RectF(200f, 200f, 300f, 300f)
        assertTrue(r1.intersects(r2))
        assertFalse(r1.intersects(r3))
    }

    @Test
    fun testOffset() {
        val rect = RectF(10f, 20f, 30f, 40f)
        val offset = rect.offset(5f, 10f)
        assertEquals(15f, offset.left)
        assertEquals(30f, offset.top)
        assertEquals(35f, offset.right)
        assertEquals(50f, offset.bottom)
    }

    @Test
    fun testInflate() {
        val rect = RectF(10f, 20f, 30f, 40f)
        val inflated = rect.inflate(5f)
        assertEquals(5f, inflated.left)
        assertEquals(15f, inflated.top)
        assertEquals(35f, inflated.right)
        assertEquals(45f, inflated.bottom)
    }
}