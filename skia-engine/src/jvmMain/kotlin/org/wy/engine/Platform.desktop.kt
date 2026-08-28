package org.wy.engine

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Shader
import org.jetbrains.skia.ImageFilter

actual class PlatformCanvas(val skCanvas: Canvas) {
    actual fun clear(int: Int) {
        skCanvas.clear(int)
    }

    actual fun save() {
        skCanvas.save()
    }

    actual fun restore() {
        skCanvas.restore()
    }

    actual fun translate(dx: Float, dy: Float) {
        skCanvas.translate(dx, dy)
    }

    actual fun rotate(degrees: Float) {
        skCanvas.rotate(degrees)
    }

    actual fun scale(sx: Float, sy: Float) {
        skCanvas.scale(sx, sy)
    }

    actual fun saveLayerAlpha(alpha: Float) {
        skCanvas.saveLayer(
            null,
            Paint().apply { this.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt() }
        )
    }

    actual fun clipRect(x: Float, y: Float, w: Float, h: Float) {
        skCanvas.clipRect(x, y, x + w, y + h, ClipMode.INTERSECT, false)
    }

    actual fun clipRRect(x: Float, y: Float, w: Float, h: Float, radius: Float) {
        skCanvas.clipRRect(RRect.makeXYWH(x, y, w, h, radius.coerceAtLeast(0f)), ClipMode.INTERSECT, false)
    }

    actual fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int, gradient: LinearGradient?) {
        skCanvas.drawRect(x, y, x + w, y + h, Paint().apply {
            isAntiAlias = true
        }.also { paint ->
            applyShaderOrColor(paint, gradient, color)
        })
    }

    actual fun strokeRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color: Int,
        strokeWidth: Float,
    ) {
        skCanvas.drawRect(x, y, x + w, y + h, Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
            setStroke(true)
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
        gradient: LinearGradient?,
    ) {
        skCanvas.drawRRect(RRect.makeXYWH(x, y, w, h, radius.coerceAtLeast(0f)), Paint().apply {
            isAntiAlias = true
        }.also { paint ->
            applyShaderOrColor(paint, gradient, color)
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
        skCanvas.drawRRect(RRect.makeXYWH(x, y, w, h, radius.coerceAtLeast(0f)), Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
            setStroke(true)
            isAntiAlias = true
        })
    }

    actual fun fillOval(x: Float, y: Float, w: Float, h: Float, color: Int, gradient: LinearGradient?) {
        skCanvas.drawOval(x, y, x + w, y + h, Paint().apply {
            isAntiAlias = true
        }.also { paint ->
            applyShaderOrColor(paint, gradient, color)
        })
    }

    private fun applyShaderOrColor(paint: Paint, gradient: LinearGradient?, color: Int) {
        if (gradient != null) {
            paint.shader = Shader.makeLinearGradient(
                gradient.startX, gradient.startY,
                gradient.endX, gradient.endY,
                gradient.colors.toIntArray(),
                gradient.stops?.toFloatArray(),
            )
        } else {
            paint.color = color
        }
    }

    actual fun strokeOval(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color: Int,
        strokeWidth: Float,
    ) {
        skCanvas.drawOval(x, y, x + w, y + h, Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
            setStroke(true)
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
        skCanvas.drawLine(x1, y1, x2, y2, Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
            setStroke(true)
            isAntiAlias = true
        })
    }

    actual fun fillPath(path: Path, color: Int, gradient: LinearGradient?) {
        val paint = Paint().apply { isAntiAlias = true }
        applyShaderOrColor(paint, gradient, color)
        skCanvas.drawPath(toSkiaPath(path), paint)
    }

    actual fun strokePath(path: Path, color: Int, strokeWidth: Float) {
        skCanvas.drawPath(toSkiaPath(path), Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
            setStroke(true)
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
        val sigma = blurSigma.coerceAtLeast(0f)
        skCanvas.saveLayer(
            Rect(x - sigma, y - sigma, x + w + sigma, y + h + sigma),
            Paint().apply {
                imageFilter = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP)
            },
        )
        skCanvas.drawRRect(RRect.makeXYWH(x, y, w, h, radius.coerceAtLeast(0f)), Paint().apply {
            this.color = color
            isAntiAlias = true
        })
        skCanvas.restore()
    }

    private fun toSkiaPath(path: Path): org.jetbrains.skia.Path {
        val builder = org.jetbrains.skia.PathBuilder()
        for (cmd in path.commands) {
            when (cmd) {
                is Path.PathCommand.MoveTo -> builder.moveTo(cmd.x, cmd.y)
                is Path.PathCommand.LineTo -> builder.lineTo(cmd.x, cmd.y)
                is Path.PathCommand.QuadTo -> builder.quadTo(cmd.cx, cmd.cy, cmd.x, cmd.y)
                is Path.PathCommand.CubicTo -> builder.cubicTo(cmd.cx1, cmd.cy1, cmd.cx2, cmd.cy2, cmd.x, cmd.y)
                is Path.PathCommand.Close -> builder.closePath()
            }
        }
        return builder.detach()
    }

    actual fun drawImage(image: PlatformImage, x: Float, y: Float, w: Float, h: Float) {
        skCanvas.drawImageRect(
            image.skImage,
            Rect(0f, 0f, image.skImage.width.toFloat(), image.skImage.height.toFloat()),
            Rect(x, y, x + w, y + h),
            org.jetbrains.skia.SamplingMode.DEFAULT,
            null,
            false
        )
    }

    actual fun drawParagraph(paragraph: PlatformParagraph, x: Float, y: Float) {
        paragraph.paragraph.paint(skCanvas, x, y)
    }
}
