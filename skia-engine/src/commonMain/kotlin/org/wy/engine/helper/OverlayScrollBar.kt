package org.wy.engine.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.ColorInt
import org.wy.engine.Direction
import org.wy.engine.LayoutSize
import org.wy.engine.Node
import org.wy.engine.PlatformCanvas
import org.wy.engine.PointerCapture
import org.wy.engine.PointerEvent
import org.wy.engine.RectNode
import org.wy.engine.Scroll
import org.wy.engine.absoluteX
import org.wy.engine.absoluteY
import org.wy.engine.animation.AnimateSignal
import org.wy.engine.animation.EaseFns
import org.wy.engine.animation.easeOutFn
import org.wy.engine.animation.tween
import org.wy.engine.contentSize
import org.wy.engine.innerHeight
import org.wy.engine.innerSize
import org.wy.engine.innerWidth
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.engine.maxScroll
import org.wy.engine.postDelayed
import org.wy.engine.rgba
import org.wy.engine.withAlpha
import org.wy.lib.EmptyFun
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.memo
import kotlin.math.max

/**
 * 浮层滚动条样式。默认值对照 macOS 系统浮层滚动条：
 * 无轨道、圆角胶囊滑块、静止时半透明、悬停加粗、停止滚动后自动淡出。
 */
data class ScrollBarStyle(
    /** 静止时滑块粗细。 */
    val width: Float = 6f,
    /** 悬停 / 拖拽时的滑块粗细。 */
    val widthHover: Float = 10f,
    /** 贴边可交互感应带宽度（比滑块宽，便于命中）。 */
    val hitWidth: Float = 12f,
    /** 滑块与容器边缘的留白。 */
    val edgeGap: Float = 3f,
    /** 轨道两端留白，滑块不会顶到容器圆角上。 */
    val trackInset: Float = 4f,
    /** 滑块最小长度（避免内容极长时滑块细成一条线）。 */
    val minThumb: Float = 28f,
    /** 滑块基色（alpha 由下面几档单独控制）。 */
    val thumb: ColorInt = rgba(15, 23, 42),
    /** 静止态不透明度。 */
    val idleAlpha: Float = 0.30f,
    /** 悬停态不透明度。 */
    val hoverAlpha: Float = 0.45f,
    /** 拖拽态不透明度。 */
    val dragAlpha: Float = 0.62f,
    /** 停止滚动后多久开始淡出。 */
    val idleDelayMs: Long = 850L,
    /** 淡入时长。 */
    val fadeInMs: Float = 110f,
    /** 淡出时长。 */
    val fadeOutMs: Float = 320f,
)

/**
 * macOS 风格浮层滚动条。
 *
 * 设计要点（与 [SimpleScrollBar] 的区别）：
 * - **不占布局空间**：宽 / 高恒为 0，浮在内容之上绘制，内容区拿满可用尺寸；
 *   传统滚动条要吃掉 10px 布局宽度，右侧内容会被挤压。
 * - **自动隐藏**：静止时完全透明，滚动时淡入，停手 [ScrollBarStyle.idleDelayMs] 后淡出。
 * - **无轨道**：只有一枚半透明胶囊，不画边框 / 轨道底色。
 * - **悬停可发现**：指针移到右侧感应带时以悬停态淡入，进入前不拦截任何指针事件
 *   （`acceptHit` 只在可见时成立，所以静止时的点击可以穿透到内容）。
 * - **拖拽 / 点击翻页**：拖动滑块按 `maxScroll / maxOffset` 换算；点击轨道空白处整页滚动。
 *
 * 几何全部由容器的 `innerSize / contentSize / maxScroll` 推导，不含任何写死的像素位置。
 */
class OverlayScrollBar(
    context: StateHolder<Node, List<Node>>,
    val scroll: Scroll,
    val style: ScrollBarStyle = ScrollBarStyle(),
) : RectNode(context), FlexParam {

    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = scroll.direction
    override val alignFix: Boolean = true
    override val alignItem: AlignItem = AlignItem.stretch
    override val directionJustify: DirectionJustify = DirectionJustify.start

    /** 浮层：两个方向都不占布局空间。 */
    override val argWidth: LayoutSize get() = LayoutSize(0f, false)
    override val argHeight: LayoutSize get() = LayoutSize(0f, false)

    /** 滚动条不是内容，不参与焦点遍历与文本选择。 */
    override val focusable: Boolean get() = false
    override val selectionEnabled: Boolean get() = false

    private val g = engineGlobal

    /** 淡入淡出进度：0 = 完全不可见，1 = 完全可见。 */
    private val fade = AnimateSignal(0f)

    private val dragStore = createSignal(false)

    /**
     * 绘制期落在 CachedPicture 的 memo 里，不允许裸读信号（会触发"禁止非观察处读取"）。
     * 因此所有信号读取都收敛到这三个 memo：绘制 / 命中测试只读 memo。
     */
    private val fadeLevel by memo { fade.value }
    private val dragging by memo { dragStore.value }
    private val geoOf by memo { computeGeo() }

    private var hideHandle: EmptyFun? = null
    private var captureHandle: PointerCapture? = null

    // ── 位置：贴在容器内容盒的右缘 / 下缘 ─────────────────────────
    override val x: Float
        get() {
            val p = layoutParent ?: return 0f
            return if (scroll.direction == Direction.y) {
                p.paddingInlineStart + p.innerWidth - style.hitWidth
            } else {
                p.paddingInlineStart
            }
        }

    override val y: Float
        get() {
            val p = layoutParent ?: return 0f
            return if (scroll.direction == Direction.y) {
                p.paddingBlockStart
            } else {
                p.paddingBlockStart + p.innerHeight - style.hitWidth
            }
        }

    private val viewportLength: Float
        get() = layoutParent?.innerSize(scroll.direction) ?: 0f

    init {
        val off = scroll.addActivityListener { onScrollActivity() }
        addDestroy {
            off()
            hideHandle?.invoke()
            hideHandle = null
            captureHandle?.release()
            captureHandle = null
        }
    }

    // ── 可见性 ────────────────────────────────────────────────────
    /**
     * 指针是否落在贴边感应带内。这里用绝对坐标自行判断，而不是看命中链：
     * 滚动条不可见时 `acceptHit` 为 false、不在命中链上，靠命中链会导致永远唤不出来。
     */
    private val hoveringBand by memo {
        val hit = g.moveHitTest ?: return@memo false
        val bx = absoluteX
        val by = absoluteY
        val len = viewportLength
        if (len <= 0f) return@memo false
        if (scroll.direction == Direction.y) {
            hit.x >= bx && hit.x <= bx + style.hitWidth && hit.y >= by && hit.y <= by + len
        } else {
            hit.y >= by && hit.y <= by + style.hitWidth && hit.x >= bx && hit.x <= bx + len
        }
    }

    private fun currentAlpha(): Float =
        if (dragging || hoveringBand) 1f else fadeLevel

    private val hasScroll: Boolean
        get() = geoOf != null

    /** 立即显示（截图 / 首帧静态渲染用，不依赖动画帧循环）。 */
    fun revealNow() {
        fade.set(1f)
    }

    // ── 几何 ──────────────────────────────────────────────────────
    private class ThumbGeo(
        val offset: Float,
        val length: Float,
        val maxOffset: Float,
        val maxScroll: Float,
    )

    private fun computeGeo(): ThumbGeo? {
        val p = layoutParent ?: return null
        val full = p.innerSize(scroll.direction)
        val viewport = full - style.trackInset * 2f
        if (viewport <= 0f) return null
        val content = p.contentSize(scroll.direction)
        val maxScroll = p.maxScroll(scroll.direction)
        if (maxScroll <= 0f || content <= 0f) return null
        val length = max(style.minThumb, viewport * (full / content)).coerceAtMost(viewport)
        val maxOffset = max(0f, viewport - length)
        val offset = if (maxOffset > 0f) {
            (maxOffset * scroll.value / maxScroll).coerceIn(0f, maxOffset)
        } else 0f
        return ThumbGeo(offset, length, maxOffset, maxScroll)
    }

    // ── 绘制 ──────────────────────────────────────────────────────
    override fun draw(canvas: PlatformCanvas) {
        val geo = geoOf ?: return
        val alpha = currentAlpha()
        if (alpha <= 0.004f) return

        val vertical = scroll.direction == Direction.y
        val expanded = dragging || hoveringBand
        val w = if (expanded) style.widthHover else style.width
        val base = when {
            dragging -> style.dragAlpha
            hoveringBand -> style.hoverAlpha
            else -> style.idleAlpha
        }
        val color = style.thumb.withAlpha(base * alpha)
        val radius = w / 2f

        if (vertical) {
            canvas.fillRoundRect(
                style.hitWidth - style.edgeGap - w,
                style.trackInset + geo.offset,
                w,
                geo.length,
                radius,
                color
            )
        } else {
            canvas.fillRoundRect(
                style.trackInset + geo.offset,
                style.hitWidth - style.edgeGap - w,
                geo.length,
                w,
                radius,
                color
            )
        }
        super.draw(canvas)
    }

    // ── 交互 ──────────────────────────────────────────────────────
    override fun acceptHit(x: Float, y: Float): Boolean {
        if (!hasScroll) return false
        // 只有"看得见"时才拦截指针：静止态让点击穿透到内容
        if (currentAlpha() <= 0.02f) return false
        return if (scroll.direction == Direction.y) {
            x >= 0f && x <= style.hitWidth
        } else {
            y >= 0f && y <= style.hitWidth
        }
    }

    override fun onPointerDown(e: PointerEvent) {
        val geo = geoOf ?: return
        val vertical = scroll.direction == Direction.y
        // 局部坐标里滑块起点是 trackInset + offset
        val p = if (vertical) e.y - style.trackInset else e.x - style.trackInset
        if (p >= geo.offset && p <= geo.offset + geo.length) {
            startDrag(e, geo)
        } else {
            val dir = if (p < geo.offset) -1f else 1f
            scroll.scroll(dir * max(viewportLength - style.trackInset * 2f, 40f) * 0.9f)
        }
        e.stopPropagation()
    }

    private fun startDrag(e: PointerEvent, geo: ThumbGeo) {
        if (geo.maxOffset <= 0f) return
        val vertical = scroll.direction == Direction.y
        val startValue = scroll.value
        val startPointer = if (vertical) e.rootY else e.rootX
        dragStore.value = true
        hideHandle?.invoke()
        hideHandle = null

        var released = false
        fun apply(me: PointerEvent) {
            val pointer = if (vertical) me.rootY else me.rootX
            scroll.value = startValue + (pointer - startPointer) * geo.maxScroll / geo.maxOffset
        }

        val capture = g.capturePointer(
            id = e.id,
            onMove = { me -> if (!released) apply(me) },
            onUp = { me ->
                if (!released) {
                    apply(me)
                    released = true
                    dragStore.value = false
                    captureHandle = null
                    scheduleHide()
                }
            }
        )
        captureHandle = capture
    }

    private fun onScrollActivity() {
        if (dragging) return
        hideHandle?.invoke()
        hideHandle = null
        if (fade.value < 0.999f) {
            fade.animateTo(1f, tween(style.fadeInMs))
        }
        scheduleHide()
    }

    /**
     * 安排淡出。重复调用会刷新计时（滚动过程中计时器一直被推后，等于"停手才开始倒数"）。
     */
    private fun scheduleHide() {
        hideHandle?.invoke()
        hideHandle = postDelayed(style.idleDelayMs) {
            hideHandle = null
            if (!dragging) {
                fade.animateTo(0f, tween(style.fadeOutMs, easeOutFn(EaseFns.cubic)))
            }
        }
    }
}
