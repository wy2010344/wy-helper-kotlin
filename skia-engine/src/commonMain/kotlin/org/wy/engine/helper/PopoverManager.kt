package org.wy.engine.helper

import com.wy.mve.Context
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.ColorInt
import org.wy.engine.Node
import org.wy.engine.RectF
import org.wy.engine.rgba
import org.wy.lib.EmptyFun
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.memo
import org.wy.signal.setValue

data class Size(val width: Float, val height: Float)
data class PointF(val x: Float, val y: Float)

data class PopoverStyle(
    val backgroundColor: ColorInt = rgba(255, 255, 255),
    val borderColor: ColorInt = rgba(200, 200, 210),
    val borderWidth: Float = 1f,
    val cornerRadius: Float = 8f,
    val shadowColor: ColorInt = rgba(0, 0, 0, 40),
    val shadowOffsetX: Float = 0f,
    val shadowOffsetY: Float = 2f,
    val shadowBlur: Float = 8f,
    val padding: Float = 12f,
    val defaultWidth: Float = 300f,
    val defaultHeight: Float = 200f
)

/** 弹窗定位：基于锚点矩形计算最终位置 */
fun interface PopoverPosition {
    fun resolve(anchorRect: RectF, popoverSize: Size): PointF
}

data class PopoverRequest(
    val id: Int,
    var content: (StateHolderWithNode<Node, List<Node>>) -> Unit,
    var position: PopoverPosition,
    var style: PopoverStyle = PopoverStyle(),
    var dismissed: Boolean = false
)

/**
 * PopoverManager：管理所有活跃的 popover 请求。
 * 由业务层创建并通过 popoverManagerContext 提供给子树。
 */
class PopoverManager {
    private var nextId = 0
    private val requests = mutableMapOf<Int, PopoverRequest>()
    private var version by createSignal(0)

    val popovers by memo {
        version
        requests.values.filter { !it.dismissed }.toList()
    }

    private val positions = mutableMapOf<Int, PointF>()
    private val nodes = mutableMapOf<Int, PopoverNode>()

    fun show(
        content: (StateHolderWithNode<Node, List<Node>>) -> Unit,
        anchorRect: RectF,
        position: PopoverPosition = defaultPosition(),
        style: PopoverStyle = PopoverStyle()
    ): EmptyFun {
        val id = nextId++
        val req = PopoverRequest(id, content, position, style)
        requests[id] = req
        val defaultSize = Size(style.defaultWidth, style.defaultHeight)
        val pos = position.resolve(anchorRect, defaultSize)
        positions[id] = pos
        version++
        return { dismiss(id) }
    }

    fun dismiss(id: Int) {
        val req = requests[id]
        if (req != null && !req.dismissed) {
            req.dismissed = true
            nodes.remove(id)
            version++
        }
    }

    fun dismissAll() {
        if (requests.isNotEmpty()) {
            requests.values.forEach { it.dismissed = true }
            requests.clear()
            positions.clear()
            nodes.clear()
            version++
        }
    }

    fun getPosition(id: Int): PointF? = positions[id]

    fun getNode(id: Int, stateHolder: StateHolder<*, *>?): PopoverNode? {
        val req = requests[id] ?: return null
        if (req.dismissed) return null
        return nodes.getOrPut(id) {
            PopoverNode(stateHolder, req)
        }
    }

    fun updatePosition(id: Int, anchorRect: RectF, popoverSize: Size) {
        val req = requests[id] ?: return
        if (!req.dismissed) {
            positions[id] = req.position.resolve(anchorRect, popoverSize)
        }
    }

    companion object {
        fun defaultPosition(): PopoverPosition = PopoverPosition { anchorRect, _ ->
            val x = anchorRect.left.coerceAtLeast(4f)
            val y = anchorRect.bottom + 4f
            PointF(x, y)
        }

        fun centeredAbove(): PopoverPosition = PopoverPosition { anchorRect, popoverSize ->
            val x = anchorRect.centerX - popoverSize.width / 2f
            val y = anchorRect.top - popoverSize.height - 4f
            PointF(x.coerceAtLeast(4f), y.coerceAtLeast(4f))
        }
    }
}

val popoverManagerContext = Context<PopoverManager?>(null)