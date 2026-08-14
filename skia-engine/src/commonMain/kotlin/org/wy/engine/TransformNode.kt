package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.memo
import org.wy.signal.setValue

open class TransformNode(
    context: StateHolder<*,*>?,
    open val translation: Pair<Float, Float> = 0f to 0f,
    open val rotation: Float = 0f,
    open val scale: Pair<Float, Float> = 1f to 1f,
    open val skew: Pair<Float, Float> = 0f to 0f
) : Node(context) {

    private val matrix by memo {
        val m = Matrix3f()
        m.translate(translation.first, translation.second)
        m.rotate(rotation)
        m.scale(scale.first, scale.second)
        m.skew(skew.first, skew.second)
        m
    }

    override fun draw(canvas: PlatformCanvas) {
        canvas.save()
        canvas.concatMatrix(matrix())
        drawChildren(canvas)
        canvas.restore()
    }

    override fun acceptHit(x: Float, y: Float): Boolean {
        val inv = matrix().inverted()
        val lx = inv.mapX(x, y)
        val ly = inv.mapY(x, y)
        return super.acceptHit(lx, ly)
    }
}