package org.wy.engine.helper

import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.Direction
import org.wy.engine.HitestResult
import org.wy.engine.LayoutNode
import org.wy.engine.LayoutSize
import org.wy.engine.Node
import org.wy.engine.NodeWithPosition
import org.wy.engine.PointerDevice
import org.wy.engine.RectNode
import org.wy.engine.Renderer
import org.wy.engine.SkiaTestBase
import org.wy.engine.outerHeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 通用 tooltip 测试。
 *
 * 动态路径（[attachTooltip] 悬停状态机）：hover 按 400ms 调度、到期经 appendPop 挂载、
 * 移出取消未到期句柄并立即卸载、触摸设备不产生 hover、节点销毁时清理；
 * 静态路径（显式视口几何）：浮层根整层不拦截命中（防 hover 抖动回归）、
 * 面板复用 [resolvePlacement]（贴底自动翻到锚点上方）。
 */
class TooltipTest : SkiaTestBase() {

    /** 定时调度记录器：替代真实定时器，供测试观察调度参数与手动触发到期。 */
    private class TimerRecord {
        var delayMs: Long = -1
            private set
        var canceled = false
            private set
        private var fire: () -> Unit = {}

        fun scheduler(delayMs: Long, action: () -> Unit): () -> Unit {
            this.delayMs = delayMs
            this.fire = action
            return { canceled = true }
        }

        fun trigger() = fire()
    }

    /** 锚点：headless 无布局几何，acceptHit 恒真保证可被 hover 命中链引用。 */
    private class HoverAnchor(context: StateHolder<Node, List<Node>>) : RectNode(context) {
        override fun acceptHit(x: Float, y: Float): Boolean = true
    }

    // ── 动态路径（hover → 调度 → 挂载 → 移出卸载）──

    private class Env {
        val timer = TimerRecord()
        lateinit var renderer: Renderer
        lateinit var anchor: HoverAnchor

        fun build() {
            renderer = object : Renderer(null) {
                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    anchor = HoverAnchor(this)
                    anchor.attachTooltip(scheduler = timer::scheduler) { "保存草稿" }
                }
            }
            renderer.children
        }
    }

    /** 模拟指针移入锚点（Mouse / Touch），并消费批次驱动 TrackSignal。 */
    private fun hover(env: Env, device: PointerDevice = PointerDevice.Mouse) {
        env.renderer.engineGlobal.moveHitTest = HitestResult(
            listOf(NodeWithPosition(env.anchor, 0f, 0f)), 0L, 0f, 0f, device
        )
        flushBatches()
    }

    /** 模拟指针移出锚点。 */
    private fun unhover(env: Env) {
        env.renderer.engineGlobal.moveHitTest = null
        flushBatches()
    }

    /** 在渲染树中递归查找已物化的 tooltip 实例。 */
    private fun findTip(n: Node): TooltipBase? {
        if (n is TooltipBase) return n
        n.children.forEach { c -> findTip(c)?.let { return it } }
        return null
    }

    @Test
    fun hoverSchedulesAfterDelayWithoutMounting() {
        val env = Env().apply { build() }
        flushBatches()
        hover(env)
        assertEquals(400L, env.timer.delayMs, "hover 应按 delayMs=400ms 调度延时显示")
        assertNull(findTip(env.renderer), "未到期不应挂载浮层")
    }

    @Test
    fun unhoverBeforeTimeoutCancelsHandle() {
        val env = Env().apply { build() }
        flushBatches()
        hover(env)
        unhover(env)
        assertTrue(env.timer.canceled, "移出应取消未到期的调度句柄")
        assertNull(findTip(env.renderer), "未到期移出不应挂载浮层")
    }

    @Test
    fun timeoutMountsTooltipIntoTree() {
        val env = Env().apply { build() }
        flushBatches()
        hover(env)
        env.timer.trigger()
        flushBatches()
        assertNotNull(findTip(env.renderer), "调度到期应经 appendPop 物化进渲染树")
    }

    @Test
    fun unhoverAfterShownRemovesTooltipImmediately() {
        val env = Env().apply { build() }
        flushBatches()
        hover(env)
        env.timer.trigger()
        flushBatches()
        assertNotNull(findTip(env.renderer), "到期后浮层应已在渲染树中")

        unhover(env)
        assertNull(findTip(env.renderer), "已显示时移出应立即卸载浮层（removePop）")
    }

    @Test
    fun destroyCancelsPendingSchedule() {
        val env = Env().apply { build() }
        flushBatches()
        hover(env)
        // Renderer.destroy() 级联销毁 state，触发 anchor.addDestroy 注册的 TrackSignal.dispose
        env.renderer.destroy()
        assertTrue(env.timer.canceled, "销毁锚点 / 树时应取消未到期的调度句柄")
    }

    @Test
    fun touchProducesNoHoverSchedule() {
        val env = Env().apply { build() }
        flushBatches()
        hover(env, PointerDevice.Touch)
        assertEquals(-1L, env.timer.delayMs, "触摸设备不应产生 hover，也不应调度显示")
        assertNull(findTip(env.renderer))
    }

    // ── 静态路径（显式视口几何：命中穿透 + 面板翻转）──

    private class StaticEnv {
        lateinit var renderer: Renderer
        lateinit var anchor: LayoutNode
        lateinit var tip: TooltipBase
    }

    private fun buildStatic(): StaticEnv {
        val env = StaticEnv()
        env.renderer = object : Renderer(null) {
            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                // 锚点贴视口底缘（200, 760, 100×30），验证面板放不下时自动翻到上方
                env.anchor = object : RectNode(this) {
                    override fun acceptHit(x: Float, y: Float): Boolean = true

                    override fun argPosition(direction: Direction): Float =
                        if (direction == Direction.x) 200f else 760f

                    override fun argSize(direction: Direction): LayoutSize =
                        LayoutSize(if (direction == Direction.x) 100f else 30f, false)
                }
                env.tip = object : TooltipBase(this, env.anchor, { "保存草稿" }) {
                    // 几何断言用显式视口（1000×800），翻转 / 命中判定才能生效
                    override fun argSize(direction: Direction): LayoutSize =
                        LayoutSize(if (direction == Direction.x) 1000f else 800f, false)
                }
            }
        }
        env.renderer.children
        fun forceBuild(n: Node) {
            n.children.forEach { forceBuild(it) }
        }
        env.tip.children.forEach { forceBuild(it) }
        return env
    }

    @Test
    fun overlayDoesNotCaptureHits() {
        val env = buildStatic()
        assertFalse(
            env.tip.acceptHit(5f, 5f),
            "浮层根铺满窗口必须不拦截命中——否则 moveHitTest 被吞导致 hover 抖动"
        )
    }

    @Test
    fun panelFlipsAboveNearViewportBottom() {
        val env = buildStatic()
        val panel = env.tip.children[0] as LayoutNode
        val ph = panel.outerHeight
        assertEquals(
            760f - 6f - ph, panel.y,
            "锚点贴底放不下 → 面板复用 resolvePlacement 翻到锚点上方（顶缘 - gap - 面板高）"
        )
        assertTrue(panel.y >= 0f && panel.y + ph <= 800f, "提示完整落在视口内")
        assertEquals(200f, panel.x, "BottomStart 水平对齐锚点左缘")
    }
}
