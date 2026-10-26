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

internal const val TICKET_BALANCE = 10000.0

internal fun StateHolder<Node, List<Node>>.dTicket(
    ticker: () -> Ticker?,
    limitPrice: StoreRef<Double?>,
    note: () -> String,
    onNote: (String) -> Unit
) {
    val side = createSignal("buy")
    val amount = createSignal("0.05")

    fun priceOf(): Double = limitPrice.value ?: ticker()?.last ?: 0.0

    object : SimpleScrollNode(this) {
        override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f
        override val scrollBarStyle: ScrollBarStyle get() = DARK_SCROLL_BAR
        override val contentGap: Float get() = 8f

        override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
            // ── 买卖 tab（实底强调级：选中买卖色填充 + 白字，对齐 ooc ticket 蓝本）──
            dRow(gap = 6f, height = 28f, padX = 10f) {
                dTabButton(
                    { "买入 / Buy" },
                    { side.value == "buy" },
                    height = 26f,
                    chipOf = { if (side.value == "buy") D_UP else null },
                    grow = true,
                    solid = true
                ) {
                    side.value = "buy"
                    onNote("")
                }
                dTabButton(
                    { "卖出 / Sell" },
                    { side.value == "sell" },
                    height = 26f,
                    chipOf = { if (side.value == "sell") D_DOWN else null },
                    grow = true,
                    solid = true
                ) {
                    side.value = "sell"
                    onNote("")
                }
            }

            // ── 限价行 ──
            dRow(gap = 8f, height = 16f, padX = 10f, justify = DirectionJustify.between) {
                dText({ "Limit price" }, 9.5f, { D_TEXT3 }, 600)
                dRow(gap = 6f, height = 14f) {
                    dText({ fmtPrice(priceOf()) }, 11f, { D_TEXT })
                    // ×按钮走 renderForEach：结构 if 读 limitPrice 是启动快照，
                    // 点盘口价填入限价后 × 永远出不来
                    renderForEach({ callback ->
                        if (limitPrice.value != null) callback(0, Unit)
                    }) { _, _ ->
                        dTabButton({ "×" }, { false }, height = 14f, padX = 4f) {
                            limitPrice.value = null
                        }
                    }
                }
            }

            // ── Amount(USDT) 行：右侧为名义金额 ──
            dRow(gap = 8f, height = 16f, padX = 10f, justify = DirectionJustify.between) {
                dText({ "Amount (USDT)" }, 9.5f, { D_TEXT3 }, 600)
                // 名义金额在文本 lambda 内现算：argChildren 一次性，
                // 外面捕获 total 是启动快照，输入/改价后不更新
                dText({
                    val total = priceOf() * (amount.value.toDoubleOrNull() ?: 0.0)
                    if (total > 0) fmtPrice(total) else "0.00"
                }, 11f, { D_TEXT })
            }

            // ── 数量输入框 ──
            object : EditableTextNode(this) {
                override var text: String
                    get() = amount.value
                    set(v) {
                        amount.value = v
                        onNote("")
                    }

                override val singleLine: Boolean = true
                override val fontSize: Float = 13f
                override val textAlign: TextAlign = TextAlign.END
                override val cursorColor: ColorInt get() = D_ACCENT
                override val selectionColor: ColorInt = rgba(76, 141, 255, 70)
                override val placeholderColor: ColorInt get() = D_TEXT3
                override val argHeight: LayoutSize = LayoutSize(28f, false)

                init {
                    placeholder = "0.05"
                }

                override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                    if (direction == Direction.x) 8f else 4f

                override fun draw(canvas: PlatformCanvas) {
                    fillOuterRoundRect(canvas, 4f, rgba(0, 0, 0, 60))
                    strokeOuterRoundRect(
                        canvas, 4f,
                        if (isFocused) D_ACCENT else D_LINE_SOFT,
                        if (isFocused) 1.5f else 1f
                    )
                    super.draw(canvas)
                }
            }

            // ── 25 / 50 / 75 / MAX ──
            dRow(gap = 6f, height = 20f, padX = 10f) {
                listOf(0.25 to "25%", 0.5 to "50%", 0.75 to "75%", 1.0 to "MAX").forEach { (r, label) ->
                    dTabButton({ label }, { false }, height = 18f, padX = 0f, grow = true) {
                        val p = priceOf()
                        if (p > 0) {
                            amount.value = String.format(
                                java.util.Locale.US, "%.6f", TICKET_BALANCE * r / p
                            )
                            onNote("")
                        } else {
                            onNote("等待行情…")
                        }
                    }
                }
            }

            // ── Est. qty ──
            dRow(gap = 8f, height = 16f, padX = 10f, justify = DirectionJustify.between) {
                dText({ "Est. qty" }, 9.5f, { D_TEXT3 }, 600)
                // 数量在文本 lambda 内现算：同上，捕获快照不随输入/改价更新
                dText({
                    val p = priceOf()
                    val qty = if (p > 0) (amount.value.toDoubleOrNull() ?: 0.0) / p else 0.0
                    fmtQty(qty)
                }, 11f, { D_TEXT2 })
            }

            // ── 提交（整行实底：买卖色填充 + 白字，压深/白纱反馈，对齐 ooc 蓝本）──
            dSubmitButton({ side.value }) {
                val p = priceOf()
                val qty = if (p > 0) (amount.value.toDoubleOrNull() ?: 0.0) / p else 0.0
                val verb = if (side.value == "buy") "买入" else "卖出"
                onNote("模拟$verb ${fmtQty(qty)} — 未提交交易所")
            }

            // ── 提示行 ──
            // 换行文本必须放在纵向容器里：交叉轴拉伸取宽（父级只读它的高度）。
            // 若放进横向 dRow，父级会回读它的宽度 → sizeFromParent → 布局重入崩溃。
            dCol(gap = 0f, height = 26f, padX = 10f) {
                dText(note, 9f, { D_TEXT3 }, wrap = true)
            }
        }
    }
}

/**
 * 提交按钮：整行实底（ooc ticket 蓝本）——买卖色填充 + 白字 600，
 * 按压叠深色、悬停覆白纱；宽度沿滚动内容交叉轴拉伸（与数量输入框同机制）。
 */
private fun StateHolder<Node, List<Node>>.dSubmitButton(
    side: () -> String,
    onClick: () -> Unit
): ButtonBase = object : ButtonBase(this) {
    override val debugName: String get() = "提交按钮"
    override val argHeight: LayoutSize get() = LayoutSize(30f, false)

    override fun onClick() = onClick()

    override fun drawContent(canvas: PlatformCanvas) {
        fillOuterRoundRect(canvas, 4f, if (side() == "buy") D_UP else D_DOWN)
        when {
            pressed -> fillOuterRoundRect(canvas, 4f, rgba(0, 0, 0, 45))
            hovered -> fillOuterRoundRect(canvas, 4f, rgba(255, 255, 255, 26))
        }
    }

    override fun drawFocusRing(canvas: PlatformCanvas) {
        strokeOuterRing(canvas, 2f, 5f, D_ACCENT, 1.5f)
    }

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        // 文本色/文案在 lambda 内现算：argChildren 一次性，外面捕获是启动快照
        dText(
            { if (side() == "buy") "买入" else "卖出" },
            12f, { rgba(255, 255, 255) }, 600
        )
    }
}

// ════════════════════════════════════════════════════
// 十五、免责声明弹窗 + 深度图全屏浮层
// ════════════════════════════════════════════════════

