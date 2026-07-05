package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.engine.layout.LayoutSize
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.memo
import org.wy.signal.setValue
import kotlin.math.max

/**
 * 可滚动的视口容器。
 *
 * 用法：在 buildChildren 中创建子节点，子节点超出 viewportWidth/viewportHeight 的部分
 * 会被裁剪，通过鼠标滚轮滚动。
 *
 * 子节点通过父级布局系统定位（Flex / absoluteLayout 等）。
 * 如果使用默认的 absoluteLayout（所有子节点叠在左上角），
 * 可以在 buildChildren 内嵌入一个 Flex 来排列子节点。
 */
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

    private val contentWidth = memo {
        var w = 0f
        for (c in children) {
            val r = c as? RectNode ?: continue
            w = max(w, r.position(Direction.x) + r.outerSize(Direction.x))
        }
        w
    }

    private val contentHeight = memo {
        var h = 0f
        for (c in children) {
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
        canvas.save()
        canvas.clipRect(0f, 0f, viewportWidth, viewportHeight)
        canvas.translate(-scrollX, -scrollY)
        drawSelf(canvas)
        children.forEach {
            canvas.save()
            canvas.translate(it.position(Direction.x), it.position(Direction.y))
            it.draw(canvas)
            canvas.restore()
        }
        canvas.restore()

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

    override fun StateHolder<Node>.beforeBuildChildren() {
        val engineGlobal = consume(engineGlobalContext)!!
        val d = engineGlobal.registerMouseWheel { _, _, _, deltaY ->
            scrollY = (scrollY - deltaY).coerceIn(0f, maxScrollY())
        }
        addDestroy { d() }
    }
}
