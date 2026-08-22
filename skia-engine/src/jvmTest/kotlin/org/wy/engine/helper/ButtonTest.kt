package org.wy.engine.helper

import com.wy.mve.StateHolder
import org.wy.engine.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 标准按钮交互测试：验证交互行为内置、不受样式影响。
 */
class ButtonTest {

    /** 暴露 hovered / pressed 供断言的测试子类。 */
    class ExposedButton(
        context: StateHolder<Node, List<Node>>,
        override val enabled: Boolean = true,
    ) : ButtonBase(context) {
        var clickCount = 0
        override fun onClick() {
            clickCount++
        }

        fun isHovered() = hovered
        fun isPressed() = pressed
    }

    private fun createEnv(): Pair<TestStateHolder<Node, List<Node>>, TestEngineGlobal> {
        val state = TestStateHolder<Node, List<Node>>()
        val g = TestEngineGlobal()
        state.provide(engineGlobalContext, g)
        state.provide(selectionManagerContext, SelectionManager())
        return state to g
    }

    private fun hit(btn: Node, device: PointerDevice = PointerDevice.Mouse) =
        HitestResult(listOf(NodeWithPosition(btn, 0f, 0f)), 0L, 0f, 0f, device)

    @Test
    fun clickTriggersOnClick() {
        val (state, _) = createEnv()
        val btn = ExposedButton(state)
        btn.onPointerClick(PointerEvent(type = PointerType.Click, x = 0f, y = 0f))
        assertEquals(1, btn.clickCount)
    }

    @Test
    fun enterAndSpaceTriggerOnClickWhenFocused() {
        val (state, g) = createEnv()
        val btn = ExposedButton(state)
        g.focused = btn
        g.simulateKeyPress(' ', KeyCode.Enter)
        assertEquals(1, btn.clickCount, "Enter 应触发")
        g.simulateKeyPress(' ')
        assertEquals(2, btn.clickCount, "Space 应触发")
        g.simulateKeyPress('x', KeyCode.Unknown)
        assertEquals(2, btn.clickCount, "其它按键不应触发")
    }

    @Test
    fun disabledIgnoresPointerAndKeyAndState() {
        val (state, g) = createEnv()
        val btn = ExposedButton(state, enabled = false)
        assertFalse(btn.focusable, "disabled 不应可聚焦")

        g.focused = btn
        g.simulateKeyPress(' ', KeyCode.Enter)
        btn.onPointerClick(PointerEvent(type = PointerType.Click, x = 0f, y = 0f))
        assertEquals(0, btn.clickCount, "disabled 不应响应点击与键盘")

        g.moveHitTest = hit(btn)
        g.pointerSelect = PointerSelect(hit(btn), null)
        assertFalse(btn.isHovered(), "disabled 不应有 hover 状态")
        assertFalse(btn.isPressed(), "disabled 不应有 pressed 状态")
    }

    @Test
    fun touchDeviceProducesNoHoverButMouseDoes() {
        val (state, g) = createEnv()
        val btn = ExposedButton(state)
        g.moveHitTest = hit(btn)

        // 设备类型随命中结果携带（HitestResult.device），触摸不产生 hover
        g.moveHitTest = hit(btn, PointerDevice.Touch)
        assertFalse(btn.isHovered(), "触摸设备不应产生 hover")

        g.moveHitTest = hit(btn, PointerDevice.Mouse)
        assertTrue(btn.isHovered(), "鼠标设备应产生 hover")
    }

    @Test
    fun pressedFollowsPressSignal() {
        val (state, g) = createEnv()
        val btn = ExposedButton(state)
        g.moveHitTest = hit(btn)
        assertFalse(btn.isPressed())

        g.pointerSelect = PointerSelect(hit(btn), null)
        assertTrue(btn.isPressed(), "命中且按下时 pressed 置位")

        g.pointerSelect = null
        assertFalse(btn.isPressed(), "松开后 pressed 复位")
    }
}
