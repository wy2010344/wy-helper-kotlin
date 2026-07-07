package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.engine.layout.LayoutSize
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.memo
import org.wy.signal.setValue
import kotlin.math.max

open class ScrollNode(
    context: StateHolder<Node>
) : RectNode(context) {
    open val viewportWidth: Float = 300f
    open val viewportHeight: Float = 200f
    open val showScrollbar: Boolean = true
    open val scrollbarWidth: Float = 8f
    open val scrollbarColor: ColorInt = rgba(0, 0, 0, 30)
    open val scrollbarActiveColor: ColorInt = rgba(0, 0, 0, 60)
    open val wheelStep: Float = 40f

    private var scrollX by createSignal(0f)
    private var scrollY by createSignal(0f)

    private var scrollbarDragging = false
    private var draggingVertical = true
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragStartScrollX = 0f
    private var dragStartScrollY = 0f

    private val contentWidth = memo {
        val pane = children.firstOrNull() as? RectNode ?: return@memo 0f
        var w = 0f
        for (c in pane.children) {
            val r = c as? RectNode ?: continue
            w = max(w, r.position(Direction.x) + r.outerSize(Direction.x))
        }
        w
    }

    private val contentHeight = memo {
        val pane = children.firstOrNull() as? RectNode ?: return@memo 0f
        var h = 0f
        for (c in pane.children) {
            val r = c as? RectNode ?: continue
            h = max(h, r.position(Direction.y) + r.outerSize(Direction.y))
        }
        h
    }

    private val maxScrollX = memo { max(0f, contentWidth() - viewportWidth) }
    private val maxScrollY = memo { max(0f, contentHeight() - viewportHeight) }

    override fun size(direction: Direction): LayoutSize = when (direction) {
        Direction.x -> LayoutSize(viewportWidth, false)
        Direction.y -> LayoutSize(viewportHeight, false)
    }

    override fun draw(canvas: PlatformCanvas) {
        drawSelf(canvas)
        children.forEach {
            canvas.save()
            canvas.translate(it.position(Direction.x), it.position(Direction.y))
            it.draw(canvas)
            canvas.restore()
        }
        if (showScrollbar) drawScrollbar(canvas)
    }

    private fun drawScrollbar(canvas: PlatformCanvas) {
        val vy = viewportHeight
        val cy = contentHeight()
        if (cy > vy) {
            val thumbH = max(20f, vy * vy / cy)
            val maxOff = cy - vy
            val thumbY = if (maxOff > 0f) (vy - thumbH) * scrollY / maxOff else 0f
            canvas.drawRect(
                x = viewportWidth - scrollbarWidth, y = thumbY,
                w = scrollbarWidth, h = thumbH, color = scrollbarColor
            )
        }
        val vx = viewportWidth
        val cx = contentWidth()
        if (cx > vx) {
            val thumbW = max(20f, vx * vx / cx)
            val maxOff = cx - vx
            val thumbX = if (maxOff > 0f) (vx - thumbW) * scrollX / maxOff else 0f
            canvas.drawRect(
                x = thumbX, y = viewportHeight - scrollbarWidth,
                w = thumbW, h = scrollbarWidth, color = scrollbarColor
            )
        }
    }

    private val absX = memo { absolutePosition(Direction.x) }
    private val absY = memo { absolutePosition(Direction.y) }

    override fun mouseDownCapture(e: MouseEvent) {
        if (!showScrollbar) return
        val vw = viewportWidth
        val vh = viewportHeight
        val sx = e.x
        val sy = e.y

        val cy = contentHeight()
        if (cy > vh) {
            val thumbH = max(20f, vh * vh / cy)
            val maxOff = cy - vh
            val thumbY = if (maxOff > 0f) (vh - thumbH) * scrollY / maxOff else 0f
            if (sx >= vw - scrollbarWidth && sx <= vw && sy >= thumbY && sy <= thumbY + thumbH) {
                scrollbarDragging = true
                draggingVertical = true
                dragStartY = sy
                dragStartScrollY = scrollY
                e.stopPropagation()
            }
        }

        val cx = contentWidth()
        if (cx > vw && !scrollbarDragging) {
            val thumbW = max(20f, vw * vw / cx)
            val maxOff = cx - vw
            val thumbX = if (maxOff > 0f) (vw - thumbW) * scrollX / maxOff else 0f
            if (sx >= thumbX && sx <= thumbX + thumbW && sy >= vh - scrollbarWidth && sy <= vh) {
                scrollbarDragging = true
                draggingVertical = false
                dragStartX = sx
                dragStartScrollX = scrollX
                e.stopPropagation()
            }
        }
    }

    init {
        val engineGlobal = context.consume(engineGlobalContext)!!
        val d0 = engineGlobal.registerMouseWheel { _, _, _, deltaY ->
            scrollY = (scrollY - deltaY).coerceIn(0f, maxScrollY())
        }
        val d1 = engineGlobal.registerMouseMove { x, y ->
            if (scrollbarDragging) {
                val localY = y - absY()
                val localX = x - absX()
                if (draggingVertical) {
                    val vh = viewportHeight
                    val ch = contentHeight()
                    val maxOff = ch - vh
                    if (maxOff > 0f) {
                        val thumbH = max(20f, vh * vh / ch)
                        scrollY = (dragStartScrollY + (localY - dragStartY) * maxOff / (vh - thumbH))
                            .coerceIn(0f, maxScrollY())
                    }
                } else {
                    val vw = viewportWidth
                    val cw = contentWidth()
                    val maxOff = cw - vw
                    if (maxOff > 0f) {
                        val thumbW = max(20f, vw * vw / cw)
                        scrollX = (dragStartScrollX + (localX - dragStartX) * maxOff / (vw - thumbW))
                            .coerceIn(0f, maxScrollX())
                    }
                }
            }
        }
        val d2 = engineGlobal.registerMouseUp { _, _ ->
            scrollbarDragging = false
        }
        context.addDestroy { d0(); d1(); d2() }
    }

    protected open fun buildContent(holder: StateHolder<Node>) {}

    final override fun StateHolder<Node>.buildChildren() {
        object : RectNode(this) {
            override fun position(d: Direction): Float = when (d) {
                Direction.x -> -this@ScrollNode.scrollX
                Direction.y -> -this@ScrollNode.scrollY
            }

            override fun size(direction: Direction) = when (direction) {
                Direction.x -> LayoutSize(this@ScrollNode.viewportWidth, false)
                Direction.y -> LayoutSize(this@ScrollNode.viewportHeight, false)
            }

            override fun draw(canvas: PlatformCanvas) {
                val sx = this@ScrollNode.scrollX
                val sy = this@ScrollNode.scrollY
                canvas.clipRect(sx, sy, this@ScrollNode.viewportWidth, this@ScrollNode.viewportHeight)
                super.draw(canvas)
            }

            override fun StateHolder<Node>.buildChildren() {
                this@ScrollNode.buildContent(this)
            }
        }
    }
}
