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

/**
 * 轻提示测试：到时自动关闭、点击内容关闭、整层不拦截命中、
 * hide 始终为 false、可注入时间源驱动超时。
 */
class ToastTest {

    /** 可注入时间源与关闭回调的 Toast 测试替身。 */
    private class FakeToast(
        context: StateHolder<Node, List<Node>>,
        durationMs: Long,
        private val clock: () -> Long,
        private val onDismissed: () -> Unit,
    ) : ToastBase(context, durationMs) {
        override fun onDismiss() = onDismissed()
        override fun now(): Long = clock()
    }

    private class Env {
        var t = 0L
        var dismissCount = 0
            private set
        lateinit var toast: FakeToast
        lateinit var renderer: Renderer

        fun build() {
            renderer = object : Renderer(null) {
                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    toast = FakeToast(this, 2000L, { t }) { dismissCount++ }
                }
            }
            renderer.children
        }
    }

    @Test
    fun dismissAfterDurationElapsed() {
        val env = Env()
        env.build()
        env.toast.timeoutEffect() // t=0 开始计时
        assertEquals(0, env.dismissCount, "未到时长不应关闭")

        env.t = 1000
        env.toast.timeoutEffect() // 仍未到
        assertEquals(0, env.dismissCount)

        env.t = 2000
        env.toast.timeoutEffect() // 已到时长
        assertEquals(1, env.dismissCount, "到时自动关闭")
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
