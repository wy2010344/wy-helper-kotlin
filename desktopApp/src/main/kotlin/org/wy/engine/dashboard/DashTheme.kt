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

// ── 终端配色（昼 / 夜双主题 + 中式涨红跌绿）────────
// 夜间（深色终端）
private val DK_BG = rgba(10, 14, 20)
private val DK_PANE = rgba(17, 22, 31)
private val DK_PANE_HEAD = rgba(22, 28, 39)
private val DK_PLOT = rgba(13, 17, 24)
private val DK_LINE = rgba(255, 255, 255, 15)
private val DK_LINE_SOFT = rgba(255, 255, 255, 8)
// 分割带底色：对应 web 的 bg-white/[0.02]（在盘口行之间拉出层次）
private val DK_BAND = rgba(255, 255, 255, 5)
private val DK_TEXT = rgba(230, 234, 242)
// 次要文字与 demo 深色板同步提亮一档，层级仍弱于 DK_TEXT
private val DK_TEXT2 = rgba(178, 188, 206)
private val DK_TEXT3 = rgba(96, 106, 122)
private val DK_ACCENT = rgba(76, 141, 255)
private val DK_HOVER = rgba(255, 255, 255, 12)
private val DK_ACTIVE_BG = rgba(76, 141, 255, 26)
private val DK_GRID = rgba(255, 255, 255, 9)
private val DK_AXIS = rgba(120, 130, 148)

// 白天（浅色终端）
private val LT_BG = rgba(244, 246, 251)
private val LT_PANE = rgba(255, 255, 255)
private val LT_PANE_HEAD = rgba(248, 250, 252)
private val LT_PLOT = rgba(255, 255, 255)
private val LT_LINE = rgba(30, 41, 59, 15)
private val LT_LINE_SOFT = rgba(30, 41, 59, 8)
// 浅色下对应 web 的 bg-white/[0.02]：用极淡的墨色代替白色叠加
private val LT_BAND = rgba(30, 41, 59, 6)
private val LT_TEXT = rgba(30, 41, 59)
private val LT_TEXT2 = rgba(100, 116, 139)
private val LT_TEXT3 = rgba(148, 163, 184)
private val LT_ACCENT = rgba(79, 70, 229)
private val LT_HOVER = rgba(30, 41, 59, 8)
private val LT_ACTIVE_BG = rgba(79, 70, 229, 22)
private val LT_GRID = rgba(30, 41, 59, 8)
private val LT_AXIS = rgba(100, 116, 139)

// 涨跌 / 琥珀两套主题共用：涨红跌绿语义色昼夜一致（shot 探针硬编码比色）
internal val D_UP = rgba(240, 76, 92) // 涨红（买盘同色）
internal val D_DOWN = rgba(14, 203, 129) // 跌绿（卖盘同色）
internal val D_AMBER = rgba(245, 166, 35)
internal val D_UP_BG = rgba(240, 76, 92, 30)
internal val D_DOWN_BG = rgba(14, 203, 129, 30)

// 语义 getter：draw / 段落 memo 内读 → 注册依赖，下帧换色；argChildren 构建期读 → 只取快照，换肤不更新
internal val D_BG get() = if (demoDark.value) DK_BG else LT_BG
internal val D_PANE get() = if (demoDark.value) DK_PANE else LT_PANE
internal val D_PANE_HEAD get() = if (demoDark.value) DK_PANE_HEAD else LT_PANE_HEAD
internal val D_PLOT get() = if (demoDark.value) DK_PLOT else LT_PLOT
internal val D_LINE get() = if (demoDark.value) DK_LINE else LT_LINE
internal val D_LINE_SOFT get() = if (demoDark.value) DK_LINE_SOFT else LT_LINE_SOFT
internal val D_BAND get() = if (demoDark.value) DK_BAND else LT_BAND
internal val D_TEXT get() = if (demoDark.value) DK_TEXT else LT_TEXT
internal val D_TEXT2 get() = if (demoDark.value) DK_TEXT2 else LT_TEXT2
internal val D_TEXT3 get() = if (demoDark.value) DK_TEXT3 else LT_TEXT3
internal val D_ACCENT get() = if (demoDark.value) DK_ACCENT else LT_ACCENT
internal val D_HOVER get() = if (demoDark.value) DK_HOVER else LT_HOVER
internal val D_ACTIVE_BG get() = if (demoDark.value) DK_ACTIVE_BG else LT_ACTIVE_BG
internal val D_GRID get() = if (demoDark.value) DK_GRID else LT_GRID
internal val D_AXIS get() = if (demoDark.value) DK_AXIS else LT_AXIS

/** 侧栏「看板」导航项的图标色（= 终端强调色，跨文件取用故单独导出）。 */
internal val DASH_ACCENT get() = D_ACCENT
internal fun fmtPrice(v: Double?): String {
    if (v == null || !v.isFinite()) return "--"
    val abs = abs(v)
    val digits = when {
        abs >= 1000 -> 2
        abs >= 1 -> 2
        abs >= 0.01 -> 4
        else -> 6
    }
    return trimZeros(String.format(java.util.Locale.US, "%.${digits}f", v))
}

internal fun fmtQty(v: Double?): String {
    if (v == null || !v.isFinite()) return "--"
    val abs = abs(v)
    val digits = when {
        abs >= 1000 -> 0
        abs >= 1 -> 3
        else -> 5
    }
    return trimZeros(String.format(java.util.Locale.US, "%.${digits}f", v))
}

internal fun fmtCompact(v: Double?): String {
    if (v == null || !v.isFinite()) return "--"
    val abs = abs(v)
    return when {
        abs >= 1e9 -> String.format(java.util.Locale.US, "%.2fB", v / 1e9)
        abs >= 1e6 -> String.format(java.util.Locale.US, "%.2fM", v / 1e6)
        abs >= 1e3 -> String.format(java.util.Locale.US, "%.2fK", v / 1e3)
        else -> String.format(java.util.Locale.US, "%.2f", v)
    }
}

internal fun fmtPercent(v: Double?): String {
    if (v == null || !v.isFinite()) return "--"
    return "${if (v >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.2f", v)}%"
}

/** 只去掉小数部分尾零，整数部分不能被吃掉。 */
internal fun trimZeros(text: String): String {
    if (!text.contains('.')) return text
    return text.replace(Regex("0+$"), "").replace(Regex("\\.$"), "")
}

/** 千分位分隔。 */
internal fun withThousands(text: String): String {
    val dot = text.indexOf('.')
    val head = if (dot < 0) text else text.substring(0, dot)
    val tail = if (dot < 0) "" else text.substring(dot)
    val sb = StringBuilder()
    head.forEachIndexed { i, c ->
        if (i > 0 && (head.length - i) % 3 == 0) sb.append(',')
        sb.append(c)
    }
    return sb.toString() + tail
}

/** UTC 时钟 HH:mm（不用 Date，避免时区 / 区域设置污染截图）。 */
internal fun fmtClockUtc(ms: Long): String {
    val secs = (ms / 1000) % 86_400L
    val h = secs / 3600
    val m = (secs % 3600) / 60
    return "${pad2(h)}:${pad2(m)}"
}

internal fun pad2(v: Long): String = if (v < 10) "0$v" else "$v"

/** 公历日期（Howard Hinnant days→civil），输入为 epoch 天数。 */
internal fun civilFromDays(z0: Long): Triple<Long, Long, Long> {
    var z = z0 + 719_468
    val era = (if (z >= 0) z else z - 146_096) / 146_097
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146_096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    return Triple(if (m <= 2) y + 1 else y, m, d)
}

/** 时间轴标签：跨度越长越粗（对齐 formatAxisTime）。 */
internal fun fmtAxisTime(time: Long, spanMs: Long): String {
    val clock = fmtClockUtc(time)
    if (spanMs >= 20 * 3600_000L) {
        val (_, m, d) = civilFromDays(time / 86_400_000L)
        return "${pad2(m)}-${pad2(d)} $clock"
    }
    return clock
}

/** 价格轴刻度：少一位视觉噪声，尽量塞进 64px 轴列。 */
internal fun fmtAxisPrice(value: Double): String {
    if (!value.isFinite()) return "--"
    val abs = abs(value)
    val digits = when {
        abs >= 1000 -> 2
        abs >= 1 -> 2
        abs >= 0.01 -> 4
        else -> 6
    }
    return trimZeros(String.format(java.util.Locale.US, "%,.${digits}f", value))
}

/** 盘口 Total 列：大数 K/M/B 收，小值 6 位。 */
internal fun fmtTotal(v: Double, exact: Boolean = false): String {
    if (!v.isFinite()) return "--"
    if (exact) return withThousands(trimZeros(String.format(java.util.Locale.US, "%.6f", v)))
    return when {
        v >= 1e9 -> String.format(java.util.Locale.US, "%.2fB", v / 1e9)
        v >= 1e6 -> String.format(java.util.Locale.US, "%.2fM", v / 1e6)
        v >= 1e3 -> String.format(java.util.Locale.US, "%.2fK", v / 1e3)
        v >= 1 -> String.format(java.util.Locale.US, "%.2f", v)
        else -> String.format(java.util.Locale.US, "%.6f", v)
    }
}

internal fun fmtStep(step: Double): String =
    if (!step.isFinite() || step <= 0) "--" else step.toString()

internal fun fmtShare(ratio: Double): String {
    val v = if (ratio.isFinite()) min(1.0, max(0.0, ratio)) else 0.5
    return String.format(java.util.Locale.US, "%.1f", v * 100)
}

internal fun fmtClockHms(ms: Long): String {
    val secs = (ms / 1000) % 86_400L
    return "${pad2(secs / 3600)}:${pad2((secs % 3600) / 60)}:${pad2(secs % 60)}"
}
// ════════════════════════════════════════════════════
// 六、画布文本助手
//
// K 线 / 深度图是单节点 canvas 直绘（蜡烛、影线、面积、十字线），
// 轴标签与浮标同样走 canvas：buildParagraph 按需建段、drawParagraph 落字。
// ════════════════════════════════════════════════════
internal fun textParagraph(text: String, size: Float, color: ColorInt, weight: Int = 400): PlatformParagraph {
    if (text.isEmpty()) {
        return buildParagraph(
            listOf(RichTextSpan(" ", RichTextStyle(null, size, weight, false, color))),
            Float.MAX_VALUE
        )
    }
    return buildParagraph(
        listOf(RichTextSpan(text, RichTextStyle(null, size, weight, false, color))),
        Float.MAX_VALUE
    )
}

/** 在 (x, y)（左上角）绘制一行文本。 */
internal fun drawText(
    canvas: PlatformCanvas,
    text: String,
    x: Float,
    y: Float,
    size: Float,
    color: ColorInt,
    weight: Int = 400
) {
    if (text.isEmpty()) return
    canvas.drawParagraph(textParagraph(text, size, color, weight), x, y)
}

/** 以 (cx, cy) 为视觉中心绘制一行文本。 */
internal fun drawTextCentered(
    canvas: PlatformCanvas,
    text: String,
    cx: Float,
    cy: Float,
    size: Float,
    color: ColorInt,
    weight: Int = 400
) {
    if (text.isEmpty()) return
    val p = textParagraph(text, size, color, weight)
    canvas.drawParagraph(p, cx - p.width / 2f, cy - p.height / 2f)
}

/** 右对齐到 rightX、垂直中心 cy。 */
internal fun drawTextEnd(
    canvas: PlatformCanvas,
    text: String,
    rightX: Float,
    cy: Float,
    size: Float,
    color: ColorInt,
    weight: Int = 400
) {
    if (text.isEmpty()) return
    val p = textParagraph(text, size, color, weight)
    canvas.drawParagraph(p, rightX - p.width, cy - p.height / 2f)
}

// ════════════════════════════════════════════════════
// 七、终端基础组件
// ════════════════════════════════════════════════════

/**
 * 终端文本。[wrap] = true 时宽度从父级拉伸并按宽折行（卡片 / 弹层文案）；
 * 给 [width] 时定宽单行不折行、溢出裁剪（右对齐数字列）；
 * 默认 autoWidth（宽度 = 文本宽，用于行内标签 / 徽标）。
 */
