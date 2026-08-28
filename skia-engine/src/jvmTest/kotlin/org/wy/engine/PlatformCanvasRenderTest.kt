package org.wy.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 平台画布新增绘制能力的真实渲染冒烟测试（JVM + Skia raster surface）：
 * 用离屏 raster surface 渲染后读回像素验证 fillPath / strokePath / 线性渐变 / 阴影。
 */
class PlatformCanvasRenderTest {

    private fun render(size: Int = 120, draw: (PlatformCanvas) -> Unit): SurfaceHelper {
        val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(size, size)
        val canvas = surface.canvas
        canvas.clear(rgba(255, 255, 255))
        draw(PlatformCanvas(canvas))
        val image = surface.makeImageSnapshot()
        val helper = SurfaceHelper(image.peekPixels())
        image.close()
        surface.close()
        return helper
    }

    @Test
    fun fillPathDrawsTriangle() {
        val pix = render {
            val path = Path()
                .moveTo(10f, 10f)
                .lineTo(110f, 10f)
                .lineTo(60f, 110f)
                .close()
            it.fillPath(path, rgba(200, 30, 30))
        }
        // 三角形中心是红色，角落是白底
        assertEquals(0xFFC81E1E.toInt(), pix.color(60, 60))
        assertEquals(0xFFFFFFFF.toInt(), pix.color(5, 5), "三角形外应保持画布底色")
    }

    @Test
    fun strokePathDrawsAlongCurve() {
        val pix = render {
            val path = Path()
                .moveTo(10f, 60f)
                .quadTo(60f, 10f, 110f, 60f)
            it.strokePath(path, rgba(0, 0, 200), strokeWidth = 4f)
        }
        // 曲线弓顶附近应有描边像素（蓝）
        val top = pix.color(60, 35)
        val r = top ushr 16 and 0xFF
        val b = top and 0xFF
        assertTrue(b > r, "描边应偏蓝，实际 r=$r b=$b")
    }

    @Test
    fun fillRectWithLinearGradient() {
        val pix = render {
            it.fillRect(
                20f, 20f, 80f, 80f,
                gradient = LinearGradient(20f, 0f, 100f, 0f, listOf(rgba(255, 0, 0), rgba(0, 0, 255))),
            )
        }
        // 渐变起点端偏红、终点端偏蓝
        val left = pix.color(25, 60)
        val right = pix.color(95, 60)
        assertTrue(left ushr 16 and 0xFF > left and 0xFF, "起点端应偏红")
        assertTrue(right and 0xFF > right ushr 16 and 0xFF, "终点端应偏蓝")
    }

    @Test
    fun fillRoundRectWithGradient() {
        val pix = render {
            it.fillRoundRect(
                20f, 20f, 80f, 80f, 16f,
                gradient = LinearGradient(0f, 0f, 0f, 100f, listOf(rgba(0, 255, 0), rgba(0, 0, 255))),
            )
        }
        val top = pix.color(60, 25)
        val bottom = pix.color(60, 95)
        assertTrue(top ushr 8 and 0xFF > top and 0xFF, "垂直渐变上端应偏绿")
        assertTrue(bottom and 0xFF > bottom ushr 8 and 0xFF, "垂直渐变下端应偏蓝")
    }

    @Test
    fun drawShadowCreatesAlphaOutsideShape() {
        val pix = render {
            it.drawShadow(45f, 45f, 30f, 30f, 8f, 8f, rgba(0, 0, 0, 180))
        }
        // 模糊扩散到形状边界之外（70,45 之外区域仍应有非白像素/或明显暗于底色）
        val outside = pix.color(40, 45)
        assertTrue(outside != 0xFFFFFFFF.toInt(), "阴影应扩散到形状外，实际 $outside")
    }

    private class SurfaceHelper(private val pixmap: org.jetbrains.skia.Pixmap?) {
        fun color(x: Int, y: Int): Int =
            pixmap?.getColor(x, y) ?: 0xFF000000.toInt()
    }
}