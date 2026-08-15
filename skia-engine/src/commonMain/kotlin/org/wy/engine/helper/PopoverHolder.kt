package org.wy.engine.helper

import com.wy.mve.Context
import com.wy.mve.DuplicateInfo
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.Node
import org.wy.engine.PlatformCanvas
import org.wy.engine.RectNode
import org.wy.lib.EmptyFun
import org.wy.lib.GetValue
import org.wy.signal.Memo

/**
 * PopoverNode：渲染一个 popover 的容器节点。
 * 负责绘制背景/边框/阴影，并调用 content block 构建子节点。
 *
 * 此节点不走正常的 mve 渲染树，由业务层在 Renderer.render() 之后手动调用 draw。
 */
class PopoverNode(
    private val popoverContext: StateHolder<*, *>?,
    private val request: PopoverRequest
) {

    private val builtChildren = mutableListOf<Node>()
    private var measuredWidth = 0f
    private var measuredHeight = 0f
    private val childHeights = mutableMapOf<Int, Float>()

    init {
        val holder = object : StateHolderWithNode<Node, List<Node>> {
            override fun getParent(): Any? = null
            override fun addNode(n: Node) { builtChildren.add(n) }
            override fun <T> provide(context: Context<T>, value: T) {}
            override fun <T> consume(context: Context<T>): T = popoverContext?.consume(context)
                ?: error("No context for popover")
            override fun addDestroy(destroy: EmptyFun) {}
            override val destroyed: Boolean get() = false
            override fun <T, K, O> renderForEach(
                forEach: (callback: (K, T) -> GetValue<O>) -> Unit,
                duplicateInfo: DuplicateInfo,
                creater: com.wy.mve.Creater<Node, List<Node>, T, K, O>
            ): Memo<*> = error("PopoverNode does not support renderForEach")
            override fun renderNode(
                node: Node,
                callback: StateHolderWithNode<Node, List<Node>>.() -> Unit
            ): GetValue<List<Node>> = { builtChildren.toList() }
            override fun <N, Target> renderNode(
                node: N,
                config: com.wy.mve.ShareConfig<N, Target>,
                callback: StateHolderWithNode<N, Target>.() -> Unit
            ): GetValue<Target> = error("PopoverNode does not support ShareConfig renderNode")
            override val node: Node get() = error("PopoverNode holder has no node")
            override val target: GetValue<List<Node>> get() = { builtChildren.toList() }
        }
        request.content.invoke(holder)
    }

    private val style = request.style

    fun measureWidth(w: Float) { measuredWidth = w }
    fun measureHeight(h: Float) { measuredHeight = h }

    fun measure() {
        builtChildren.forEachIndexed { index, child ->
            childHeights[index] = (child as? RectNode)?.argHeight?.value ?: 40f
        }
    }

    fun draw(canvas: PlatformCanvas) {
        val w = if (measuredWidth > 0f) measuredWidth else style.defaultWidth
        val h = if (measuredHeight > 0f) measuredHeight else style.defaultHeight

        // 绘制背景
        if (style.shadowBlur > 0f) {
            canvas.save()
            canvas.saveLayerAlpha(1f)
            canvas.fillRoundRect(
                style.shadowOffsetX, style.shadowOffsetY,
                w, h, style.cornerRadius,
                style.shadowColor
            )
            canvas.restore()
        }

        canvas.fillRoundRect(0f, 0f, w, h, style.cornerRadius, style.backgroundColor)
        canvas.strokeRoundRect(
            0f, 0f, w, h, style.cornerRadius,
            style.borderColor, style.borderWidth
        )

        // 简单垂直布局：逐个 child 往下排
        val pad = style.padding
        var currentY = pad
        builtChildren.forEachIndexed { index, child ->
            canvas.save()
            canvas.translate(pad, currentY)
            child.draw(canvas)
            canvas.restore()
            currentY += childHeights[index] ?: 40f
        }
    }
}