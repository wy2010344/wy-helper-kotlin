package org.wy.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader

actual class PlatformCanvas(val canvas: Canvas) {
    actual fun clear(int: Int) {
        canvas.drawColor(int)
    }

    actual fun save() {
        canvas.save()
    }

    actual fun restore() {
        canvas.restore()
    }

    actual fun translate(dx: Float, dy: Float) {
        canvas.translate(dx, dy)
    }

    actual fun rotate(degrees: Float) {
        canvas.rotate(degrees)
    }

    actual fun scale(sx: Float, sy: Float) {
        canvas.scale(sx, sy)
    }

    actual fun saveLayerAlpha(alpha: Float) {
        canvas.saveLayerAlpha(null, (alpha.coerceIn(0f, 1f) * 255f).toInt())
    }

    actual fun clipRect(x: Float, y: Float, w: Float, h: Float) {
        canvas.clipRect(x, y, x + w, y + h)
    }

    actual fun clipRRect(x: Float, y: Float, w: Float, h: Float, radius: Float) {
        canvas.clipRRect(
            android.graphics.RectF(x, y, x + w, y + h),
            radius,
            radius,
            android.graphics.Region.Op.INTERSECT
        )
    }

    actual fun fillRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color: Int,
        gradient: Gradient?,
    ) {
        canvas.drawRect(x, y, x + w, y + h, Paint().apply {
            applyShaderOrColor(gradient, color)
            isAntiAlias = true
        })
    }

    actual fun strokeRect(x: Float, y: Float, w: Float, h: Float, color: Int, strokeWidth: Float) {
        canvas.drawRect(x, y, x + w, y + h, Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
            style = Paint.Style.STROKE
            isAntiAlias = true
        })
    }

    actual fun fillRoundRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float,
        color: Int,
        gradient: Gradient?,
    ) {
        canvas.drawRoundRect(x, y, x + w, y + h, radius, radius, Paint().apply {
            applyShaderOrColor(gradient, color)
            isAntiAlias = true
        })
    }

    actual fun strokeRoundRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float,
        color: Int,
        strokeWidth: Float,
    ) {
        canvas.drawRoundRect(x, y, x + w, y + h, radius, radius, Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
            style = Paint.Style.STROKE
            isAntiAlias = true
        })
    }

    actual fun fillOval(x: Float, y: Float, w: Float, h: Float, color: Int, gradient: Gradient?) {
        canvas.drawOval(x, y, x + w, y + h, Paint().apply {
            applyShaderOrColor(gradient, color)
            isAntiAlias = true
        })
    }

    actual fun strokeOval(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color: Int,
        strokeWidth: Float,
    ) {
        canvas.drawOval(x, y, x + w, y + h, Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
            style = Paint.Style.STROKE
            isAntiAlias = true
        })
    }

    actual fun drawLine(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: Int,
        strokeWidth: Float,
    ) {
        canvas.drawLine(x1, y1, x2, y2, Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
            style = Paint.Style.STROKE
            isAntiAlias = true
        })
    }

    actual fun fillPath(path: Path, color: Int, gradient: Gradient?) {
        canvas.drawPath(toAndroidPath(path), Paint().apply {
            applyShaderOrColor(gradient, color)
            isAntiAlias = true
        })
    }

    actual fun strokePath(path: Path, color: Int, strokeWidth: Float, dash: FloatArray?, phase: Float) {
        canvas.drawPath(toAndroidPath(path), Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
            style = Paint.Style.STROKE
            if (dash != null) {
                pathEffect = android.graphics.DashPathEffect(dash, phase)
            }
            isAntiAlias = true
        })
    }

    actual fun drawShadow(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float,
        blurSigma: Float,
        color: Int,
    ) {
        // saveLayer 内以模糊图充当软阴影，避免直接画实边矩形
        canvas.saveLayer(
            android.graphics.RectF(
                x - blurSigma, y - blurSigma,
                x + w + blurSigma, y + h + blurSigma,
            ),
            null,
        )
        canvas.drawRoundRect(x, y, x + w, y + h, radius, radius, Paint().apply {
            this.color = color
            maskFilter = android.graphics.BlurMaskFilter(
                blurSigma.coerceAtLeast(0f),
                android.graphics.BlurMaskFilter.Blur.NORMAL,
            )
            isAntiAlias = true
        })
        canvas.restore()
    }

    private fun applyShaderOrColor(paint: Paint, gradient: Gradient?, color: Int) {
        when (gradient) {
            null -> paint.color = color
            is LinearGradient -> paint.shader = android.graphics.LinearGradient(
                gradient.startX, gradient.startY,
                gradient.endX, gradient.endY,
                gradient.colors.toIntArray(),
                gradient.stops?.toFloatArray(),
                Shader.TileMode.CLAMP,
            )
            is RadialGradient -> paint.shader = android.graphics.RadialGradient(
                gradient.centerX, gradient.centerY, gradient.radius,
                gradient.colors.toIntArray(),
                gradient.stops?.toFloatArray(),
                Shader.TileMode.CLAMP,
            )
            is SweepGradient -> paint.shader = android.graphics.SweepGradient(
                gradient.centerX, gradient.centerY,
                gradient.colors.toIntArray(),
                gradient.stops?.toFloatArray(),
            )
        }
    }

    private fun toAndroidPath(path: Path): android.graphics.Path = buildAndroidPath(path.commands)

    actual fun drawImage(image: PlatformImage, x: Float, y: Float, w: Float, h: Float) {
        val src = android.graphics.Rect(0, 0, image.bitmap.width, image.bitmap.height)
        val dst = android.graphics.RectF(x, y, x + w, y + h)
        canvas.drawBitmap(image.bitmap, src, dst, Paint().apply { isAntiAlias = true })
    }

    actual fun drawParagraph(paragraph: PlatformParagraph, x: Float, y: Float) {
        paragraph.draw(canvas, x, y)
    }
}

private fun buildAndroidPath(commands: List<Path.PathCommand>): android.graphics.Path {
    val p = android.graphics.Path()
    for (cmd in commands) {
        when (cmd) {
            is Path.PathCommand.MoveTo -> p.moveTo(cmd.x, cmd.y)
            is Path.PathCommand.LineTo -> p.lineTo(cmd.x, cmd.y)
            is Path.PathCommand.QuadTo -> p.quadTo(cmd.cx, cmd.cy, cmd.x, cmd.y)
            is Path.PathCommand.CubicTo -> p.cubicTo(cmd.cx1, cmd.cy1, cmd.cx2, cmd.cy2, cmd.x, cmd.y)
            is Path.PathCommand.Close -> p.close()
        }
    }
    return p
}

internal actual fun pathHitTest(commands: List<Path.PathCommand>, x: Float, y: Float): Boolean =
    buildAndroidPath(commands).contains(x, y)
