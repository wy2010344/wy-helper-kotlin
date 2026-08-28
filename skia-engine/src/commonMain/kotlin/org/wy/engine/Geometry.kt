package org.wy.engine

import kotlin.math.cos
import kotlin.math.sin

/** 二维点（窗口 / 节点坐标通用）。 */
data class PointF(val x: Float, val y: Float)

data class RectF(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom
    fun intersects(other: RectF): Boolean = left < other.right && right > other.left && top < other.bottom && bottom > other.top
    fun offset(dx: Float, dy: Float): RectF = RectF(left + dx, top + dy, right + dx, bottom + dy)
    fun inflate(delta: Float): RectF = RectF(left - delta, top - delta, right + delta, bottom + delta)
}

class Path {
    /**
     * 命中近似用的多边形顶点：线段取端点，曲线按固定细分采样。
     * [contains] 基于这些点做点是否在多边形内判定。
     */
    private val points = mutableListOf<Point>()

    /** 实际的路径命令序列，供平台端翻译成 Skia / Android Path。 */
    internal val commands = mutableListOf<PathCommand>()

    val isEmpty: Boolean get() = commands.isEmpty() || (commands.size == 1 && commands[0] is PathCommand.MoveTo)

    val lastX: Float get() = (points.lastOrNull()?.x) ?: 0f
    val lastY: Float get() = (points.lastOrNull()?.y) ?: 0f

    fun moveTo(x: Float, y: Float): Path {
        points.add(Point(x, y))
        commands.add(PathCommand.MoveTo(x, y))
        return this
    }

    fun lineTo(x: Float, y: Float): Path {
        points.add(Point(x, y))
        commands.add(PathCommand.LineTo(x, y))
        return this
    }

    /**
     * 二次贝塞尔曲线：控制点 (cx, cy)，终点 (x, y)。
     * 命中近似按 4 段均匀细分采样。
     */
    fun quadTo(cx: Float, cy: Float, x: Float, y: Float): Path {
        val px0 = lastX
        val py0 = lastY
        for (i in 1..SEGMENTS_QUAD) {
            val t = i / SEGMENTS_QUAD.toFloat()
            val px = (1 - t) * (1 - t) * px0 + 2 * (1 - t) * t * cx + t * t * x
            val py = (1 - t) * (1 - t) * py0 + 2 * (1 - t) * t * cy + t * t * y
            points.add(Point(px, py))
        }
        commands.add(PathCommand.QuadTo(cx, cy, x, y))
        return this
    }

    /**
     * 三次贝塞尔曲线：两个控制点，终点 (x, y)。
     * 命中近似按 6 段均匀细分采样。
     */
    fun cubicTo(cx1: Float, cy1: Float, cx2: Float, cy2: Float, x: Float, y: Float): Path {
        val px0 = lastX
        val py0 = lastY
        for (i in 1..SEGMENTS_CUBIC) {
            val t = i / SEGMENTS_CUBIC.toFloat()
            val mt = 1 - t
            val px = mt * mt * mt * px0 + 3 * mt * mt * t * cx1 + 3 * mt * t * t * cx2 + t * t * t * x
            val py = mt * mt * mt * py0 + 3 * mt * mt * t * cy1 + 3 * mt * t * t * cy2 + t * t * t * y
            points.add(Point(px, py))
        }
        commands.add(PathCommand.CubicTo(cx1, cy1, cx2, cy2, x, y))
        return this
    }

    fun close(): Path {
        commands.add(PathCommand.Close)
        return this
    }

    fun reset(): Path {
        points.clear()
        commands.clear()
        return this
    }

    fun contains(x: Float, y: Float): Boolean {
        var inside = false
        for (i in points.indices) {
            val j = (i + 1) % points.size
            val yi = points[i].y
            val yj = points[j].y
            val xi = points[i].x
            val xj = points[j].x
            if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) {
                inside = !inside
            }
        }
        return inside
    }

    private data class Point(val x: Float, val y: Float)

    sealed class PathCommand {
        abstract val x: Float
        abstract val y: Float

        data class MoveTo(override val x: Float, override val y: Float) : PathCommand()
        data class LineTo(override val x: Float, override val y: Float) : PathCommand()
        data class QuadTo(
            val cx: Float,
            val cy: Float,
            override val x: Float,
            override val y: Float,
        ) : PathCommand()

        data class CubicTo(
            val cx1: Float,
            val cy1: Float,
            val cx2: Float,
            val cy2: Float,
            override val x: Float,
            override val y: Float,
        ) : PathCommand()

        data object Close : PathCommand() {
            override val x: Float get() = 0f
            override val y: Float get() = 0f
        }
    }

    private companion object {
        const val SEGMENTS_QUAD = 4
        const val SEGMENTS_CUBIC = 6
    }
}

/**
 * 线性渐变定义：从 (startX, startY) 到 (endX, endY) 的颜色渐变。
 *
 * - [colors] 至少两个颜色，按 [stops] 分布（0..1）；[stops] 传 null 时均匀分布。
 * - 颜色为 [rgba] 结果（ARGB Int）。
 *
 * 用于 `fillRect` / `fillRoundRect` / `fillOval` / `fillPath` 等方法的 `gradient` 参数。
 * 设置 gradient 后忽略对应方法的 color 参数。
 */
class LinearGradient(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val colors: List<Int>,
    val stops: List<Float>? = null,
) {
    init {
        require(colors.size >= 2) { "LinearGradient 至少需要两个颜色，实际 ${colors.size}" }
        if (stops != null) {
            require(stops.size == colors.size) { "stops 数量必须与 colors 一致：${stops.size} vs ${colors.size}" }
            require(stops.all { it in 0f..1f }) { "stops 必须在 0..1 区间" }
        }
    }
}

class Matrix3f {
    private val m = FloatArray(6)

    init {
        m[0] = 1f; m[1] = 0f
        m[2] = 0f; m[3] = 1f
        m[4] = 0f; m[5] = 0f
    }

    fun translate(dx: Float, dy: Float): Matrix3f {
        val tx = m[0] * dx + m[2] * dy + m[4]
        val ty = m[1] * dx + m[3] * dy + m[5]
        m[4] = tx; m[5] = ty
        return this
    }

    fun rotate(degrees: Float): Matrix3f {
        val rad = Math.toRadians(degrees.toDouble())
        val cos = cos(rad).toFloat()
        val sin = sin(rad).toFloat()
        val a00 = m[0] * cos + m[1] * sin
        val a01 = m[1] * cos + m[3] * sin
        val a10 = m[0] * (-sin) + m[2] * cos
        val a11 = m[1] * (-sin) + m[3] * cos
        m[0] = a00; m[1] = a01
        m[2] = a10; m[3] = a11
        return this
    }

    fun scale(sx: Float, sy: Float): Matrix3f {
        m[0] *= sx; m[1] *= sx
        m[2] *= sy; m[3] *= sy
        return this
    }

    fun skew(sx: Float, sy: Float): Matrix3f {
        val a00 = m[0] + m[2] * sy
        val a01 = m[1] + m[3] * sy
        val a10 = m[0] * sx + m[2]
        val a11 = m[1] * sx + m[3]
        m[0] = a00; m[1] = a01
        m[2] = a10; m[3] = a11
        return this
    }

    fun mapX(x: Float, y: Float): Float = m[0] * x + m[2] * y + m[4]
    fun mapY(x: Float, y: Float): Float = m[1] * x + m[3] * y + m[5]

    fun inverted(): Matrix3f {
        val det = m[0] * m[3] - m[1] * m[2]
        if (kotlin.math.abs(det) < 1e-1f) return Matrix3f()
        val invDet = 1f / det
        val result = Matrix3f()
        result.m[0] = m[3] * invDet
        result.m[1] = -m[1] * invDet
        result.m[2] = -m[2] * invDet
        result.m[3] = m[0] * invDet
        result.m[4] = (m[2] * m[5] - m[3] * m[4]) * invDet
        result.m[5] = (m[1] * m[4] - m[0] * m[5]) * invDet
        return result
    }

    fun reset(): Matrix3f {
        m[0] = 1f; m[1] = 0f
        m[2] = 0f; m[3] = 1f
        m[4] = 0f; m[5] = 0f
        return this
    }
}
