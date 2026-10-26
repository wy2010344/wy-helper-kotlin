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

/**
 * 侧栏导航项测试：点击 / 键盘触发选中（交互继承自 ButtonBase）。
 */
class NavItemTest {

    @Test
    fun clickInvokesOnClick() {
        var clicked = false
        lateinit var item: ButtonBase
        val renderer = object : Renderer(null) {
            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                item = navItem({ "设置" }, { false }, { clicked = true }, focusOrder = 3, badge = { 3 })
            }
        }
        renderer.children

        item.onPointerClick(PointerEvent(type = PointerType.Click, x = 0f, y = 0f))
        assertEquals(true, clicked, "点击应触发 onSelect")
    }

    @Test
    fun enterAndSpaceTriggerWhenFocused() {
        var clicked = false
        lateinit var item: ButtonBase
        val renderer = object : Renderer(null) {
            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                item = navItem({ "设置" }, { false }, { clicked = true })
            }
        }
        renderer.children

        renderer.engineGlobal.focused = item
        renderer.keyPress(' ', KeyCode.Enter, false, false, false)
        assertEquals(true, clicked, "Enter 应触发")

        clicked = false
        renderer.keyPress(' ', KeyCode.Unknown, false, false, false)
        assertEquals(true, clicked, "Space 应触发")
    }

    @Test
    fun disabledNotFocusableAndIgnoresClicks() {
        var clicked = false
        lateinit var item: ButtonBase
        val renderer = object : Renderer(null) {
            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                item = navItem({ "设置" }, { false }, { clicked = true }, enabled = false)
            }
        }
        renderer.children

        assertFalse(item.focusable, "disabled 不应可聚焦")
        item.onPointerClick(PointerEvent(type = PointerType.Click, x = 0f, y = 0f))
        assertEquals(false, clicked, "disabled 不应响应点击")
    }
}
