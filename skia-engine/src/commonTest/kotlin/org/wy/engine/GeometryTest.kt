package org.wy.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

class PathTest {
    @Test
    fun testPolylineContains() {
        // 矩形路径（逆时针均可）
        val path = Path()
            .moveTo(0f, 0f)
            .lineTo(100f, 0f)
            .lineTo(100f, 100f)
            .lineTo(0f, 100f)
            .close()
        assertTrue(path.contains(50f, 50f))
        assertFalse(path.contains(150f, 50f))
        assertFalse(path.contains(-10f, -10f))
    }

    @Test
    fun testQuadBulgesAboveBaseline() {
        // 二次曲线：控制点拉高，命中近似点应越过起点到终点的连线
        val path = Path()
            .moveTo(0f, 100f)
            .quadTo(50f, 0f, 100f, 100f)
            .lineTo(0f, 100f)
            .close()
        // 拱形内部
        assertTrue(path.contains(50f, 60f))
        // 起点-终点连线上方但在弓外下沿
        assertFalse(path.contains(100f, 200f))
    }

    @Test
    fun testCubicContainsCenter() {
        val path = Path()
            .moveTo(0f, 0f)
            .cubicTo(0f, 100f, 100f, 100f, 100f, 0f)
            .lineTo(100f, 100f)
            .lineTo(0f, 100f)
            .close()
        // 弓形高 y≈75（x=50），内部为 [弓顶, 底线 100]
        assertTrue(path.contains(50f, 90f))
        assertFalse(path.contains(50f, 40f))
    }

    @Test
    fun testCommandsRecordedInOrder() {
        val path = Path()
            .moveTo(1f, 2f)
            .lineTo(3f, 4f)
            .quadTo(5f, 6f, 7f, 8f)
            .cubicTo(9f, 10f, 11f, 12f, 13f, 14f)
            .close()
        val cmds = path.commands
        assertEquals(5, cmds.size)
        assertTrue(cmds[0] is Path.PathCommand.MoveTo)
        assertTrue(cmds[1] is Path.PathCommand.LineTo)
        val q = cmds[2] as Path.PathCommand.QuadTo
        assertEquals(5f, q.cx)
        assertEquals(7f, q.x)
        val c = cmds[3] as Path.PathCommand.CubicTo
        assertEquals(9f, c.cx1)
        assertEquals(11f, c.cx2)
        assertEquals(13f, c.x)
        assertTrue(cmds[4] is Path.PathCommand.Close)
    }

    @Test
    fun testReset() {
        val path = Path().moveTo(0f, 0f).lineTo(10f, 10f)
        assertTrue(path.commands.isNotEmpty())
        path.reset()
        assertTrue(path.commands.isEmpty())
    }

    @Test
    fun testEmptyFromSingleMoveTo() {
        // 只有 moveTo 视为空路径（无实际绘制指令）
        val path = Path().moveTo(0f, 0f)
        val cmds = path.commands
        assertTrue(cmds.size == 1 && cmds[0] is Path.PathCommand.MoveTo)
    }

    @Test
    fun testLinearGradientRequiresColors() {
        assertFails { LinearGradient(0f, 0f, 100f, 0f, listOf(0xFF0000)) }
        // stops 数量与 colors 不一致
        assertFails {
            LinearGradient(0f, 0f, 100f, 0f, listOf(0xFF0000, 0x00FF00), listOf(0.5f))
        }
        // stops 越界
        assertFails {
            LinearGradient(0f, 0f, 100f, 0f, listOf(0xFF0000, 0x00FF00), listOf(0f, 1.5f))
        }
    }

    @Test
    fun testLinearGradientValid() {
        val g = LinearGradient(0f, 0f, 100f, 0f, listOf(0xFF0000, 0x00FF00, 0x0000FF))
        assertEquals(3, g.colors.size)
        assertNull(g.stops)
        val withStops = LinearGradient(0f, 0f, 100f, 0f, listOf(0xFF0000, 0x00FF00), listOf(0f, 1f))
        assertEquals(listOf(0f, 1f), withStops.stops)
    }

    @Test
    fun testRadialGradientRequiresColorsRadius() {
        assertFails { RadialGradient(50f, 50f, 40f, listOf(0xFF0000)) }
        assertFails { RadialGradient(50f, 50f, 0f, listOf(0xFF0000, 0x00FF00)) }
        assertFails { RadialGradient(50f, 50f, 40f, listOf(0xFF0000, 0x00FF00), listOf(0f, 1.5f)) }
    }

    @Test
    fun testRadialGradientValid() {
        val g = RadialGradient(50f, 50f, 40f, listOf(0xFF0000, 0x00FF00))
        assertEquals(0xFF0000, g.colors[0])
        assertEquals(40f, g.radius)
        assertNotNull(g)
    }

    @Test
    fun testSweepGradientRequiresColors() {
        assertFails { SweepGradient(50f, 50f, listOf(0xFF0000)) }
        // stops 数量与 colors 不一致
        assertFails {
            SweepGradient(50f, 50f, listOf(0xFF0000, 0x00FF00), listOf(0.5f))
        }
        // stops 越界
        assertFails {
            SweepGradient(50f, 50f, listOf(0xFF0000, 0x00FF00), listOf(0f, 1.5f))
        }
    }

    @Test
    fun testSweepGradientValid() {
        val g = SweepGradient(50f, 50f, listOf(0xFF0000, 0x00FF00, 0x0000FF))
        assertEquals(3, g.colors.size)
        assertEquals(50f, g.centerX)
        assertEquals(50f, g.centerY)
        assertNull(g.stops)
    }

    @Test
    fun testGradientHierarchy() {
        // fill 系列 gradient 参数接收的是公共接口 Gradient
        val gradients: List<Gradient> = listOf(
            LinearGradient(0f, 0f, 100f, 0f, listOf(0xFF0000, 0x00FF00)),
            RadialGradient(50f, 50f, 40f, listOf(0xFF0000, 0x00FF00)),
            SweepGradient(50f, 50f, listOf(0xFF0000, 0x00FF00)),
        )
        assertEquals(3, gradients.size)
    }

    private fun assertFails(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        error("expected IllegalArgumentException")
    }
}