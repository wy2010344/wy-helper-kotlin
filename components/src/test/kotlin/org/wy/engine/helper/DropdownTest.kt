package org.wy.engine.helper

import com.wy.mve.StateHolderWithNode
import org.wy.engine.Direction
import org.wy.engine.KeyCode
import org.wy.engine.LayoutNode
import org.wy.engine.LayoutSize
import org.wy.engine.Node
import org.wy.engine.PointerEvent
import org.wy.engine.PointerType
import org.wy.engine.RectNode
import org.wy.engine.Renderer
import org.wy.engine.outerHeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 下拉浮层测试：Esc / 点击外部关闭、面板点击不关闭、打开聚焦第一项、
 * 关闭还原焦点、锚点下方定位、不圈定焦点、hide 跟随打开状态、
 * 8 方向放置与空间不足自动回退（集成）。
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

    /**
     * 构建带显式视口（1000×800）与固定几何锚点的下拉，用于 8 方向放置集成断言：
     * 锚点 80×30 固定在 (100, [anchorY])；面板内容为 100 高定高块（面板高度可预期）。
     */
    private fun buildPlacedDropdown(placement: Placement, anchorY: Float): TestEnv {
        val env = TestEnv()
        env.open = true
        val place = placement   // 捕获参数，避免对象成员遮蔽
        env.renderer = object : Renderer(null) {
            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                env.anchor = object : RectNode(this@argChildren) {
                    override fun argSize(direction: Direction): LayoutSize =
                        LayoutSize(if (direction == Direction.x) 80f else 30f, false)

                    override fun argPosition(direction: Direction): Float =
                        if (direction == Direction.x) 100f else anchorY
                }
                env.dd = object : DropdownBase(this@argChildren, env.anchor) {
                    override val placement: Placement get() = place
                    // 浮层根即视口：显式给定尺寸，翻转 / clamp 生效
                    override fun argSize(direction: Direction): LayoutSize =
                        LayoutSize(if (direction == Direction.x) 1000f else 800f, false)

                    override fun onDismiss() { env.dismissCount++ }
                    override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
                        object : RectNode(this) {
                            override fun argSize(direction: Direction): LayoutSize =
                                LayoutSize(100f, false)
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
        lateinit var anchor: LayoutNode
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

    @Test
    fun bottomPlacementFlipsUpWhenNearViewportBottom() {
        // 锚点 y=700、底边 730，下方放不下（+gap+面板高超出 800）→ 自动翻到上方
        val env = buildPlacedDropdown(Placement.BottomStart, anchorY = 700f)
        val panel = env.dd.children[0] as LayoutNode
        val ph = panel.outerHeight
        assertEquals(100f, panel.x, "x 对齐锚点起始边")
        assertEquals(700f - ph - 4f, panel.y, "贴底放不下 → 翻到锚点上方")
        assertTrue(panel.y >= 0f && panel.y + ph <= 800f, "面板完整落在视口内")
    }

    @Test
    fun topPlacementFlipsDownWhenNearViewportTop() {
        // 锚点 y=50，上方放不下 → 自动翻到下方
        val env = buildPlacedDropdown(Placement.TopStart, anchorY = 50f)
        val panel = env.dd.children[0] as LayoutNode
        assertEquals(100f, panel.x, "x 对齐锚点起始边")
        assertEquals(50f + 30f + 4f, panel.y, "贴顶放不下 → 翻到锚点下方")
        assertTrue(panel.y >= 0f, "面板不越出视口顶")
    }
}
