package org.wy.engine

expect class PlatformCanvas {
    fun clear(int: Int = rgba(255, 255, 255))

    fun save()

    fun restore()

    fun translate(dx: Float, dy: Float)

    fun clipRect(x: Float, y: Float, w: Float, h: Float)

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

    fun drawParagraph(paragraph: PlatformParagraph, x: Float, y: Float)
}
