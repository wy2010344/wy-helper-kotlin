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
 * 模态对话框测试：Esc / 遮罩点击关闭、面板点击不关闭、
 * 打开聚焦面板内第一个可聚焦元素、关闭还原焦点、disabled 不响应、hide 跟随打开状态。
 */
class DialogTest {

    /** 构建一个 Renderer + 对话框 + 两个按钮（一个在对话框外作对比）。 */
    private fun buildDialog(enabled: Boolean = true): TestEnv {
        val env = TestEnv()
        env.renderer = object : Renderer(null) {
            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                env.outside = button({ "外部按钮" }, {})
                env.dlg = dialog({ env.open }, { env.dismissCount++ }, enabled = enabled) {
                    env.okBtn = button({ "确定" }, {})
                    env.cancelBtn = button({ "取消" }, {})
                }
            }
        }
        env.renderer.children
        // 惰性构建：展开 dialog 子树（panel → 按钮），建立 parent 链
        fun forceBuild(n: Node) {
            n.children.forEach { forceBuild(it) }
        }
        env.dlg.children.forEach { forceBuild(it) }
        return env
    }

    class TestEnv {
        lateinit var renderer: Renderer
        lateinit var dlg: DialogBase
        lateinit var outside: ButtonBase
        lateinit var okBtn: ButtonBase
        lateinit var cancelBtn: ButtonBase
        var open = true
        var dismissCount = 0
    }

    @Test
    fun escapeClosesDialogWhenFocusInside() {
        val env = buildDialog()
        env.renderer.engineGlobal.focused = env.okBtn

        env.renderer.keyPress('\u001b', KeyCode.Escape, false, false, false)
        assertEquals(1, env.dismissCount, "焦点在对话框内时 Esc 应关闭")
    }

    @Test
    fun escapeIgnoredWhenFocusOutside() {
        val env = buildDialog()
        env.renderer.engineGlobal.focused = env.outside

        env.renderer.keyPress('\u001b', KeyCode.Escape, false, false, false)
        assertEquals(0, env.dismissCount, "焦点在对话框外时 Esc 不应关闭（避免关闭最上层之外的对话框）")
    }

    @Test
    fun scrimClickCloses() {
        val env = buildDialog()
        env.dlg.onPointerClick(PointerEvent(type = PointerType.Click, x = 5f, y = 5f))
        assertEquals(1, env.dismissCount, "点击遮罩应关闭")
    }

    @Test
    fun panelClickDoesNotClose() {
        val env = buildDialog()
        val panel = env.dlg.children[0]
        val e = PointerEvent(type = PointerType.Click, x = 0f, y = 0f)
        panel.onPointerClick(e)
        assertTrue(e.stoppedProgression, "面板点击应拦截冒泡，防止触发遮罩关闭")
        assertEquals(0, env.dismissCount)
    }

    @Test
    fun openingFocusesFirstFocusableInside() {
        val env = buildDialog()
        // 打开前焦点在外部 → 同步后移入对话框内第一个可聚焦元素
        env.renderer.engineGlobal.focused = env.outside
        env.dlg.syncFocusNow()
        assertEquals(env.okBtn, env.renderer.engineGlobal.focused, "打开时自动聚焦面板第一个可聚焦元素")
    }

    @Test
    fun closingRestoresPreviousFocus() {
        val env = buildDialog()
        env.renderer.engineGlobal.focused = env.outside
        env.dlg.syncFocusNow() // open：移入 okBtn

        env.open = false
        env.dlg.syncFocusNow() // close：还原打开前焦点
        assertEquals(env.outside, env.renderer.engineGlobal.focused, "关闭后还原打开前的焦点")
    }

    @Test
    fun repeatedFocusSyncKeepsOriginalSavedFocus() {
        val env = buildDialog()
        env.renderer.engineGlobal.focused = env.outside
        env.dlg.syncFocusNow() // open：记录外部焦点，移入 okBtn
        env.renderer.engineGlobal.focused = env.cancelBtn
        env.dlg.syncFocusNow() // 常驻效果再次同步：不应覆盖保存的原始焦点

        env.open = false
        env.dlg.syncFocusNow()
        assertEquals(env.outside, env.renderer.engineGlobal.focused, "还原的应是打开前的原始焦点")
    }

    @Test
    fun disabledDialogDoesNotDismissOrFocus() {
        val env = buildDialog(enabled = false)
        env.renderer.engineGlobal.focused = env.okBtn
        env.renderer.keyPress('\u001b', KeyCode.Escape, false, false, false)
        assertEquals(0, env.dismissCount, "disabled 时 Esc 不应关闭")

        env.dlg.syncFocusNow()
        assertTrue(env.dlg.hide, "disabled 对话框应隐藏，不拦截事件")
    }

    @Test
    fun hideFollowsOpenState() {
        val env = buildDialog()
        assertFalse(env.dlg.hide, "打开时可见")

        env.open = false
        assertTrue(env.dlg.hide, "关闭时整个节点隐藏，不占命中测试")
    }

    @Test
    fun focusTrapOnlyWhenActive() {
        val env = buildDialog()
        assertTrue(env.dlg.focusTrap, "打开时圈定焦点")

        env.open = false
        assertFalse(env.dlg.focusTrap, "关闭时不圈定焦点")
    }
}
