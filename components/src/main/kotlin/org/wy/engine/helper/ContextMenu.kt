package org.wy.engine.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.Direction
import org.wy.engine.LayoutSize
import org.wy.engine.Node
import org.wy.engine.PlatformCanvas
import org.wy.engine.PointerEvent
import org.wy.engine.RectNode
import org.wy.engine.StartEnd
import org.wy.engine.absoluteX
import org.wy.engine.absoluteY
import org.wy.engine.fillOuterRoundRect
import org.wy.engine.outerHeight
import org.wy.engine.outerWidth
import org.wy.engine.strokeOuterRing
import org.wy.engine.strokeOuterRoundRect
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection

/**
 * 右键菜单项数据。
 *
 * @param label 菜单项文字
 * @param onClick 业务动作（点击项触发后菜单自动关闭；null 表示纯展示项）
 * @param enabled false 时不可点、不聚焦，文字置灰
 * @param danger 危险操作项（文字用 [ThemeColors.danger] 警示色，如删除）
 * @param divided 该项上方画分隔线（首项不画）
 * @param shortcut 快捷键提示（居右显示）
 */
data class MenuItem(
    val label: String,
    val onClick: (() -> Unit)? = null,
    val enabled: Boolean = true,
    val danger: Boolean = false,
    val divided: Boolean = false,
    val shortcut: String? = null,
)

/**
 * 右键菜单浮层基类：锚为取点矩形 (atX, atY, 0, 0)，经 [resolvePlacement] 定位
 * （默认取点下方，空间不足自动翻转 + 贴边 clamp）。
 *
 * 交互（右键只派发 ContextMenu、不派发 Down / Click）：
 * - 左键点菜单项 → 触发 [MenuItem.onClick] 并关闭（面板拦截冒泡，由项自身关闭）；
 * - 左键 / 右键点浮层空白处 → 关闭（左键走 [OverlayBase.onPointerClick]，
 *   右键须走本类 [onPointerContextMenu] 覆写）；
 * - 左键 / 右键点面板 → 拦截冒泡，保持打开；Esc 关闭。
 *
 * [trapsFocus] = true：打开期间 Tab 只在菜单项间循环并聚焦首项，关闭还原焦点。
 * 样式全部读 [Theme.current]（信号化，自动昼 / 夜切换）。
 */
open class ContextMenuBase(
    context: StateHolder<Node, List<Node>>,
    protected val atX: Float,
    protected val atY: Float,
    protected val items: List<MenuItem>,
) : OverlayBase(context) {

    /** 面板宽度（高度由菜单项撑起）。 */
    open val panelWidth: Float get() = 180f

    /** 菜单与取点在主轴上的间距（避开 FlexParam.gap 成员名）。 */
    open val offsetY: Float get() = 4f

    /** 菜单相对取点的放置方向（默认取点下方；空间不足自动回退见 [resolvePlacement]）。 */
    open val placement: Placement get() = Placement.BottomStart

    /** 右键菜单模态：Tab / Shift+Tab 只在菜单项间循环。 */
    override val trapsFocus: Boolean get() = true

    /**
     * 右键点浮层空白处关闭。
     *
     * 右键不派发 Click，只靠 [OverlayBase.onPointerClick] 无法响应右键；
     * 点面板 / 菜单项时冒泡在子节点上被拦截，不会走到这里。
     */
    override fun onPointerContextMenu(e: PointerEvent) {
        if (enabled) onDismiss()
    }

    /** 菜单面板：位置锚定取点矩形，点击 / 右键均不冒泡到浮层空白处。 */
    protected open fun StateHolderWithNode<Node, List<Node>>.panel() {
        object : RectNode(this), FlexParam {
            override val layout: LayoutDirection = FlexObject(this)
            override val direction: Direction get() = Direction.y
            override val directionJustify: DirectionJustify get() = DirectionJustify.grow
            override val alignItem: AlignItem get() = AlignItem.stretch
            override val alignFix: Boolean get() = true
            override val gap: Float get() = 1f
            override val argWidth: LayoutSize
                get() = LayoutSize(this@ContextMenuBase.panelWidth, false)

            override fun argPadding(direction: Direction, startEnd: StartEnd): Float = 6f

            // 取点为零尺寸锚矩形（浮层根内相对坐标），复用 8 方向放置 + 自动回退 + 贴边
            override fun argPosition(direction: Direction): Float {
                val overlay = this@ContextMenuBase
                val (px, py) = resolvePlacement(
                    overlay.placement,
                    atX - overlay.absoluteX,
                    atY - overlay.absoluteY,
                    0f, 0f,
                    outerWidth, outerHeight,
                    overlay.outerWidth, overlay.outerHeight,
                    overlay.offsetY,
                )
                return when (direction) {
                    Direction.x -> px
                    Direction.y -> py
                }
            }

            // 拦截冒泡：点击 / 右键面板（含项间空白）不触发浮层空白处的关闭
            override fun onPointerClick(e: PointerEvent) {
                e.stopPropagation()
            }

            override fun onPointerContextMenu(e: PointerEvent) {
                e.stopPropagation()
            }

            override fun draw(canvas: PlatformCanvas) {
                val c = Theme.current.colors
                val r = Theme.current.radius.card
                fillOuterRoundRect(canvas, r, c.surface)
                strokeOuterRoundRect(canvas, r, c.border, 1f)
                super.draw(canvas)
            }

            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                items.forEachIndexed { index, item ->
                    if (item.divided && index > 0) {
                        // 分隔线：高度固定 1px，宽度经 stretch 通道拉伸到面板内宽（首项上方不画）
                        object : RectNode(this) {
                            override val argHeight: LayoutSize get() = LayoutSize(1f, false)

                            override fun draw(canvas: PlatformCanvas) {
                                canvas.fillRect(
                                    0f, 0f, outerWidth, outerHeight,
                                    Theme.current.colors.border
                                )
                            }
                        }
                    }
                    menuRow(item)
                }
            }
        }
    }

    /** 单个菜单项行：交互内置（[ButtonBase]），样式读 [Theme.current]。 */
    private fun StateHolderWithNode<Node, List<Node>>.menuRow(item: MenuItem) {
        object : ButtonBase(this) {
            override val enabled: Boolean get() = item.enabled
            override val focusOrder: Int? get() = null
            // 标签居左、快捷键居右；宽度不覆写 → 经 stretch 拉伸到面板内宽
            override val directionJustify: DirectionJustify get() = DirectionJustify.between
            override val argHeight: LayoutSize get() = LayoutSize(30f, false)

            override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                if (direction == Direction.x) 10f else 0f

            override fun onClick() {
                item.onClick?.invoke()
                // 面板拦截了冒泡（overlay 的空白点击关闭不会触发），由项自身关闭菜单
                onDismiss()
            }

            override fun drawContent(canvas: PlatformCanvas) {
                if (hovered) {
                    fillOuterRoundRect(canvas, 6f, Theme.current.colors.surfaceHover)
                }
            }

            override fun drawFocusRing(canvas: PlatformCanvas) {
                strokeOuterRing(canvas, 2f, 8f, Theme.current.colors.focus, 2f)
            }

            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                // 主题色在 lambda 内活读，不在 argChildren 里捕获 Theme 快照
                text({ item.label }, Theme.current.textSize.label, {
                    when {
                        !enabled -> Theme.current.colors.textDisabled
                        item.danger -> Theme.current.colors.danger
                        else -> Theme.current.colors.text
                    }
                }, 400)
                item.shortcut?.let { s ->
                    text({ s }, Theme.current.textSize.caption, { Theme.current.colors.textSecondary }, 400)
                }
            }
        }
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        panel()
    }
}

/**
 * 在 [e] 取点处弹出右键菜单（经 Pop 全局挂载，始终在主界面之上）。
 *
 * 调用方在目标节点：
 * ```
 * override fun onPointerContextMenu(e: PointerEvent) {
 *     showContextMenu(e, listOf(MenuItem("删除", onClick = { ... }, danger = true)))
 * }
 * ```
 * 菜单关闭（点外 / Esc / 点项）时经 [EngineGlobal.removePop] 自动卸载。
 * 本方法内部已 [PointerEvent.stopPropagation]，右键事件不会继续冒泡到祖先。
 */
fun Node.showContextMenu(e: PointerEvent, items: List<MenuItem>) {
    e.stopPropagation()
    val atX = e.rootX
    val atY = e.rootY
    val g = engineGlobal
    g.appendPop { pop ->
        object : ContextMenuBase(this, atX, atY, items) {
            override fun onDismiss() {
                g.removePop(pop)
            }
        }
    }
}
