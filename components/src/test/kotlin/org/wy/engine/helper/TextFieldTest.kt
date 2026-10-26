package org.wy.engine.helper

import org.wy.engine.KeyCode
import org.wy.engine.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 输入框工厂测试：值双向绑定、聚焦后键盘输入写回。
 * 光标 / 选区 / IME 等核心交互由 TextSelectionTest 覆盖。
 */
class TextFieldTest {

    @Test
    fun valueBindsBothDirections() {
        var v = "hi"
        val (state, _) = createHelperEnv()
        val tf = state.textField({ v }, { v = it })

        assertEquals("hi", tf.text, "读取应来自 value")

        tf.text = "world"
        assertEquals("world", v, "写入应回调 onChange")
        assertEquals("world", tf.text, "写回后读取应同步")
    }

    @Test
    fun typingWritesBackThroughOnChange() {
        var v = ""
        val (state, g) = createHelperEnv()
        val tf = state.textField({ v }, { v = it })
        g.focused = tf

        tf.handleKey(KeyEvent('H', KeyCode.Unknown, false, false, false, false))
        tf.handleKey(KeyEvent('i', KeyCode.Unknown, false, false, false, false))
        assertEquals("Hi", v, "键盘输入应写回业务状态")
        assertEquals("Hi", tf.text)
    }
}
