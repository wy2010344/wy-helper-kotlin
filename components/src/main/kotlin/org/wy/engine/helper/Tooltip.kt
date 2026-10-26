package org.wy.engine.helper

import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.ColorInt
import org.wy.engine.Direction
import org.wy.engine.LayoutNode
import org.wy.engine.Node
import org.wy.engine.PlatformCanvas
import org.wy.engine.PointerDevice
import org.wy.engine.Pop
import org.wy.engine.RectNode
import org.wy.engine.StartEnd
import org.wy.engine.WrappedTextNode
import org.wy.engine.absoluteX
import org.wy.engine.absoluteY
import org.wy.engine.fillOuterRoundRect
import org.wy.engine.include
import org.wy.engine.outerHeight
import org.wy.engine.outerWidth
import org.wy.engine.postDelayed
import org.wy.engine.strokeOuterRoundRect
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.lib.EmptyFun
import org.wy.signal.TrackSignal
import org.wy.signal.addEffect

/**
 * tooltip 提示浮层基类：经 Pop 全局挂载，面板锚定 [anchor] 节点，
 * 经 [resolvePlacement] 定位（默认锚点下方，空间不足自动翻转 + 贴边 clamp）。
 *
 * 整层（浮层根 + 面板）[acceptHit] = false：浮层铺满窗口，若拦截命中会吞掉
 * moveHitTest 导致 hover 进出抖动（ToastBase 同款，回归见 TooltipTest）。
 * 不圈定焦点、不响应点击，纯展示；锚隐藏 / 销毁时浮层隐藏（同 DropdownBase）。
 *
 * 显示 / 隐藏由 [attachTooltip] 的 hover 防抖状态机驱动（进入延时挂载、移出立即卸载），
 * 面板样式读 [Theme.current]（信号化，自动昼 / 夜切换）。
 */
open class TooltipBase(
    context: StateHolder<Node, List<Node>>,
    protected val anchor: LayoutNode,
    protected val text: () -> String,
) : OverlayBase(context) {

    /** 面板相对锚点的放置方向（默认锚点下方；空间不足自动回退见 [resolvePlacement]）。 */
    open val placement: Placement get() = Placement.BottomStart

    /** 面板与锚点的间距（避开 FlexParam.gap 成员名）。 */
    open val offsetY: Float get() = 6f

    /** 整层不拦截命中：命中穿透到下方内容，hover 稳定不抖动。 */
    override fun acceptHit(x: Float, y: Float): Boolean = false

    /** 锚离树（隐藏 / 销毁）时浮层隐藏：布局不再对锚做过期几何换算（同 DropdownBase）。 */
    override val hide: Boolean
        get() = super.hide || anchor.destroyed || anchor.hide

    /** 提示面板：位置锚定锚点矩形（浮层根内相对坐标换算），整层命中穿透。 */
    protected open fun StateHolderWithNode<Node, List<Node>>.panel() {
        object : RectNode(this), FlexParam {
            override val layout: LayoutDirection = FlexObject(this)
            override val direction: Direction get() = Direction.y
            // 高度包裹文本；宽度不覆写 → 交叉轴 alignFix=false，量取子节点内容宽
            override val directionJustify: DirectionJustify get() = DirectionJustify.grow

            override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                if (direction == Direction.x) 8f else 5f

            // 命中穿透：划过 / 点击面板区域不拦截，指针事件落到下方内容
            override fun acceptHit(x: Float, y: Float): Boolean = false

            // 锚点 / 自身 / 视口统一换算为浮层根内相对坐标（浮层根即视口），
            // 8 方向放置 + 空间不足自动回退 + 贴边（见 resolvePlacement）
            override fun argPosition(direction: Direction): Float {
                val a = anchor
                val overlay = this@TooltipBase
                val (px, py) = resolvePlacement(
                    overlay.placement,
                    a.absoluteX - overlay.absoluteX,
                    a.absoluteY - overlay.absoluteY,
                    a.outerWidth, a.outerHeight,
                    outerWidth, outerHeight,
                    overlay.outerWidth, overlay.outerHeight,
                    overlay.offsetY,
                )
                return when (direction) {
                    Direction.x -> px
                    Direction.y -> py
                }
            }

            override fun draw(canvas: PlatformCanvas) {
                val c = Theme.current.colors
                val r = Theme.current.radius.card
                fillOuterRoundRect(canvas, r, c.surface)
                strokeOuterRoundRect(canvas, r, c.border, 1f)
                super.draw(canvas)
            }

            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                // 内联声明而不用 text() 工厂：外层成员 text 是属性，
                // 同名工厂调用会被遮蔽解析成属性 invoke
                object : WrappedTextNode(this) {
                    override val autoWidth: Boolean get() = true
                    override val text: String get() = this@TooltipBase.text()
                    override val fontSize: Float get() = Theme.current.textSize.label
                    override val color: ColorInt get() = Theme.current.colors.text
                    override val fontWeight: Int get() = 400
                }
            }
        }
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        panel()
    }
}

/**
 * 给 [this]（锚点）挂通用 tooltip：悬停 [delayMs]（默认 400ms）后显示提示，移出立即隐藏。
 *
 * - 防抖：进入锚点经 [scheduler] 延时挂载（存取消句柄）；移出先取消未到期句柄，
 *   已挂载则经 [EngineGlobal.removePop] 卸载——卸载经 [addEffect] 推迟到 effect
 *   阶段执行（TrackSignal 回调处于批次执行中，直接改 popList 会触发批次期写入保护）；
 * - 命中穿透：浮层根 / 面板均不拦截命中，moveHitTest 保持指向锚点，hover 不抖动；
 * - 定位复用 [resolvePlacement]（8 方向 + 空间不足自动翻转 + 贴边 clamp）。
 *
 * 调用方在容器 argChildren 中：
 * ```
 * gButton("保存").attachTooltip { "保存草稿（Ctrl+S）" }
 * ```
 *
 * @param placement 放置方向（默认锚点下方，空间不足自动回退）
 * @param delayMs 悬停到显示的延时毫秒，默认 400
 * @param scheduler 延时调度器（测试注入假定时器）
 * @param text 提示内容（惰性读取，可随信号变化；置尾支持尾随 lambda）
 * @return 锚点自身（可链式调用）
 */
fun <T : LayoutNode> T.attachTooltip(
    placement: Placement = Placement.BottomStart,
    delayMs: Long = 400L,
    scheduler: (delayMs: Long, action: () -> Unit) -> EmptyFun = ::postDelayed,
    text: () -> String,
): T {
    val anchor = this
    val g = engineGlobal
    // 先捕获进局部：匿名子类成员 placement 会遮蔽同名参数（同 dropdown 工厂）
    val place = placement
    var tipPop: Pop? = null

    val track = object : TrackSignal<Boolean>() {
        override fun get(old: Boolean?, inited: Boolean): Boolean {
            val h = g.moveHitTest ?: return false
            // 触摸没有 hover 语义（同 ButtonBase.hovered）
            return h.device != PointerDevice.Touch && h.include(anchor)
        }

        override fun set(v: Boolean, oldV: Boolean?, inited: Boolean): EmptyFun? {
            if (!v) return null
            // 进入：延时挂载；返回的销毁句柄在移出（值变化）或 dispose 时执行
            val cancel = scheduler(delayMs) {
                tipPop = g.appendPop { pop ->
                    object : TooltipBase(this, anchor, text) {
                        override val placement: Placement get() = place
                        override fun onDismiss() {
                            g.removePop(pop)
                        }
                    }
                }
            }
            return {
                cancel()
                val p = tipPop
                tipPop = null
                // 本回调处于批次执行中：popList 是信号，直接改会触发批次期写入保护，
                // 推迟到 effect 阶段（onWorkBatch 已清空）再卸载
                if (p != null) addEffect { g.removePop(p) }
            }
        }
    }
    // 锚点 / 树销毁时兜底：取消未到期句柄并卸载已挂载浮层
    addDestroy { track.dispose() }
    return this
}
