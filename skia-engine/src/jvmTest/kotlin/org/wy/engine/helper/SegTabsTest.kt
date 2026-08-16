package org.wy.engine.helper

import com.wy.mve.StateHolderWithNode
import org.wy.engine.KeyCode
import org.wy.engine.Node
import org.wy.engine.PointerEvent
import org.wy.engine.PointerType
import org.wy.engine.RectNode
import org.wy.engine.Renderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * 分段控件测试：点击分段选中、键盘选中、disabled 传递。
 * 每个分段复用 ButtonBase 交互（点击 / Enter / Space / disabled），这里验证转发与组合。
 */
class SegTabsTest {

    private fun buildTabs(
        enabled: Boolean = true,
        onSelect: (String) -> Unit = {}
    ): Pair<Renderer, RectNode> {
        var sel = "A"
        lateinit var tabs: RectNode
        val renderer = object : Renderer(null) {
            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                tabs = segTabs({ sel }, onSelect, listOf("A" to "A", "B" to "B"), enabled = enabled)
            }
        }
        renderer.children
        return renderer to tabs
    }

    @Test
    fun clickOnTabSelectsIt() {
        var selected: String? = null
        val (_, tabs) = buildTabs { selected = it }
        val tabB = tabs.children[1] as ButtonBase

        tabB.onPointerClick(PointerEvent(type = PointerType.Click, x = 0f, y = 0f))
        assertEquals("B", selected, "点击第二个分段应选中其值")
    }

    @Test
    fun enterAndSpaceSelectWhenFocused() {
        var selected: String? = null
        val (renderer, tabs) = buildTabs { selected = it }
        val tabA = tabs.children[0] as ButtonBase
        val tabB = tabs.children[1] as ButtonBase

        renderer.engineGlobal.focused = tabB
        renderer.keyPress(' ', KeyCode.Enter, false, false, false)
        assertEquals("B", selected, "Enter 应选中聚焦分段")

        renderer.engineGlobal.focused = tabA
        renderer.keyPress(' ', KeyCode.Unknown, false, false, false)
        assertEquals("A", selected, "Space 应选中聚焦分段")
    }

    @Test
    fun disabledTabsNotFocusableAndIgnoreClicks() {
        var selected: String? = null
        val (_, tabs) = buildTabs(enabled = false) { selected = it }
        val tab = tabs.children[0] as ButtonBase

        assertFalse(tab.focusable, "disabled 时分段不应可聚焦")
        tab.onPointerClick(PointerEvent(type = PointerType.Click, x = 0f, y = 0f))
        assertEquals(null, selected, "disabled 时点击不应选中")
    }
}
