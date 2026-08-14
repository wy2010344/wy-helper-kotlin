package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.signal.getValue
import org.wy.signal.memo

open class SelectionOverlayNode(
    context: StateHolder<*,*>?,
    private val selectionColor: ColorInt = rgba(0, 100, 200, 60)
) : Node(context) {

    override fun draw(canvas: PlatformCanvas) {
        val selMgr = context?.consume(selectionManagerContext) ?: run {
            super.draw(canvas)
            return
        }
        val rects = selMgr.coveredRects
        for (rect in rects) {
            canvas.fillRect(
                x = rect.left,
                y = rect.top,
                w = rect.width,
                h = rect.height,
                color = selectionColor
            )
        }
        super.draw(canvas)
    }
}
