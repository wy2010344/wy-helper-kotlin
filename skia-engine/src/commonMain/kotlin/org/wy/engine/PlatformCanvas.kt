package org.wy.engine

expect class PlatformCanvas {
    fun clear(int: Int = rgba(255, 255, 255))

    fun save()

    fun restore()

    fun translate(dx: Float, dy: Float)

    fun rotate(degrees: Float)

    fun scale(sx: Float, sy: Float)

    /**
     * 开启一个透明度层，之后的绘制都会以 [alpha] 合成到画布。
     * 必须与 [save] 配对使用一次 [restore]。
     */
    fun saveLayerAlpha(alpha: Float)

    fun clipRect(x: Float, y: Float, w: Float, h: Float)

    fun clipRRect(x: Float, y: Float, w: Float, h: Float, radius: Float)

    fun fillRect(
        x: Float = 0f,
        y: Float = 0f,
        w: Float,
        h: Float,
        color: Int = rgba(0, 0, 0),
        gradient: Gradient? = null,
    )

    fun strokeRect(
        x: Float = 0f,
        y: Float = 0f,
        w: Float,
        h: Float,
        color: Int = rgba(0, 0, 0),
        strokeWidth: Float = 1f,
    )

    fun fillRoundRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float,
        color: Int = rgba(0, 0, 0),
        gradient: Gradient? = null,
    )

    fun strokeRoundRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float,
        color: Int = rgba(0, 0, 0),
        strokeWidth: Float = 1f,
    )

    fun fillOval(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color: Int = rgba(0, 0, 0),
        gradient: Gradient? = null,
    )

    fun strokeOval(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color: Int = rgba(0, 0, 0),
        strokeWidth: Float = 1f,
    )

    fun drawLine(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: Int = rgba(0, 0, 0),
        strokeWidth: Float = 1f,
    )

    /**
     * 填充路径：[path] 的当前子路径（moveTo/lineTo/quadTo/cubicTo/close）围成的区域。
     * [gradient] 非 null 时用线性渐变填充，忽略 [color]。
     */
    fun fillPath(
        path: Path,
        color: Int = rgba(0, 0, 0),
        gradient: Gradient? = null,
    )

    /**
     * 描边路径：沿 [path] 绘制 strokeWidth 宽度线条。
     * [dash] 非 null 时为虚线，格式 [实线长, 空白长, ...]，phase 为起始偏移。
     */
    fun strokePath(
        path: Path,
        color: Int = rgba(0, 0, 0),
        strokeWidth: Float = 1f,
        dash: FloatArray? = null,
        phase: Float = 0f,
    )

    /**
     * 软阴影：在 (x, y, w, h) 圆角矩形上绘制模糊阴影（结果合成到画布）。
     * 阴影经 saveLayer + blur 实时合成，圆角由 [radius] 决定，模糊强度由 [blurSigma] 决定。
     * [color] 建议带透明度的阴影色（如 rgba(0,0,0,60)）。
     */
    fun drawShadow(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float,
        blurSigma: Float,
        color: Int,
    )

    fun drawImage(image: PlatformImage, x: Float, y: Float, w: Float, h: Float)

    fun drawParagraph(paragraph: PlatformParagraph, x: Float, y: Float)
}
