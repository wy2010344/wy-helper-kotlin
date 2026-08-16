package org.wy.engine.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import org.wy.engine.Direction
import org.wy.engine.KeyCode
import org.wy.engine.LayoutSize
import org.wy.engine.Node
import org.wy.engine.PlatformCanvas
import org.wy.engine.PointerDevice
import org.wy.engine.PointerEvent
import org.wy.engine.RectNode
import org.wy.engine.absoluteX
import org.wy.engine.include
import org.wy.engine.isFocused
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.engine.outerHeight
import org.wy.engine.outerWidth
import org.wy.signal.getValue
import org.wy.signal.memo

/**
 * 标准滑杆交互基类：交互行为内置，样式完全外置。
 *
 * 交互（内置，符合标准）：
 * - 指针按下即取值（按下位置对应值），拖动持续更新，Up 自动结束捕获；
 * - 聚焦时 Left / Right 方向键步进 ±0.05（值域 0..1）；
 * - disabled 时不聚焦（不进 Tab 序）、不响应指针与键盘；
 * - 触摸 / 笔设备不产生 hover。
 *
 * 样式（全部可自定义）：override [drawTrack] / [drawThumb] / [drawFocusRing] /
 * [drawHoverRing] / [argWidth] / [argHeight] 等。默认样式见 [slider]。
 */
open class SliderBase(
    context: StateHolder<Node, List<Node>>,
    protected val enabled: Boolean = true,
) : RectNode(context), FlexParam {
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.x
    override val directionJustify: DirectionJustify get() = DirectionJustify.center
    override val alignItem: AlignItem get() = AlignItem.center
    override val alignFix: Boolean get() = true

    override val focusable: Boolean get() = enabled
    override val focusOrder: Int? get() = null

    protected val g = engineGlobal

    /** 鼠标悬停状态（触摸 / 笔不置位；disabled 无 hover）。 */
    protected val hovered by memo {
        enabled && g.moveHitTest?.include(this) == true && g.lastPointerDevice != PointerDevice.Touch
    }

    /** 当前值 0..1，子类从业务状态派生。 */
    open fun value(): Float = 0f

    /** 值变更回调：指针拖动 / 方向键统一入口，子类 override。 */
    open fun onValueChanged(v: Float) {}

    /** 可拖动轨道宽度（默认撑满自身宽度），绘制与取值统一使用。 */
    open fun trackWidth(): Float = outerWidth

    init {
        context.addDestroy(g.registerKeyPress { e ->
            if (enabled && isFocused) {
                when (e.code) {
                    KeyCode.Left -> onValueChanged((value() - 0.05f).coerceIn(0f, 1f))
                    KeyCode.Right -> onValueChanged((value() + 0.05f).coerceIn(0f, 1f))
                    else -> {}
                }
            }
        })
    }

    override fun onPointerDown(e: PointerEvent) {
        if (!enabled) return
        setFromPointer(e.x)
        context?.let { it.drag(e) { me -> setFromPointer(me.rootX - absoluteX) } }
    }

    private fun setFromPointer(x: Float) {
        onValueChanged((x / trackWidth()).coerceIn(0f, 1f))
    }

    /** 绘制轨道（含已填充段），默认读 [Theme.current]。 */
    open fun drawTrack(canvas: PlatformCanvas, v: Float) {
        val c = Theme.current.colors
        val w = trackWidth()
        canvas.fillRoundRect(0f, outerHeight / 2f - 2f, w, 4f, 2f, c.track)
        if (v > 0f) canvas.fillRoundRect(0f, outerHeight / 2f - 2f, w * v, 4f, 2f, c.primary)
    }

    /** 绘制滑块，默认读 [Theme.current]。 */
    open fun drawThumb(canvas: PlatformCanvas, v: Float) {
        val c = Theme.current.colors
        val w = trackWidth()
        canvas.fillOval(w * v - 6f, outerHeight / 2f - 8f, 16f, 16f, c.primary)
    }

    /** 聚焦时绘制焦点环，默认空。 */
    open fun drawFocusRing(canvas: PlatformCanvas) {}

    /** 悬停时绘制描边，默认空。 */
    open fun drawHoverRing(canvas: PlatformCanvas) {}

    override fun draw(canvas: PlatformCanvas) {
        val v = value()
        drawTrack(canvas, v)
        drawThumb(canvas, v)
        if (isFocused) {
            drawFocusRing(canvas)
        } else if (hovered) {
            drawHoverRing(canvas)
        }
        super.draw(canvas)
    }
}

/**
 * 默认样式滑杆工厂。
 *
 * 样式值全部来自 [Theme.current]；需要更自由的视觉时可改用
 * `object : SliderBase(this) { ... }` 直接覆盖视觉方法。
 */
fun StateHolder<Node, List<Node>>.slider(
    value: () -> Float,
    onChanged: (Float) -> Unit,
    enabled: Boolean = true,
    focusOrder: Int? = null,
    width: Float = 220f,
    height: Float = 20f,
): SliderBase = object : SliderBase(this, enabled) {
    override val argWidth: LayoutSize get() = LayoutSize(width, false)
    override val argHeight: LayoutSize get() = LayoutSize(height, false)
    override val focusOrder: Int? get() = focusOrder

    override fun value(): Float = value()
    override fun onValueChanged(v: Float) = onChanged(v)

    override fun drawFocusRing(canvas: PlatformCanvas) {
        canvas.strokeRoundRect(0f, 0f, outerWidth, outerHeight, outerHeight / 2f, Theme.current.colors.focus, 1f)
    }

    override fun drawHoverRing(canvas: PlatformCanvas) {
        canvas.strokeRoundRect(0f, 0f, outerWidth, outerHeight, outerHeight / 2f, Theme.current.colors.borderHover, 1f)
    }
}
