package org.wy.engine

import com.wy.mve.StateHolderWithNode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 焦点圈定（focusTrap）行为测试：焦点落在弹出层（focusTrap 节点）内时，
 * Tab / Shift+Tab 只在最近的圈定子树内循环；焦点在圈定外时按全局序。
 * 圈定范围由当前焦点沿父链计算获得，引擎不存储任何圈定状态。
 */
class FocusScopeTest {

    private class FocusNode(context: StateHolderWithNode<Node, List<Node>>?, g: EngineGlobal?) : Node(context, g) {
        override val focusable: Boolean = true
    }

    /** 嵌套两层 focusTrap：outerTrap(innerTrap(inner0, inner1), outerInside)，outside 在最外层。 */
    private class TestEnv {
        lateinit var outside: Node
        lateinit var inner0: Node
        lateinit var inner1: Node
        lateinit var outerInside: Node
        lateinit var renderer: Renderer

        init {
            renderer = object : Renderer(null) {
                val g = engineGlobal
                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    outside = FocusNode(this, g)
                    object : Node(this, g) {
                        override val focusTrap: Boolean = true
                        override fun createGetChildren(): () -> List<Node> =
                            context?.renderNode(this, nodeConfig) {
                                object : Node(this, g) {
                                    override val focusTrap: Boolean = true
                                    override fun createGetChildren(): () -> List<Node> =
                                        context?.renderNode(this, nodeConfig) {
                                            inner0 = FocusNode(this, g)
                                            inner1 = FocusNode(this, g)
                                        } ?: { emptyList() }
                                }
                                outerInside = FocusNode(this, g)
                            } ?: { emptyList() }
                    }
                }
            }
            // children 惰性构建：显式触发整棵树构建，建立 parent 链
            fun forceBuild(n: Node) {
                n.children.forEach { forceBuild(it) }
            }
            renderer.children.forEach { forceBuild(it) }
        }
    }

    private fun describe(n: Node?, env: TestEnv): String = when (n) {
        env.outside -> "outside"
        env.inner0 -> "inner0"
        env.inner1 -> "inner1"
        env.outerInside -> "outerInside"
        else -> "?$n"
    }

    private fun TestEnv.tab() = renderer.keyPress('\t', KeyCode.Tab, false, false, false)
    private fun TestEnv.shiftTab() = renderer.keyPress('\t', KeyCode.Tab, false, true, false)

    @Test
    fun focusOutsideTrapUsesGlobalOrder() {
        val env = TestEnv()
        env.renderer.engineGlobal.focused = env.outside

        env.tab()
        assertEquals(env.inner0, env.renderer.engineGlobal.focused,
            "焦点在圈定外时按全局序进入第一个节点，实际=" + describe(env.renderer.engineGlobal.focused, env))
    }

    @Test
    fun tabStaysInsideOuterTrap() {
        val env = TestEnv()
        // 焦点在外层 trap 内（非最内层）→ 圈定整个外层
        env.renderer.engineGlobal.focused = env.outerInside

        env.tab()
        assertEquals(env.inner0, env.renderer.engineGlobal.focused, "外层圈定内前进，不逃逸到 outside")

        env.tab()
        assertEquals(env.inner1, env.renderer.engineGlobal.focused, "外层圈定内前进")

        env.tab()
        assertEquals(env.inner0, env.renderer.engineGlobal.focused,
            "焦点进入内层 trap 后圈定切换为内层（嵌套 trap 从 focused 上溯最近的圈定）")

        // 焦点移出圈定范围（Dialog 关闭恢复焦点）后回到全局序
        env.renderer.engineGlobal.focused = env.outside
        env.tab()
        assertEquals(env.inner0, env.renderer.engineGlobal.focused, "焦点离开圈定后回到全局 Tab 序")
    }

    @Test
    fun tabStaysInsideInnermostTrap() {
        val env = TestEnv()
        env.renderer.engineGlobal.focused = env.inner0

        env.tab()
        assertEquals(env.inner1, env.renderer.engineGlobal.focused)

        env.tab()
        assertEquals(env.inner0, env.renderer.engineGlobal.focused, "最内层圈定内循环，不逃逸到 outerInside")
    }

    @Test
    fun shiftTabCyclesBackwardsInsideTrap() {
        val env = TestEnv()
        env.renderer.engineGlobal.focused = env.outerInside

        env.shiftTab()
        assertEquals(env.inner1, env.renderer.engineGlobal.focused, "圈定内 Shift+Tab 逆向循环（outerInside→inner1）")
    }

    @Test
    fun trapExcludesHiddenAndNonFocusable() {
        val renderer = object : Renderer(null) {
            val g = engineGlobal
            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                object : Node(this, g) {
                    override val focusTrap: Boolean = true
                    override fun createGetChildren(): () -> List<Node> =
                        context?.renderNode(this, nodeConfig) {
                            object : Node(this, g) {
                                override val focusable: Boolean = true
                                override val hide: Boolean = true
                            }
                            FocusNode(this, g)
                            FocusNode(this, g)
                        } ?: { emptyList() }
                }
            }
        }
        renderer.children

        // 收集 trap 子树：应跳过 hidden，只剩两个可见可聚焦节点
        val trap = renderer.children.first()
        val visible = mutableListOf<Node>()
        fun collect(n: Node) {
            if (n.focusable && !n.hide) visible.add(n)
            n.children.forEach(::collect)
        }
        trap.children.forEach(::collect)
        assertEquals(2, visible.size, "隐藏节点不应进入圈定遍历")
    }
}
