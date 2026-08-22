package org.wy.engine.helper

import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.Node
import org.wy.engine.PointerEvent
import org.wy.engine.PointerType
import org.wy.engine.Renderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 轻提示测试：挂载即倒计时（init 时调度定时器，与渲染无关）、
 * 定时器到期关闭、点击内容关闭、整层不拦截命中、hide 始终为 false、
 * 节点销毁时取消定时器。
 */
class ToastTest {

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

    /** Toast 测试替身：注入 [TimerRecord]，不真正起定时器。 */
    private class FakeToast(
        context: StateHolder<Node, List<Node>>,
        durationMs: Long,
        timer: TimerRecord,
        private val onDismissed: () -> Unit,
    ) : ToastBase(context, durationMs, timer::scheduler) {
        override fun onDismiss() = onDismissed()
    }

    private class Env {
        val timer = TimerRecord()
        var dismissCount = 0
            private set
        lateinit var toast: FakeToast
        lateinit var renderer: Renderer

        fun build() {
            renderer = object : Renderer(null) {
                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    toast = FakeToast(this, 2000L, timer) { dismissCount++ }
                }
            }
            renderer.children
        }
    }

    @Test
    fun schedulesTimerOnInitWithoutDraw() {
        val env = Env()
        env.build()
        assertEquals(2000L, env.timer.delayMs, "init 时即按 durationMs 调度定时器")
        assertEquals(0, env.dismissCount, "未到时不应关闭")
    }

    @Test
    fun timeoutActionTriggersDismiss() {
        val env = Env()
        env.build()
        env.timer.trigger()
        assertEquals(1, env.dismissCount, "定时器到期应自动关闭")
    }

    @Test
    fun destroyCancelsTimer() {
        val env = Env()
        env.build()
        // Renderer.destroy() 级联销毁 root state，触发 addDestroy 注册的取消回调
        env.renderer.destroy()
        assertTrue(env.timer.canceled, "节点销毁时应取消定时器")
    }

    @Test
    fun clickDismisses() {
        val env = Env()
        env.build()
        env.toast.onPointerClick(PointerEvent(type = PointerType.Click, x = 0f, y = 0f))
        assertEquals(1, env.dismissCount, "点击内容应立即关闭")
    }

    @Test
    fun overlayDoesNotCaptureHits() {
        val env = Env()
        env.build()
        assertFalse(env.toast.acceptHit(5f, 5f), "浮层整层不拦截命中，点击穿透到主界面")
    }

    @Test
    fun hideAlwaysFalse() {
        val env = Env()
        env.build()
        assertFalse(env.toast.hide, "Toast 构造即显示，hide 始终为 false")
    }
}
