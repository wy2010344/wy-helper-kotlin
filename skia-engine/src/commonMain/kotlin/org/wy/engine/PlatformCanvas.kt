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
        color: Int = rgba(0, 0, 0)
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
        color: Int = rgba(0, 0, 0)
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
        color: Int = rgba(0, 0, 0)
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

    fun drawImage(image: PlatformImage, x: Float, y: Float, w: Float, h: Float)

    fun drawParagraph(paragraph: PlatformParagraph, x: Float, y: Float)
}
