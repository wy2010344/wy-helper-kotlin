package org.wy.engine

import com.wy.mve.StateHolder

open class OpacityNode(
    context: StateHolder<*,*>?,
    open val opacity: Float = 1f
) : Node(context) {

    override fun draw(canvas: PlatformCanvas) {
        canvas.save()
        canvas.saveLayerAlpha(opacity.coerceIn(0f, 1f))
        drawChildren(canvas)
        canvas.restore()
    }
}