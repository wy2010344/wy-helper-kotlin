package org.wy.engine.dashboard

import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.lib.StoreRef

// ════════════════════════════════════════════════════
// 看板顶栏（自 DashPage 拆出）
//
// 结构（对齐 finance-dashboard.ooc header）：
//   logo 瓦片 → 品牌区（<1340 隐藏）→ 交易对 + 最新价 + 涨跌 →
//   交易对 tabs（<1400 留 4 个 / <1200 留 2 个）→ 周期 tabs（<1060 隐藏）→
//   DEMO 徽章 → 状态点 → MOCK 状态文案 + 数据源开关（<1020 只留状态点）。
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
    // 断点分四级（顶栏右侧新增 MOCK 开关后需要更多横向预算）：
    // 优先让装饰性内容（品牌区）和交易对快选（自选面板已全量）先收起，
    // 保留周期 tabs 与数据源开关这两项功能性控件。
    val hideBrand = { windowWidth() < 1340f }      // 品牌区隐藏
    val hideIntervals = { windowWidth() < 1060f }  // 周期 tabs 隐藏
    val tight = { windowWidth() < 1200f }          // 交易对 tabs 只留前 2 个
    val compact = { windowWidth() < 1400f }        // 交易对 tabs 只留前 4 个
    val veryNarrow = { windowWidth() < 1020f }     // 数据源开关收起（只留状态点）

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
        renderForEach({ callback -> if (!hideBrand()) callback(0, Unit) }) { _, _ ->
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
            val tOf = { DashMarket.tickerOf(symbol.value) }
            dText({ fmtPrice(tOf().last) }, 13f, { D_TEXT })
            dText(
                { fmtPercent(tOf().changePercent) }, 11f,
                { if (tOf().changePercent >= 0) D_UP else D_DOWN }, 600
            )
        }
        dSpacer()
        // 交易对 tabs（窄窗口逐级收起：≥1400 全量 / ≥1200 前四个 / 更窄前两个；
        // 完整列表在自选面板）。take 结果在 forEach 内按窗口宽现算，登记为依赖
        renderForEach({ callback ->
            val n = when {
                tight() -> 2
                compact() -> 4
                else -> MarketData.symbols.size
            }
            MarketData.symbols.take(n).forEach { s -> callback(s, s) }
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
            if (!hideIntervals()) {
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
        // 状态点：颜色随数据源连接状态（LIVE 绿 / MOCK 蓝 / 轮询·重连 琥珀 / 离线 灰）
        object : RectNode(this) {
            override val argWidth: LayoutSize get() = LayoutSize(6f, false)
            override val argHeight: LayoutSize get() = LayoutSize(6f, false)

            override fun draw(canvas: PlatformCanvas) {
                fillOuterOval(canvas, feedStatusColor(DashMarket.status.value))
            }
        }
        // 状态文案 + 数据源开关（MOCK ON = 正在吃本地模拟；点一下切币安实时）
        // 超窄窗口只保留状态点：开关与文案让位给交易对/价格信息
        renderForEach({ callback -> if (!veryNarrow()) callback(0, Unit) }) { _, _ ->
            dText({ feedLabel(DashMarket.status.value) }, 9f, { D_TEXT3 }, 600)
            dTabButton(
                { if (DashMarket.source.value == FeedSource.SIM) "MOCK ON" else "MOCK" },
                { DashMarket.source.value == FeedSource.SIM },
                height = 20f,
                padX = 7f,
                chipOf = { if (DashMarket.source.value == FeedSource.SIM) rgba(56, 189, 248) else null }
            ) { DashMarket.toggleSource() }
        }
    }
}
