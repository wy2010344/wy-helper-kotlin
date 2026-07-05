package org.wy.engine

expect class PlatformCanvas {
    fun clear(int:Int=rgba(255,255,255,))

    fun save()

    fun restore()

    fun translate(dx: Float,dy: Float)

    fun clipRect(x: Float, y: Float, w: Float, h: Float)

    fun drawRect(
        x: Float=0f,
        y: Float=0f,
        w: Float,
        h: Float,
        color:Int=rgba(0,0,0)
    )

    fun drawText(
        text: String,
        x: Float=0f,
        y: Float=0f,
        fontFamily: String?=null,
        fontWeight:Int=400,
        fontSize: Float=16f,
        color: ColorInt=rgba(0,0,0)
    )


    companion object {
        fun measureText(
            text: String,
            fontFamily: String?=null,
            fontWeight:Int=400,
            fontSize: Float=16f,
            ): Float
    }
}