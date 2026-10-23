package org.wy.engine.dashboard

import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.lib.StoreRef
import kotlin.math.exp

// ════════════════════════════════════════════════════
// 看板中列底部「Market activity」窗格（自 DashPage 拆出）
//
// Depth / Trades 两页 + 工具 tab；行情心跳（marketRev）与
// 深度缩放（depthZoom）作为页面级信号注入，页内不建信号。
// ════════════════════════════════════════════════════

/**
 * 行情活动窗格。两页各用一个 renderForEach（同 DashBook avgSum 模式）：
 * 结构 if 若直接写在 argChildren 里是启动快照，点 Trades 切不过去；
 * key 增删即建/毁子树，activity 翻转即换页。
 */
internal fun StateHolderWithNode<Node, List<Node>>.dDashActivity(
    centerBottom: StoreRef<Float>,
    activity: StoreRef<String>,
    symbol: StoreRef<String>,
    marketRev: StoreRef<Int>,
    depthZoom: StoreRef<Double>
) {
    val g = consume(engineGlobalContext)!!
    dPane(
        "Market activity",
        "Trades / Depth",
        heightOf = { centerBottom.value },
        tools = {
            dTabButton({ "Depth" }, { activity.value == "Depth" }, height = 18f, padX = 7f) {
                activity.value = "Depth"
            }
            dTabButton({ "Trades" }, { activity.value == "Trades" }, height = 18f, padX = 7f) {
                activity.value = "Trades"
            }
        }
    ) {
        renderForEach({ callback ->
            if (activity.value == "Depth") callback(0, Unit)
        }) { _, _ ->
            dCol(gap = 0f, growY = true) {
                // 图例 + 缩放控件行
                dRow(gap = 8f, height = 20f, padX = 8f, justify = DirectionJustify.between) {
                    // 图例数值走 lambda：val geo 是启动快照，心跳/zoom 后不更新
                    val geoOf = { DepthGeom.geo(MarketData.depthOf(symbol.value, marketRev.value), depthZoom.value) }
                    dRow(gap = 5f, height = 14f) {
                        object : RectNode(this) {
                            override val argWidth: LayoutSize get() = LayoutSize(10f, false)
                            override val argHeight: LayoutSize get() = LayoutSize(6f, false)

                            override fun draw(canvas: PlatformCanvas) {
                                fillOuterRoundRect(canvas, 1.5f, D_UP.withAlpha(0.7f))
                            }
                        }
                        dText({ "BID" }, 8.5f, { D_UP }, 600)
                        dText({ fmtQty(geoOf().bidMax) }, 8.5f, { D_TEXT3 })
                        object : RectNode(this) {
                            override val argWidth: LayoutSize get() = LayoutSize(10f, false)
                            override val argHeight: LayoutSize get() = LayoutSize(6f, false)

                            override fun draw(canvas: PlatformCanvas) {
                                fillOuterRoundRect(canvas, 1.5f, D_DOWN.withAlpha(0.7f))
                            }
                        }
                        dText({ "ASK" }, 8.5f, { D_DOWN }, 600)
                        dText({ fmtQty(geoOf().askMax) }, 8.5f, { D_TEXT3 })
                        dText({ "价差幅度" }, 8.5f, { D_TEXT3 })
                        dText(
                            {
                                String.format(
                                    java.util.Locale.US, "%.2f",
                                    DepthGeom.RANGE_PCT / DepthGeom.zoomClamp(depthZoom.value)
                                ) + "%"
                            },
                            8.5f, { D_TEXT2 }
                        )
                    }
                    dRow(gap = 4f, height = 16f) {
                        dIconButton(size = 16f, onClick = {
                            depthZoom.value = DepthGeom.zoomClamp(depthZoom.value * 0.8)
                        }) { canvas, w, h ->
                            drawTextCentered(canvas, "−", w / 2f, h / 2f, 10f, D_TEXT2, 600)
                        }
                        dText(
                            { "${String.format(java.util.Locale.US, "%.1f", DepthGeom.zoomClamp(depthZoom.value))}×" },
                            8.5f, { D_TEXT3 }, width = 26f, align = TextAlign.CENTER
                        )
                        dIconButton(size = 16f, onClick = {
                            depthZoom.value = DepthGeom.zoomClamp(depthZoom.value * 1.25)
                        }) { canvas, w, h ->
                            drawTextCentered(canvas, "+", w / 2f, h / 2f, 10f, D_TEXT2, 600)
                        }
                        dTabButton({ "RESET" }, { false }, height = 16f, padX = 5f) {
                            depthZoom.value = 1.0
                        }
                        dTabButton({ "FULL" }, { false }, height = 16f, padX = 5f) {
                            g.appendPop { pop ->
                                dDepthFull(
                                    depth = { MarketData.depthOf(symbol.value, marketRev.value) },
                                    zoom = { depthZoom.value },
                                    onWheelZoom = { delta ->
                                        depthZoom.value = DepthGeom.zoomClamp(
                                            depthZoom.value * exp(-delta * 0.0015)
                                        )
                                    },
                                    onClose = { g.removePop(pop) }
                                )
                            }
                        }
                    }
                }
                depthCanvas(
                    depth = { MarketData.depthOf(symbol.value, marketRev.value) },
                    zoom = { depthZoom.value },
                    onWheelZoom = { delta ->
                        depthZoom.value = DepthGeom.zoomClamp(
                            depthZoom.value * exp(-delta * 0.0015)
                        )
                    }
                )
            }
        }
        renderForEach({ callback ->
            if (activity.value == "Trades") callback(0, Unit)
        }) { _, _ ->
            dTrades { MarketData.tradesOf(symbol.value) }
        }
    }
}
