package org.wy.engine.helper

import org.wy.engine.KeyCode
import org.wy.engine.PointerEvent
import org.wy.engine.PointerType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 标准开关交互测试：点击 / 键盘切换、disabled 忽略交互（交互继承自 ButtonBase）。
 */
class SwitchTest {

    @Test
    fun clickTogglesChecked() {
        var checked = false
        val (state, _) = createHelperEnv()
        val sw = state.switch({ checked }, { checked = it })
        assertFalse(sw.isChecked(), "初始应关闭")

        sw.onPointerClick(PointerEvent(type = PointerType.Click, x = 0f, y = 0f))
        assertTrue(checked, "点击后应开启")
        assertTrue(sw.isChecked())

        sw.onPointerClick(PointerEvent(type = PointerType.Click, x = 0f, y = 0f))
        assertFalse(checked, "再次点击应关闭")
    }

    @Test
    fun enterAndSpaceToggleWhenFocused() {
        var checked = false
        val (state, g) = createHelperEnv()
        val sw = state.switch({ checked }, { checked = it })
        g.focused = sw

        g.simulateKeyPress(' ', KeyCode.Enter)
        assertTrue(checked, "Enter 应开启")

        g.simulateKeyPress(' ')
        assertFalse(checked, "Space 应关闭")

        g.simulateKeyPress('x', KeyCode.Unknown)
        assertFalse(checked, "其它按键不应切换")
    }

    @Test
    fun disabledIgnoresPointerAndKeyAndNotFocusable() {
        var checked = false
        val (state, g) = createHelperEnv()
        val sw = state.switch({ checked }, { checked = it }, enabled = false)
        assertFalse(sw.focusable, "disabled 不应可聚焦")

        g.focused = sw
        g.simulateKeyPress(' ', KeyCode.Enter)
        sw.onPointerClick(PointerEvent(type = PointerType.Click, x = 0f, y = 0f))
        assertFalse(checked, "disabled 不应响应点击与键盘")
    }
}
