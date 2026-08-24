package org.wy.engine

import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.signal.batchSignalEnd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 双击 / 三击路径的点击派发与按压态回归测试。
 *
 * 背景：双击 / 三击按下刻意不开 pointerSelect 会话（holding 会话会压制程序化词选区），
 * 但 EngineGlobal.pressed 曾从会话派生——导致：
 *   1. 双击 / 三击的第二次、第三次 mouseUp 快照不到按下记录，onClick 不派发；
 *   2. 按住期间按钮等组件的按压视觉反馈失效。
 * 修复方向：pressed 改由引擎在每次 Down / Up 维护的独立信号驱动，与会话解耦。
 */
class PointerClickDispatchTest {

    /** 任意坐标都可命中的测试文本节点（headless 无布局，绕过几何边界）。 */
    private class TestText(
        context: StateHolder<Node, List<Node>>,
        override val text: String
    ) : WrappedTextNode(context) {
        override val autoWidth: Boolean get() = true
        override fun acceptHit(x: Float, y: Float): Boolean = true

        var clicks = 0
        override fun onPointerClick(e: PointerEvent) {
            clicks++
        }
    }

    private class Env(text: String = "Hello world") {
        lateinit var renderer: Renderer
        lateinit var textNode: TestText

        val g: EngineGlobal get() = renderer.engineGlobal

        init {
            renderer = object : Renderer(null) {
                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    textNode = TestText(this, text)
                }
            }
            renderer.children
        }
    }

    @org.junit.After
    fun drainSignalBatch() {
        Thread.sleep(50)
        batchSignalEnd()
    }

    @Test
    fun everyUpOfMultiClickDispatchesClick() {
        val env = Env()

        // 单击
        env.renderer.mouseDown(5f, 5f)
        env.renderer.mouseUp(5f, 5f)
        assertEquals(1, env.textNode.clicks, "单击应派发一次 Click")

        // 双击的第二下：按下不开会话，但松手仍应派发 Click
        env.renderer.mouseDown(6f, 4f)
        env.renderer.mouseUp(6f, 4f)
        assertEquals(2, env.textNode.clicks, "双击第二次松手也应派发 Click")

        // 三击的第三下同理
        env.renderer.mouseDown(5f, 5f)
        env.renderer.mouseUp(5f, 5f)
        assertEquals(3, env.textNode.clicks, "三击第三次松手也应派发 Click")
    }

    @Test
    fun pressedReflectsHoldEvenWithoutSession() {
        val env = Env()

        assertNull(env.g.pressed, "未按下时无按压态")

        // 单击按住：有会话，按压态生效
        env.renderer.mouseDown(5f, 5f)
        assertNotNull(env.g.pressed, "按住期间应有按压态")
        env.renderer.mouseUp(5f, 5f)
        assertNull(env.g.pressed, "松手后按压态消失")

        // 双击第二下按住：无会话，按压态同样必须生效
        env.renderer.mouseDown(6f, 4f)
        assertNotNull(env.g.pressed, "双击按住期间（无会话）按压态不应失效")
        env.renderer.mouseUp(6f, 4f)
        assertNull(env.g.pressed, "松手后按压态消失")
    }
}
