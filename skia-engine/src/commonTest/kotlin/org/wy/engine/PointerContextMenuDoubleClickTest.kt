package org.wy.engine

import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.signal.batchSignalEnd
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 右键（ContextMenu）与双击（DoubleClick）事件的派发语义测试。
 *
 * 关键语义：
 * - 右键按下派发 ContextMenu，**不派发 Down**（否则右键会启动文本拖选等左键专属行为），
 *   右键松手也不派发 Click；
 * - 双击不吞单击：同一次双击先派发 Click 再补发 DoubleClick；连点序列中
 *   双击只触发一次（第三次点击重新计为单击，与浏览器一致）；
 * - 事件携带 button（触发键编号）与 buttons（按住位掩码），引擎自跟踪按住状态。
 */
class PointerContextMenuDoubleClickTest {

    /** 任意坐标都可命中的测试文本节点（headless 无布局，绕过几何边界）。 */
    private class TestText(
        context: StateHolder<Node, List<Node>>,
        override val text: String
    ) : WrappedTextNode(context) {
        override val autoWidth: Boolean get() = true
        override fun acceptHit(x: Float, y: Float): Boolean = true

        var downs = 0
        var clicks = 0
        var doubleClicks = 0
        var contextMenuCapture = 0
        var contextMenuBubble = 0
        var downButtons = -1

        override fun onPointerDown(e: PointerEvent) {
            downs++
            downButtons = e.buttons
        }

        override fun onPointerClick(e: PointerEvent) {
            clicks++
        }

        override fun onPointerDoubleClick(e: PointerEvent) {
            doubleClicks++
        }

        override fun onPointerContextMenuCapture(e: PointerEvent) {
            contextMenuCapture++
        }

        override fun onPointerContextMenu(e: PointerEvent) {
            contextMenuBubble++
        }
    }

    private class Env(text: String = "Hello world") {
        lateinit var renderer: Renderer
        lateinit var textNode: TestText

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
        runOnUiThread {
            Thread.sleep(50)
            batchSignalEnd()
        }
    }

    @Test
    fun rightClickDispatchesContextMenuWithoutDownOrClick() {
        runOnUiThread {
            val env = Env()
            env.renderer.mouseDown(5f, 5f, button = PointerButton.RIGHT)
            env.renderer.mouseUp(5f, 5f, button = PointerButton.RIGHT)

            assertEquals(1, env.textNode.contextMenuCapture, "右键按下应派发 ContextMenu（捕获）")
            assertEquals(1, env.textNode.contextMenuBubble, "右键按下应派发 ContextMenu（冒泡）")
            assertEquals(0, env.textNode.downs, "右键不应派发 Down，否则退化为假左键")
            assertEquals(0, env.textNode.clicks, "右键松手不应派发 Click")
        }
    }

    @Test
    fun doubleClickDispatchesClickThenDoubleClick() {
        runOnUiThread {
            val env = Env()
            env.renderer.mouseDown(5f, 5f)
            env.renderer.mouseUp(5f, 5f)
            assertEquals(0, env.textNode.doubleClicks, "第一次单击不构成双击")
            assertEquals(1, env.textNode.clicks)

            // 第二次点击：间隔（毫秒级连续调用）与落点均在阈值内
            env.renderer.mouseDown(6f, 4f)
            env.renderer.mouseUp(6f, 4f)
            assertEquals(2, env.textNode.clicks, "双击不吞单击：第二次仍应派发 Click")
            assertEquals(1, env.textNode.doubleClicks, "第二次点击应补发 DoubleClick")
        }
    }

    @Test
    fun tripleClickSequenceYieldsOnlyOneDoubleClick() {
        runOnUiThread {
            val env = Env()
            repeat(3) { i ->
                val x = 5f + i * 0.5f
                env.renderer.mouseDown(x, 5f)
                env.renderer.mouseUp(x, 5f)
            }
            assertEquals(3, env.textNode.clicks)
            assertEquals(1, env.textNode.doubleClicks, "连点序列中双击只应触发一次（第二下）")
        }
    }

    @Test
    fun distantSecondClickIsNotDouble() {
        runOnUiThread {
            val env = Env()
            env.renderer.mouseDown(5f, 5f)
            env.renderer.mouseUp(5f, 5f)
            // 落点相距远超 5px：不构成双击
            env.renderer.mouseDown(60f, 60f)
            env.renderer.mouseUp(60f, 60f)
            assertEquals(2, env.textNode.clicks)
            assertEquals(0, env.textNode.doubleClicks, "落点过远的两次单击不应判为双击")
        }
    }

    @Test
    fun buttonsMaskTracksHeldKeys() {
        runOnUiThread {
            val env = Env()
            env.renderer.mouseDown(5f, 5f)
            assertEquals(
                PointerButton.LEFT_DOWN_MASK, env.textNode.downButtons,
                "按住期间 Down 事件应携带左键按住位"
            )
            env.renderer.mouseUp(5f, 5f)

            // 松手后再按下，位掩码不应残留
            env.renderer.mouseDown(5f, 5f)
            assertEquals(PointerButton.LEFT_DOWN_MASK, env.textNode.downButtons)
            env.renderer.mouseUp(5f, 5f)
        }
    }
}
