package org.wy.engine.helper

import com.wy.mve.Context
import com.wy.mve.DuplicateInfo
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.Node
import org.wy.engine.PlatformCanvas
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
            ): GetValue<List<Node>> = error("PopoverNode does not support renderNode")
            override fun <Node, Target> renderNode(
                node: Node,
                config: com.wy.mve.ShareConfig<Node, Target>,
                callback: StateHolderWithNode<Node, Target>.() -> Unit
            ): GetValue<Target> = error("PopoverNode does not support renderNode")
            override val node: Node get() = error("PopoverNode holder has no node")
            override val target: GetValue<List<Node>> get() = { builtChildren.toList() }
        }
        request.content.invoke(holder)
    }

    private val style = request.style

    fun measureWidth(w: Float) { measuredWidth = w }
    fun measureHeight(h: Float) { measuredHeight = h }

    fun draw(canvas: PlatformCanvas) {
        val w = if (measuredWidth > 0f) measuredWidth else style.defaultWidth
        val h = if (measuredHeight > 0f) measuredHeight else style.defaultHeight

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

        val pad = style.padding
        canvas.save()
        canvas.translate(pad, pad)
        builtChildren.forEach { child ->
            canvas.save()
            canvas.translate(child.x, child.y)
            child.draw(canvas)
            canvas.restore()
        }
        canvas.restore()
    }
}