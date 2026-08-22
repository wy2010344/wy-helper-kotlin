package org.wy.engine

import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.signal.batchSignalEnd
import org.wy.signal.getValue
import org.wy.signal.setValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 选择范围声明（selectionEnabled）：
 * - 文本可选性不是"实现了 Selectable 就必选"，而是渲染树的派生属性；
 * - 容器声明 selectionEnabled = false 时，本节点及整个子树退出可选集合
 *   （如按钮内嵌文本、装饰性标签），拖选 / 双击 / 全选均不可波及。
 */
class SelectionScopeTest {

    private class TestText(
        context: StateHolder<Node, List<Node>>,
        override val text: String
    ) : WrappedTextNode(context) {
        override val autoWidth: Boolean get() = true
    }

    /** 模拟按钮：声明本子树不参与文本选择。 */
    private class FakeButton(
        context: StateHolder<Node, List<Node>>,
        private val content: StateHolderWithNode<Node, List<Node>>.() -> Unit
    ) : RectNode(context) {
        override val selectionEnabled: Boolean get() = false
        override fun StateHolderWithNode<Node, List<Node>>.argChildren() = content()
    }

    private class Env {
        lateinit var renderer: Renderer
        lateinit var button: FakeButton
        lateinit var insideText: TestText
        lateinit var outsideText: TestText

        val g: EngineGlobal get() = renderer.engineGlobal
        val manager: SelectionManager get() = renderer.engineGlobal.selectionManager

        fun build() {
            renderer = object : Renderer(null) {
                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    button = FakeButton(this) {
                        insideText = TestText(this, "button-label")
                    }
                    outsideText = TestText(this, "free-text")
                }
            }
            renderer.children
            // 模拟一帧布局/绘制：children 是惰性构建的，真实运行时整棵树都会被访问；
            // headless 下显式展开按钮子树，content 声明才会执行
            button.children
        }
    }

    @org.junit.After
    fun drainSignalBatch() {
        Thread.sleep(50)
        batchSignalEnd()
    }

    @Test
    fun disabledSubtreeIsNotSelectable() {
        val env = Env()
        env.build()

        // 全选只覆盖自由文本，按钮内标签出局
        env.manager.selectAll()
        assertEquals("free-text", env.manager.selectedText, "selectionEnabled=false 子树不应参与全选")

        assertFalse(env.manager.isSelectable(env.insideText), "按钮内文本不在可选集合")
        assertTrue(env.manager.isSelectable(env.outsideText), "自由文本在可选集合")
    }

    @Test
    fun pointerSessionOnDisabledSubtreeYieldsNoSelection() {
        val env = Env()
        env.build()

        // 定格指针会话指向按钮内文本：命中链回退找不到任何在册节点 → 选区为空
        env.g.pointerSelect = PointerSelect(
            HitestResult(
                chain = listOf(NodeWithPosition(env.insideText, 0.5f, 5f)),
                time = 0L, x = 0.5f, y = 5f
            ),
            HitestResult(
                chain = listOf(NodeWithPosition(env.insideText, 9f, 5f)),
                time = 0L, x = 9f, y = 5f
            )
        )

        assertNull(env.manager.selectedText, "对不可选子树的拖选不应产生选区")
    }
}
