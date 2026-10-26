package org.wy.engine.helper

import com.wy.mve.StateHolderWithNode
import org.wy.engine.KeyCode
import org.wy.engine.Node
import org.wy.engine.PointerEvent
import org.wy.engine.PointerType
import org.wy.engine.RectF
import org.wy.engine.Renderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 词典浮层测试：Esc / 点击外部关闭、面板点击不关闭、锚点区域下方定位、
 * 默认面板宽度、hide 跟随打开状态。
 */
class PopoverTest {

    private val anchorRect = RectF(100f, 200f, 180f, 230f)

    /** 构建一个 Renderer + 词典浮层（含一个可聚焦项）。 */
    private fun buildPopover(open: Boolean = true): TestEnv {
        val env = TestEnv()
        env.open = open
        env.renderer = object : Renderer(null) {
            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                env.popover = object : PopoverBase(this@argChildren, anchorRect) {
                    override val enabled: Boolean get() = env.open
                    override fun onDismiss() { env.dismissCount++ }
                    override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
                        env.item = object : Button(this) {
                            override val label: String get() = "项 A"
                        }
                    }
                }
            }
        }
        env.renderer.children
        fun forceBuild(n: Node) {
            n.children.forEach { forceBuild(it) }
        }
        env.popover.children.forEach { forceBuild(it) }
        return env
    }

    class TestEnv {
        lateinit var renderer: Renderer
        lateinit var popover: PopoverBase
        lateinit var item: ButtonBase
        var open = true
        var dismissCount = 0
    }

    @Test
    fun escapeClosesWhenFocusInside() {
        val env = buildPopover()
        env.renderer.engineGlobal.focused = env.item

        env.renderer.keyPress('\u001b', KeyCode.Escape, false, false, false)
        assertEquals(1, env.dismissCount, "焦点在面板内时 Esc 应关闭")
    }

    @Test
    fun outsideClickCloses() {
        val env = buildPopover()
        env.popover.onPointerClick(PointerEvent(type = PointerType.Click, x = 5f, y = 5f))
        assertEquals(1, env.dismissCount, "点击浮层空白处应关闭")
    }

    @Test
    fun panelClickDoesNotClose() {
        val env = buildPopover()
        val panel = env.popover.children[0]
        val e = PointerEvent(type = PointerType.Click, x = 0f, y = 0f)
        panel.onPointerClick(e)
        assertTrue(e.stoppedProgression, "面板点击应拦截冒泡，防止触发关闭")
        assertEquals(0, env.dismissCount)
    }

    @Test
    fun panelPositionedBelowAnchorRect() {
        val env = buildPopover()
        val panel = env.popover.children[0]
        assertEquals(anchorRect.left, panel.x, "面板 x 对齐锚点区域左缘")
        assertEquals(
            anchorRect.bottom + env.popover.offsetY, panel.y,
            "面板 y 在锚点区域底部加间距"
        )
    }

    @Test
    fun defaultPanelWidth() {
        val env = buildPopover()
        assertEquals(280f, env.popover.panelWidth, "默认面板宽度")
    }

    @Test
    fun hideFollowsOpenState() {
        val env = buildPopover()
        assertFalse(env.popover.hide, "打开时可见")

        env.open = false
        assertTrue(env.popover.hide, "关闭时整个节点隐藏")
    }
}
