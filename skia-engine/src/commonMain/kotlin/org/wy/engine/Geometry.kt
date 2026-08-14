package org.wy.engine

import kotlin.math.cos
import kotlin.math.sin

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
    private val points = mutableListOf<Point>()
    private val commands = mutableListOf<PathCommand>()

    val isEmpty: Boolean get() = points.isEmpty()

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
        data class MoveTo(val x: Float, val y: Float) : PathCommand()
        data class LineTo(val x: Float, val y: Float) : PathCommand()
        data object Close : PathCommand()
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
