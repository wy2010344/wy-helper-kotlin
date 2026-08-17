package org.wy.engine.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.Context
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.ColorInt
import org.wy.engine.Direction
import org.wy.engine.EngineGlobal
import org.wy.engine.LayoutSize
import org.wy.engine.Node
import org.wy.engine.PlatformCanvas
import org.wy.engine.PointerEvent
import org.wy.engine.PointF
import org.wy.engine.Pop
import org.wy.engine.RectF
import org.wy.engine.RectNode
import org.wy.engine.StartEnd
import org.wy.engine.WrappedTextNode
import org.wy.engine.fillOuterRoundRect
import org.wy.engine.innerSize
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.engine.rgba
import org.wy.engine.strokeOuterRoundRect
import org.wy.lib.EmptyFun

data class Size(val width: Float, val height: Float)

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

fun interface PopoverPosition {
    fun resolve(anchorRect: RectF, popoverSize: Size): PointF
}

/**
 * Popover 管理器：每个 popover 通过 [EngineGlobal.appendPop] 挂载到真实 MVE 树，
 * 不再使用假树。非重叠（新 popover 可与旧 popover 共存，由业务控制）。
 *
 * 通过 [popoverManagerContext] 提供给子树。
 */
class PopoverManager(private val g: EngineGlobal) {
    private var nextId = 0
    private val pops = mutableMapOf<Int, Pop>()

    fun show(
        content: StateHolder<Node, List<Node>>.() -> Unit,
        anchorRect: RectF,
        position: PopoverPosition = defaultPosition(),
        style: PopoverStyle = PopoverStyle(),
        onDismiss: () -> Unit = {}
    ): EmptyFun {
        val id = nextId++
        val defaultSize = Size(style.defaultWidth, style.defaultHeight)
        val pos = position.resolve(anchorRect, defaultSize)

        val pop = g.appendPop { holder ->
            PopoverNode(this, pos, style, content) {
                dismiss(id)
                onDismiss()
            }
        }
        pops[id] = pop
        return { dismiss(id) }
    }

    fun dismiss(id: Int) {
        val pop = pops.remove(id) ?: return
        g.removePop(pop)
    }

    fun dismissAll() {
        pops.values.forEach { g.removePop(it) }
        pops.clear()
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

/**
 * Popover 容器节点：通过 Pop 机制挂载到真实 MVE 树，支持信号/上下文/生命周期。
 *
 * notInLayout：由 [pos] 手动定位，不参与父节点布局。
 * 内容由 [content] 在真实 StateHolder 中构建，拥有完整 MVE 能力。
 */
class PopoverNode(
    context: StateHolder<Node, List<Node>>,
    private val pos: PointF,
    private val style: PopoverStyle,
    private val content: StateHolder<Node, List<Node>>.() -> Unit,
    private val onDismiss: () -> Unit,
) : RectNode(context), FlexParam {

    override val notInLayout: Boolean get() = true
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.y
    override val directionJustify: DirectionJustify get() = DirectionJustify.grow
    override val alignItem: AlignItem get() = AlignItem.stretch
    override val gap: Float get() = 4f
    override val alignFix: Boolean get() = true

    override fun argPosition(direction: Direction): Float = when (direction) {
        Direction.x -> pos.x
        Direction.y -> pos.y
    }

    override fun argSize(direction: Direction): LayoutSize =
        LayoutSize(0f, false)

    override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
        style.padding

    override fun onPointerClick(e: PointerEvent) {
        e.stopPropagation()
    }

    override fun draw(canvas: PlatformCanvas) {
        // 阴影
        if (style.shadowBlur > 0f) {
            canvas.save()
            canvas.saveLayerAlpha(1f)
            fillOuterRoundRect(canvas, style.cornerRadius, style.shadowColor)
            canvas.restore()
        }
        // 背景
        fillOuterRoundRect(canvas, style.cornerRadius, style.backgroundColor)
        // 边框
        strokeOuterRoundRect(canvas, style.cornerRadius, style.borderColor, style.borderWidth)
        super.draw(canvas)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        content()
        // 关闭按钮
        object : RectNode(this), FlexParam {
            override val argWidth: LayoutSize get() = LayoutSize(60f, false)
            override val argHeight: LayoutSize get() = LayoutSize(24f, false)
            override val alignFix: Boolean get() = true
            override fun draw(canvas: PlatformCanvas) {
                fillOuterRoundRect(canvas, 4f, rgba(230, 230, 240))
                super.draw(canvas)
            }
            override fun onPointerClick(e: PointerEvent) {
                onDismiss()
            }
            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                object : WrappedTextNode(this) {
                    override val autoWidth: Boolean get() = true
                    override val text: String get() = "Close"
                    override val fontSize: Float get() = 12f
                    override val color: ColorInt get() = rgba(80, 80, 100)
                }
            }
        }
    }
}

val popoverManagerContext = Context<PopoverManager?>(null)
