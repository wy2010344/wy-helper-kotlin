package org.wy.engine.helper

import com.wy.mve.StateHolderWithNode
import org.wy.engine.KeyCode
import org.wy.engine.Node
import org.wy.engine.PointerEvent
import org.wy.engine.PointerType
import org.wy.engine.Renderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 下拉浮层测试：Esc / 点击外部关闭、面板点击不关闭、打开聚焦第一项、
 * 关闭还原焦点、锚点下方定位、不圈定焦点、hide 跟随打开状态。
 */
class DropdownTest {

    /** 构建一个 Renderer + 锚点按钮 + 下拉浮层（含两个可聚焦项）。 */
    private fun buildDropdown(open: Boolean = true): TestEnv {
        val env = TestEnv()
        env.open = open
        env.renderer = object : Renderer(null) {
            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                env.anchor = object : Button(this@argChildren) {
                    override val label: String get() = "菜单"
                }
                env.dd = object : DropdownBase(this@argChildren, env.anchor) {
                    override val enabled: Boolean get() = env.open
                    override fun onDismiss() { env.dismissCount++ }
                    override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
                        env.itemA = object : Button(this) {
                            override val label: String get() = "项 A"
                        }
                        env.itemB = object : Button(this) {
                            override val label: String get() = "项 B"
                        }
                    }
                }
            }
        }
        env.renderer.children
        fun forceBuild(n: Node) {
            n.children.forEach { forceBuild(it) }
        }
        env.dd.children.forEach { forceBuild(it) }
        return env
    }

    class TestEnv {
        lateinit var renderer: Renderer
        lateinit var anchor: ButtonBase
        lateinit var dd: DropdownBase
        lateinit var itemA: ButtonBase
        lateinit var itemB: ButtonBase
        var open = true
        var dismissCount = 0
    }

    @Test
    fun escapeClosesDropdownWhenFocusInside() {
        val env = buildDropdown()
        env.renderer.engineGlobal.focused = env.itemA

        env.renderer.keyPress('\u001b', KeyCode.Escape, false, false, false)
        assertEquals(1, env.dismissCount, "焦点在面板内时 Esc 应关闭")
    }

    @Test
    fun outsideClickCloses() {
        val env = buildDropdown()
        env.dd.onPointerClick(PointerEvent(type = PointerType.Click, x = 5f, y = 5f))
        assertEquals(1, env.dismissCount, "点击浮层空白处应关闭")
    }

    @Test
    fun panelClickDoesNotClose() {
        val env = buildDropdown()
        val panel = env.dd.children[0]
        val e = PointerEvent(type = PointerType.Click, x = 0f, y = 0f)
        panel.onPointerClick(e)
        assertTrue(e.stoppedProgression, "面板点击应拦截冒泡，防止触发关闭")
        assertEquals(0, env.dismissCount)
    }

    @Test
    fun openingFocusesFirstItemInside() {
        val env = buildDropdown()
        env.renderer.engineGlobal.focused = env.anchor
        env.dd.syncFocusNow()
        assertEquals(env.itemA, env.renderer.engineGlobal.focused, "打开时自动聚焦面板第一个可聚焦项")
    }

    @Test
    fun closingRestoresPreviousFocus() {
        val env = buildDropdown()
        env.renderer.engineGlobal.focused = env.anchor
        env.dd.syncFocusNow()

        env.open = false
        env.dd.syncFocusNow()
        assertEquals(env.anchor, env.renderer.engineGlobal.focused, "关闭后还原打开前的焦点")
    }

    @Test
    fun doesNotTrapFocus() {
        val env = buildDropdown()
        assertFalse(env.dd.focusTrap, "轻量浮层不圈定焦点，Tab 可逃逸回主界面")
    }

    @Test
    fun hideFollowsOpenState() {
        val env = buildDropdown()
        assertFalse(env.dd.hide, "打开时可见")

        env.open = false
        assertTrue(env.dd.hide, "关闭时整个节点隐藏")
    }

    @Test
    fun panelWidthUsesFactoryValue() {
        val env = buildDropdown()
        assertEquals(160f, env.dd.panelWidth, "默认面板宽度")
    }
}
