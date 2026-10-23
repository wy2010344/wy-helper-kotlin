package org.wy.engine.dashboard

import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.lib.StoreRef

// ════════════════════════════════════════════════════
// 看板顶栏（自 DashPage 拆出）
//
// 结构（对齐 finance-dashboard.ooc header）：
//   logo 瓦片 → 品牌区（超窄隐藏）→ 交易对 + 最新价 + 涨跌 →
//   交易对 tabs（超窄留前两个）→ 周期 tabs（窄窗隐藏）→
//   DEMO 徽章 → 状态点 → MOCK（窄窗隐藏）。
//
// 契约：窄化断点全部走 lambda 通道 + renderForEach 现读 windowWidth——
// argChildren 只执行一次，启动快照会让窗口缩放不再增减子块。
// ════════════════════════════════════════════════════

/**
 * 顶栏行。[windowWidth] 由各 renderForEach 在 memo 内现读登记依赖，
 * 断点翻转时 key 增删即建/毁对应子块。
 */
internal fun StateHolderWithNode<Node, List<Node>>.dDashHeader(
    windowWidth: () -> Float,
    symbol: StoreRef<String>,
    interval: StoreRef<String>,
    limitPrice: StoreRef<Double?>,
    note: StoreRef<String>,
    chartCount: StoreRef<Int>,
    chartOffset: StoreRef<Int>
) {
    val g = consume(engineGlobalContext)!!
    val narrow = { windowWidth() < 1000f }
    val veryNarrow = { windowWidth() < 880f }

    dRow(gap = 10f, height = DASH_HEADER_H, padX = 12f) {
        // logo 瓦片
        object : RectNode(this) {
            override val argWidth: LayoutSize get() = LayoutSize(26f, false)
            override val argHeight: LayoutSize get() = LayoutSize(26f, false)

            override fun draw(canvas: PlatformCanvas) {
                fillOuterRoundRect(canvas, 6f, D_ACCENT)
                drawTextCentered(canvas, "W", 13f, 13f, 11f, rgba(255, 255, 255), 700)
                super.draw(canvas)
            }
        }
        // 品牌区（窄窗隐藏）：renderForEach 在 memo 内现读窗口宽，缩放可增减
        renderForEach({ callback -> if (!veryNarrow()) callback(0, Unit) }) { _, _ ->
            dText({ "WY Markets" }, 11f, { D_TEXT2 }, 600)
            // 分隔竖线
            object : RectNode(this) {
                override val argWidth: LayoutSize get() = LayoutSize(1f, false)
                override val argHeight: LayoutSize get() = LayoutSize(22f, false)

                override fun draw(canvas: PlatformCanvas) {
                    fillOuterRect(canvas, D_LINE)
                }
            }
        }
        // 交易对 + 最新价 + 涨跌（tOf 走 lambda：val t 会被捕获成启动快照，
        // 切换交易对后 header 价格/涨跌停在旧币种）
        dRow(gap = 8f, height = 30f) {
            dText({ symbol.value }, 13f, { D_TEXT }, 600)
            val tOf = { MarketData.tickerOf(symbol.value) }
            dText({ fmtPrice(tOf().last) }, 13f, { D_TEXT })
            dText(
                { fmtPercent(tOf().changePercent) }, 11f,
                { if (tOf().changePercent >= 0) D_UP else D_DOWN }, 600
            )
        }
        dSpacer()
        // 交易对 tabs（窄窗口只留前两个，完整列表在自选面板）；
        // take 结果在 forEach 内按窗口宽现算，登记为依赖
        renderForEach({ callback ->
            MarketData.symbols
                .take(if (veryNarrow()) 2 else MarketData.symbols.size)
                .forEach { s -> callback(s, s) }
        }) { _, each ->
            dTabButton({ each.value }, { symbol.value == each.value }) {
                if (symbol.value != each.value) {
                    symbol.value = each.value
                    limitPrice.value = null
                    note.value = ""
                }
            }
        }
        // 周期 tabs（窄窗口隐藏）
        renderForEach({ callback ->
            if (!narrow()) {
                MarketData.intervals.forEach { itv -> callback(itv, itv) }
            }
        }) { _, each ->
            dTabButton({ each.value }, { interval.value == each.value }, height = 22f, padX = 7f) {
                if (interval.value != each.value) {
                    interval.value = each.value
                    chartCount.value = ChartGeom.DEFAULT_VISIBLE
                    chartOffset.value = 0
                }
            }
        }
        // DEMO 徽章 → 免责声明（手动打开同样回写已读标记）
        dTabButton({ "DEMO" }, { false }, height = 20f, padX = 7f, chip = D_AMBER) {
            g.appendPop { pop ->
                dNoticeDialog(onClose = {
                    g.removePop(pop)
                    prefs.put("dash.noticeSeen", "1")
                })
            }
        }
        // 状态点
        object : RectNode(this) {
            override val argWidth: LayoutSize get() = LayoutSize(6f, false)
            override val argHeight: LayoutSize get() = LayoutSize(6f, false)

            override fun draw(canvas: PlatformCanvas) {
                fillOuterOval(canvas, D_AMBER)
            }
        }
        renderForEach({ callback -> if (!narrow()) callback(0, Unit) }) { _, _ ->
            dText({ "MOCK" }, 9f, { D_TEXT3 }, 600)
        }
    }
}
