package org.wy.engine

import com.wy.mve.StateHolder

open class ClipNode(
    context: StateHolder<*,*>?,
    open val clipRect: RectF? = null,
    open val clipPath: Path? = null,
    open val borderRadius: Float = 0f
) : Node(context) {

    override fun draw(canvas: PlatformCanvas) {
        canvas.save()
        when {
            clipPath != null -> {
                canvas.clipRect(
                    clipPath!!.let { path ->
                        val bounds = pathBounds(path)
                        bounds.left
                    },
                    clipPath!!.let { pathBounds(it).top },
                    clipPath!!.let { pathBounds(it).width },
                    clipPath!!.let { pathBounds(it).height }
                )
            }
            clipRect != null -> {
                if (borderRadius > 1f) {
                    canvas.clipRRect(
                        clipRect!!.left,
                        clipRect!!.top,
                        clipRect!!.right - clipRect!!.left,
                        clipRect!!.bottom - clipRect!!.top,
                        borderRadius
                    )
                } else {
                    canvas.clipRect(
                        clipRect!!.left,
                        clipRect!!.top,
                        clipRect!!.right - clipRect!!.left,
                        clipRect!!.bottom - clipRect!!.top
                    )
                }
            }
        }
        drawChildren(canvas)
        canvas.restore()
    }

    private fun pathBounds(path: Path): RectF {
        if (clipRect != null) return clipRect!!
        return RectF(0f, 0f, 10000f, 10000f)
    }

    override fun acceptHit(x: Float, y: Float): Boolean {
        if (!super.acceptHit(x, y)) return false
        return when {
            clipPath != null -> clipPath!!.contains(x, y)
            clipRect != null -> {
                if (borderRadius > 0f) {
                    containsRoundedRect(clipRect!!, borderRadius, x, y)
                } else {
                    x >= clipRect!!.left && x <= clipRect!!.right && y >= clipRect!!.top && y <= clipRect!!.bottom
                }
            }
            else -> true
        }
    }

    private fun containsRoundedRect(rect: RectF, radius: Float, x: Float, y: Float): Boolean {
        if (x >= rect.left + radius && x <= rect.right - radius) {
            return y >= rect.top && y <= rect.bottom
        }
        if (y >= rect.top + radius && y <= rect.bottom - radius) {
            return x >= rect.left && x <= rect.right
        }
        val cornerX = if (x < rect.left + radius) rect.left + radius else rect.right - radius
        val cornerY = if (y < rect.top + radius) rect.top + radius else rect.bottom - radius
        val dx = x - cornerX
        val dy = y - cornerY
        return dx * dx + dy * dy <= radius * radius
    }
}
