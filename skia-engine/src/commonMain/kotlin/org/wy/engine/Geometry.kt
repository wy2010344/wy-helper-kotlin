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
    /** 实际的路径命令序列，供平台端翻译成 Skia / Android Path。 */
    internal val commands = mutableListOf<PathCommand>()

    val isEmpty: Boolean get() = commands.isEmpty() || (commands.size == 1 && commands[0] is PathCommand.MoveTo)

    /** 最后一个有效命令的终点（Close 不改变当前位置）。 */
    private val lastPosition: Point
        get() {
            val last = commands.lastOrNull { it !is PathCommand.Close }
            return when (last) {
                is PathCommand.MoveTo -> Point(last.x, last.y)
                is PathCommand.LineTo -> Point(last.x, last.y)
                is PathCommand.QuadTo -> Point(last.x, last.y)
                is PathCommand.CubicTo -> Point(last.x, last.y)
                else -> Point(0f, 0f)
            }
        }

    val lastX: Float get() = lastPosition.x
    val lastY: Float get() = lastPosition.y

    fun moveTo(x: Float, y: Float): Path {
        commands.add(PathCommand.MoveTo(x, y))
        return this
    }

    fun lineTo(x: Float, y: Float): Path {
        commands.add(PathCommand.LineTo(x, y))
        return this
    }

    /** 二次贝塞尔曲线：控制点 (cx, cy)，终点 (x, y)。 */
    fun quadTo(cx: Float, cy: Float, x: Float, y: Float): Path {
        commands.add(PathCommand.QuadTo(cx, cy, x, y))
        return this
    }

    /** 三次贝塞尔曲线：两个控制点，终点 (x, y)。 */
    fun cubicTo(cx1: Float, cy1: Float, cx2: Float, cy2: Float, x: Float, y: Float): Path {
        commands.add(PathCommand.CubicTo(cx1, cy1, cx2, cy2, x, y))
        return this
    }

    fun close(): Path {
        commands.add(PathCommand.Close)
        return this
    }

    fun reset(): Path {
        commands.clear()
        return this
    }

    /** 点 (x, y) 是否落在填充区域内（精确判定，由平台 Path.contains 实现）。 */
    fun contains(x: Float, y: Float): Boolean = pathHitTest(commands, x, y)

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
}

/** 平台路径命中测试：命令序列翻译成原生 Path 后判定点是否落在填充区域内。 */
internal expect fun pathHitTest(commands: List<Path.PathCommand>, x: Float, y: Float): Boolean

/**
 * 渐变抽象：`fill` 系列方法的 `gradient` 参数统一接收此类型。
 * 实现：`LinearGradient`（线性）、`RadialGradient`（径向）、`SweepGradient`（扫描）。
 */
interface Gradient

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
) : Gradient {
    init {
        require(colors.size >= 2) { "LinearGradient 至少需要两个颜色，实际 ${colors.size}" }
        requireValidStops(colors, stops, "LinearGradient")
    }
}

/**
 * 径向渐变定义：从圆心 (centerX, centerY) 以半径 [radius] 向外扩散的颜色渐变。
 * 圆外区域使用最后一个颜色延伸（TileMode.CLAMP）。
 *
 * - [colors] 至少两个颜色，按 [stops] 分布（0..1）；[stops] 传 null 时均匀分布。
 */
class RadialGradient(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val colors: List<Int>,
    val stops: List<Float>? = null,
) : Gradient {
    init {
        require(colors.size >= 2) { "RadialGradient 至少需要两个颜色，实际 ${colors.size}" }
        require(radius > 0f) { "RadialGradient radius 必须为正数，实际 $radius" }
        requireValidStops(colors, stops, "RadialGradient")
    }
}

/**
 * 扫描渐变定义：绕圆心 (centerX, centerY) 按角度扫过的颜色渐变。
 * 从正 x 轴方向起顺时针一圈，角度 0°（正右）对应第一个颜色，360° 回到第一个颜色。
 *
 * - [colors] 至少两个颜色，按 [stops] 分布（0..1）；[stops] 传 null 时均匀分布。
 */
class SweepGradient(
    val centerX: Float,
    val centerY: Float,
    val colors: List<Int>,
    val stops: List<Float>? = null,
) : Gradient {
    init {
        require(colors.size >= 2) { "SweepGradient 至少需要两个颜色，实际 ${colors.size}" }
        requireValidStops(colors, stops, "SweepGradient")
    }
}

internal fun requireValidStops(colors: List<Int>, stops: List<Float>?, name: String) {
    if (stops != null) {
        require(stops.size == colors.size) { "$name stops 数量必须与 colors 一致：${stops.size} vs ${colors.size}" }
        require(stops.all { it in 0f..1f }) { "$name stops 必须在 0..1 区间" }
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
