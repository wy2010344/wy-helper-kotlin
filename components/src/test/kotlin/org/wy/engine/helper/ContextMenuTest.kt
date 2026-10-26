package org.wy.engine.helper

import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.Direction
import org.wy.engine.KeyCode
import org.wy.engine.LayoutNode
import org.wy.engine.LayoutSize
import org.wy.engine.Node
import org.wy.engine.PointerButton
import org.wy.engine.PointerEvent
import org.wy.engine.PointerType
import org.wy.engine.RectNode
import org.wy.engine.Renderer
import org.wy.engine.SkiaTestBase
import org.wy.engine.outerHeight
import org.wy.engine.outerWidth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 通用右键菜单测试。
 *
 * 双路径：
 * - 真实生命周期：右键 → [showContextMenu] → pop 物化进渲染树 → 关闭后移除，
 *   且右键只派发 ContextMenu（不派发 Down / Click）；
 * - 静态构建：点项触发动作并关闭、disabled 不响应、点外 / Esc 关闭、
 *   面板拦截冒泡、焦点圈定、贴右缘 clamp / 贴底自动翻转。
 */
class ContextMenuTest : SkiaTestBase() {

    // ── 真实生命周期（右键打开 → pop 物化 → 关闭移除）──

    /** headless 无布局几何：任意点可命中（同 PointerContextMenuDoubleClickTest）。 */
    private class RightClickTarget(context: StateHolder<Node, List<Node>>) : RectNode(context) {
        var downs = 0
        var clicks = 0
        var opens = 0

        override fun acceptHit(x: Float, y: Float): Boolean = true

        override fun onPointerDown(e: PointerEvent) {
            downs++
        }

        override fun onPointerClick(e: PointerEvent) {
            clicks++
        }

        override fun onPointerContextMenu(e: PointerEvent) {
            opens++
            showContextMenu(e, listOf(MenuItem("打开")))
        }
    }

    private class TargetEnv {
        lateinit var renderer: Renderer
        lateinit var target: RightClickTarget
    }

    private fun buildTarget(): TargetEnv {
        val env = TargetEnv()
        env.renderer = object : Renderer(null) {
            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                env.target = RightClickTarget(this)
            }
        }
        env.renderer.children
        return env
    }

    /** 在渲染树中递归查找已物化的菜单实例。 */
    private fun findMenu(n: Node): ContextMenuBase? {
        if (n is ContextMenuBase) return n
        n.children.forEach { c -> findMenu(c)?.let { return it } }
        return null
    }

    @Test
    fun rightClickOpensMenuWithoutDownOrClick() {
        val env = buildTarget()
        env.renderer.mouseDown(5f, 5f, button = PointerButton.RIGHT)
        env.renderer.mouseUp(5f, 5f, button = PointerButton.RIGHT)
        flushBatches()

        assertEquals(1, env.target.opens, "右键应触发目标节点的 showContextMenu")
        assertEquals(0, env.target.downs, "右键不应派发 Down（否则退化为假左键）")
        assertEquals(0, env.target.clicks, "右键松手不应派发 Click")
        assertNotNull(findMenu(env.renderer), "菜单应经 appendPop 物化进渲染树")
    }

    @Test
    fun dismissRemovesMenuFromTree() {
        val env = buildTarget()
        env.renderer.mouseDown(5f, 5f, button = PointerButton.RIGHT)
        flushBatches()
        val menu = assertNotNull(findMenu(env.renderer), "右键后菜单应在渲染树中")

        // 右键点浮层空白处（真实场景命中链 leaf 即浮层根 → 走本回调关闭）
        menu.onPointerContextMenu(PointerEvent(type = PointerType.ContextMenu, x = 1f, y = 1f))
        flushBatches()
        assertNull(findMenu(env.renderer), "关闭后菜单应从渲染树移除")
    }

    // ── 静态构建（面板 / 菜单项交互与几何）──

    private fun buildMenu(
        atX: Float = 200f,
        atY: Float = 200f,
        viewport: Boolean = false,
        divided: Boolean = false,
    ): TestEnv {
        val env = TestEnv()
        env.renderer = object : Renderer(null) {
            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                env.outside = object : Button(this@argChildren) {
                    override val label: String get() = "外部"
                }
                env.menu = object : ContextMenuBase(
                    this@argChildren, atX, atY,
                    listOf(
                        MenuItem("操作", onClick = { env.actionCount++ }),
                        MenuItem("禁用", onClick = { env.actionCount++ }, enabled = false, divided = divided),
                        MenuItem("关于"),
                    ),
                ) {
                    // 几何断言用显式视口（1000×800），翻转 / clamp 才能生效
                    override fun argSize(direction: Direction): LayoutSize =
                        if (viewport) {
                            LayoutSize(if (direction == Direction.x) 1000f else 800f, false)
                        } else {
                            super.argSize(direction)
                        }

                    override fun onDismiss() {
                        env.dismissCount++
                    }
                }
            }
        }
        env.renderer.children
        fun forceBuild(n: Node) {
            n.children.forEach { forceBuild(it) }
        }
        env.menu.children.forEach { forceBuild(it) }
        return env
    }

    class TestEnv {
        lateinit var renderer: Renderer
        lateinit var menu: ContextMenuBase
        lateinit var outside: ButtonBase
        var dismissCount = 0
        var actionCount = 0
    }

    @Test
    fun itemClickTriggersActionAndDismisses() {
        val env = buildMenu()
        val row = env.menu.children[0].children[0] as ButtonBase
        row.onPointerClick(PointerEvent(type = PointerType.Click, x = 0f, y = 0f))
        assertEquals(1, env.actionCount, "点菜单项应触发业务动作")
        assertEquals(1, env.dismissCount, "点菜单项后菜单应关闭（面板拦截冒泡，由项自身关闭）")
    }

    @Test
    fun disabledItemNeitherActionsNorDismisses() {
        val env = buildMenu()
        val disabled = env.menu.children[0].children[1] as ButtonBase
        assertFalse(disabled.enabled, "disabled 项不可用")
        disabled.onPointerClick(PointerEvent(type = PointerType.Click, x = 0f, y = 0f))
        assertEquals(0, env.actionCount, "disabled 不应触发动作")
        assertEquals(0, env.dismissCount, "disabled 点击不应关闭菜单")
    }

    @Test
    fun plainItemClickStillDismisses() {
        val env = buildMenu()
        val row = env.menu.children[0].children[2] as ButtonBase
        row.onPointerClick(PointerEvent(type = PointerType.Click, x = 0f, y = 0f))
        assertEquals(0, env.actionCount, "无动作项不触发业务")
        assertEquals(1, env.dismissCount, "无动作项点击也应关闭菜单")
    }

    @Test
    fun rightClickOnBlankCloses() {
        val env = buildMenu()
        env.menu.onPointerContextMenu(PointerEvent(type = PointerType.ContextMenu, x = 3f, y = 3f))
        assertEquals(1, env.dismissCount, "右键点浮层空白处应关闭（右键不派发 Click，须走本回调）")
    }

    @Test
    fun rightClickOnPanelIntercepts() {
        val env = buildMenu()
        val e = PointerEvent(type = PointerType.ContextMenu, x = 0f, y = 0f)
        env.menu.children[0].onPointerContextMenu(e)
        assertTrue(e.stoppedProgression, "右键点面板应拦截冒泡，防止触发关闭")
        assertEquals(0, env.dismissCount)
    }

    @Test
    fun leftClickOnBlankCloses() {
        val env = buildMenu()
        env.menu.onPointerClick(PointerEvent(type = PointerType.Click, x = 5f, y = 5f))
        assertEquals(1, env.dismissCount, "左键点浮层空白处关闭（OverlayBase 既有语义）")
    }

    @Test
    fun leftClickOnPanelDoesNotClose() {
        val env = buildMenu()
        val e = PointerEvent(type = PointerType.Click, x = 0f, y = 0f)
        env.menu.children[0].onPointerClick(e)
        assertTrue(e.stoppedProgression, "面板点击拦截冒泡")
        assertEquals(0, env.dismissCount)
    }

    @Test
    fun escapeClosesWhenFocusInside() {
        val env = buildMenu()
        val row = env.menu.children[0].children[0] as ButtonBase
        env.renderer.engineGlobal.focused = row
        env.renderer.keyPress('\u001b', KeyCode.Escape, false, false, false)
        assertEquals(1, env.dismissCount, "焦点在菜单内时 Esc 应关闭")
    }

    @Test
    fun trapsFocusWhileOpen() {
        val env = buildMenu()
        assertTrue(env.menu.focusTrap, "菜单打开时圈定焦点（Tab 只在菜单内循环）")
    }

    @Test
    fun openingFocusesFirstEnabledItem() {
        val env = buildMenu()
        val first = env.menu.children[0].children[0] as ButtonBase
        env.renderer.engineGlobal.focused = env.outside
        env.menu.syncFocusNow()
        assertEquals(first, env.renderer.engineGlobal.focused, "打开时自动聚焦首个菜单项")
    }

    @Test
    fun panelClampsAtRightEdge() {
        val env = buildMenu(atX = 980f, atY = 200f, viewport = true)
        val panel = env.menu.children[0] as LayoutNode
        assertEquals(
            1000f - panel.outerWidth, panel.x,
            "取点贴右缘放不下 → clamp 到视口内（菜单不越出右边界）"
        )
    }

    @Test
    fun flipsAboveNearViewportBottom() {
        val env = buildMenu(atX = 200f, atY = 790f, viewport = true)
        val panel = env.menu.children[0] as LayoutNode
        val ph = panel.outerHeight
        assertEquals(790f - ph - 4f, panel.y, "贴底放不下 → 翻到取点上方")
        assertTrue(panel.y >= 0f && panel.y + ph <= 800f, "菜单完整落在视口内")
    }

    @Test
    fun anchoredAtClickPoint() {
        val env = buildMenu(atX = 200f, atY = 300f, viewport = true)
        val panel = env.menu.children[0]
        assertEquals(200f, panel.x, "菜单左缘对齐取点")
        assertEquals(300f + 4f, panel.y, "菜单顶缘在取点下方 gap 处")
    }

    @Test
    fun dividedItemAddsSeparatorAbove() {
        val env = buildMenu(divided = true)
        val panel = env.menu.children[0]
        assertEquals(4, panel.children.size, "3 个菜单项 + 1 条分隔线")
        assertFalse(panel.children[1] is ButtonBase, "分隔线是普通矩形节点，不是菜单项")
    }
}
