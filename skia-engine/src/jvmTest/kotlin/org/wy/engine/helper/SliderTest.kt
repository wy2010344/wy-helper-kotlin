package org.wy.engine.helper

import com.wy.mve.StateHolder
import org.wy.engine.KeyCode
import org.wy.engine.LayoutSize
import org.wy.engine.Node
import org.wy.engine.PointerEvent
import org.wy.engine.PointerType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * 标准滑杆交互测试：指针取值 / 拖动、方向键步进、值域钳制、disabled。
 */
class SliderTest {

    /** 固定轨道宽度的测试子类，便于在无布局环境下计算指针取值。 */
    private class TestSlider(
        context: StateHolder<Node, List<Node>>,
        private val trackW: Float,
        enabled: Boolean = true,
    ) : SliderBase(context, enabled) {
        override val argWidth: LayoutSize get() = LayoutSize(trackW, false)
        var v = 0.5f

        override fun value(): Float = v
        override fun onValueChanged(v: Float) {
            this.v = v
        }

        override fun trackWidth(): Float = trackW
    }

    @Test
    fun pointerDownSetsValueFromPosition() {
        val (state, _) = createHelperEnv()
        val s = TestSlider(state, 100f)
        s.onPointerDown(PointerEvent(type = PointerType.Down, x = 50f, y = 0f))
        assertEquals(0.5f, s.v, 0.001f, "按下位置对应值")

        s.onPointerDown(PointerEvent(type = PointerType.Down, x = 25f, y = 0f))
        assertEquals(0.25f, s.v, 0.001f)
    }

    @Test
    fun pointerDownClampsToUnitRange() {
        val (state, _) = createHelperEnv()
        val s = TestSlider(state, 100f)
        s.onPointerDown(PointerEvent(type = PointerType.Down, x = 200f, y = 0f))
        assertEquals(1f, s.v, 0.001f, "超出右端应钳制为 1")

        s.onPointerDown(PointerEvent(type = PointerType.Down, x = -50f, y = 0f))
        assertEquals(0f, s.v, 0.001f, "超出左端应钳制为 0")
    }

    @Test
    fun dragUpdatesValueWhileCaptured() {
        val (state, g) = createHelperEnv()
        val s = TestSlider(state, 100f)
        s.onPointerDown(PointerEvent(type = PointerType.Down, x = 30f, y = 0f))
        assertEquals(0.3f, s.v, 0.001f)

        g.simulatePointerMove(70f, 0f)
        assertEquals(0.7f, s.v, 0.001f, "捕获期间 move 应持续更新")

        g.simulatePointerUp(90f, 0f)
        assertEquals(0.9f, s.v, 0.001f, "up 应携带最后位置并结束捕获")
    }

    @Test
    fun leftRightKeysStepWhenFocused() {
        val (state, g) = createHelperEnv()
        val s = TestSlider(state, 100f)
        g.focused = s

        g.simulateKeyPress(' ', KeyCode.Left)
        assertEquals(0.45f, s.v, 0.001f, "Left 减 0.05")

        g.simulateKeyPress(' ', KeyCode.Right)
        assertEquals(0.5f, s.v, 0.001f, "Right 加 0.05")
    }

    @Test
    fun keysClampToUnitRange() {
        val (state, g) = createHelperEnv()
        val s = TestSlider(state, 100f)
        g.focused = s

        repeat(12) { g.simulateKeyPress(' ', KeyCode.Left) }
        assertEquals(0f, s.v, 0.001f, "连按 Left 不应低于 0")

        repeat(25) { g.simulateKeyPress(' ', KeyCode.Right) }
        assertEquals(1f, s.v, 0.001f, "连按 Right 不应超过 1")
    }

    @Test
    fun disabledIgnoresPointerAndNotFocusable() {
        val (state, _) = createHelperEnv()
        val s = TestSlider(state, 100f, enabled = false)
        assertFalse(s.focusable, "disabled 不应可聚焦")

        s.onPointerDown(PointerEvent(type = PointerType.Down, x = 50f, y = 0f))
        assertEquals(0.5f, s.v, 0.001f, "disabled 不应响应指针")
    }
}
