package org.wy.engine.helper

import com.wy.mve.StateHolderWithNode
import org.wy.engine.KeyCode
import org.wy.engine.Node
import org.wy.engine.PointerEvent
import org.wy.engine.PointerType
import org.wy.engine.Renderer
import org.wy.engine.absoluteX
import org.wy.engine.absoluteY
import org.wy.engine.outerHeight
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
    private fun buildDropdown(enabled: Boolean = true): TestEnv {
        val env = TestEnv()
        env.renderer = object : Renderer(null) {
            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                env.anchor = button({ "菜单" }, {})
                env.dd = dropdown(
                    { env.open },
                    { env.anchor },
                    { env.dismissCount++ },
                    enabled = enabled,
                ) {
                    env.itemA = button({ "项 A" }, {})
                    env.itemB = button({ "项 B" }, {})
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
    fun panelPositionedBelowAnchor() {
        val env = buildDropdown()
        val panel = env.dd.children[0]
        assertEquals(env.anchor.absoluteX, panel.absoluteX, "面板左边缘对齐锚点")
        assertEquals(
            env.anchor.absoluteY + env.anchor.outerHeight + env.dd.offsetY,
            panel.absoluteY,
            "面板位于锚点底部下方"
        )
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
