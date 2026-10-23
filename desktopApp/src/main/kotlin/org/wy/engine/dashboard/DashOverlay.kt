package org.wy.engine.dashboard

import com.wy.layout.AlignItem
import org.wy.engine.*
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.animation.DefaultFrameSource
import org.wy.engine.animation.FrameSubscription
import org.wy.engine.helper.ButtonBase
import org.wy.engine.helper.DialogBase
import org.wy.engine.helper.DropdownBase
import org.wy.engine.helper.OverlayBase
import org.wy.engine.helper.ScrollBarStyle
import org.wy.engine.helper.SimpleScrollNode
import org.wy.engine.helper.dialog
import org.wy.engine.helper.drag
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.GrowChild
import org.wy.engine.layout.LayoutDirection
import org.wy.lib.StoreRef
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.memo
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sign
import kotlin.math.sqrt

/** 深色模态对话框（DEMO 免责声明）。 */
internal fun StateHolder<Node, List<Node>>.dNoticeDialog(onClose: () -> Unit) {
    object : DialogBase(this) {
        override fun onDismiss() = onClose()
        override val panelWidth: Float get() = 440f

        override fun StateHolderWithNode<Node, List<Node>>.panel() {
            val pw = panelWidth
            object : RectNode(this), FlexParam {
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.y
                override val directionJustify: DirectionJustify get() = DirectionJustify.grow
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val alignFix: Boolean get() = true
                override val gap: Float get() = 10f
                override val argWidth: LayoutSize
                    get() = LayoutSize(pw, false)

                override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                    if (direction == Direction.x) 20f else 18f

                override fun onPointerClick(e: PointerEvent) {
                    e.stopPropagation()
                }

                override fun draw(canvas: PlatformCanvas) {
                    fillOuterRoundRect(canvas, 10f, D_PANE)
                    strokeOuterRoundRect(canvas, 10f, D_LINE, 1f)
                    canvas.drawShadow(4f, 4f, outerWidth - 8f, outerHeight - 8f, 10f, 24f, rgba(0, 0, 0, 140))
                    super.draw(canvas)
                }

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    contentChildren()
                }
            }
        }

        override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
            dRow(gap = 8f, height = 20f) {
                dText({ "演示环境 · 免责声明" }, 14f, { D_TEXT }, 700)
                dSpacer()
                dTabButton({ "DEMO" }, { false }, height = 18f, padX = 6f, chip = D_AMBER) { }
            }
            dText(
                {
                    "本页为行情终端界面复刻演示：全部行情与盘口均为本地确定性模拟数据，" +
                        "不连接任何交易所接口，不构成任何投资建议。\n" +
                        "下单窗格（Paper order）只做界面演示，任何「提交」都不会产生真实委托。"
                },
                11.5f, { D_TEXT2 }, wrap = true
            )
            dRow(gap = 8f, height = 30f, justify = DirectionJustify.end) {
                dTabButton({ "我知道了" }, { false }, height = 28f, padX = 14f, chip = D_ACCENT) {
                    onClose()
                }
            }
        }
    }
}

/** 深度图全屏浮层（FULL）：遮罩 + 居中大图画布 + CLOSE。 */
internal fun StateHolder<Node, List<Node>>.dDepthFull(
    depth: () -> Depth?,
    zoom: () -> Double,
    onWheelZoom: (Float) -> Unit,
    onClose: () -> Unit
) {
    object : OverlayBase(this) {
        override val trapsFocus: Boolean get() = true
        override fun onDismiss() = onClose()

        override fun drawScrim(canvas: PlatformCanvas) {
            fillOuterRect(canvas, rgba(0, 0, 0, 150))
        }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            object : RectNode(this), FlexParam {
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.y
                override val directionJustify: DirectionJustify get() = DirectionJustify.start
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val alignFix: Boolean get() = true
                override val gap: Float get() = 0f

                // 尺寸随窗口收放（OverlayBase 已铺满窗口，读它的内尺寸安全：
                // 根节点尺寸是定值，不参与本面板的测量）
                override val argWidth: LayoutSize
                    get() = LayoutSize(min(780f, (layoutParent?.innerWidth ?: 780f) - 48f), false)
                override val argHeight: LayoutSize
                    get() = LayoutSize(min(480f, (layoutParent?.innerHeight ?: 480f) - 48f), false)

                override fun onPointerClick(e: PointerEvent) {
                    e.stopPropagation()
                }

                override fun draw(canvas: PlatformCanvas) {
                    fillOuterRoundRect(canvas, 8f, D_BG)
                    strokeOuterRoundRect(canvas, 8f, D_LINE, 1f)
                    super.draw(canvas)
                }

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    dRow(gap = 8f, height = 30f, padX = 12f, justify = DirectionJustify.between) {
                        dText({ "Depth · Cumulative" }, 11f, { D_TEXT2 }, 600)
                        dTabButton({ "CLOSE" }, { false }, height = 20f, padX = 8f) { onClose() }
                    }
                    depthCanvas(depth, zoom, onWheelZoom)
                }
            }
        }
    }
}
