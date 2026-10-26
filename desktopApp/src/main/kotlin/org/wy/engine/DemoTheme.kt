package org.wy.engine

import org.wy.engine.helper.Theme
import org.wy.engine.helper.ThemeColors
import org.wy.lib.StoreRef
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue
import java.util.prefs.Preferences

// ════════════════════════════════════════════════════
// 调色板（信号驱动主题）
//
// 颜色不是编译期常量，而是「消费点活读」的 getter。注意读取时机分两类：
// - 在 draw() / 段落 memo（dLabel、text 等的 color lambda）里读 → 注册信号依赖，
//   主题翻触发重算 / 重绘，颜色立即更新；
// - 在 argChildren 构建期读（直接传值给 color、label 等参数）→ 只取到构建那一刻
//   的快照，子树不会重建，切换主题后仍是旧色板。主题色一律走 lambda / getter 活读。
// 深浅两套色板各自完整，语义一一对应（卡片、边框、正文、次级文本…）。
// ════════════════════════════════════════════════════

internal val prefs: Preferences by lazy { Preferences.userRoot().node("wy-helper/demo") }

/** 全局深色模式信号；初始值来自用户偏好持久化。 */
internal val demoDark: StoreRef<Boolean> = createSignal(prefs.get("dark", "0") == "1")

/**
 * 应用主题切换：demo 调色板（各消费点经 lambda / getter 活读 demoDark）与
 * 引擎组件库 [Theme.current]（helper 组件整体换肤，文本默认色经 TextDefaults 同步）一起翻转。
 */
internal fun setDarkTheme(v: Boolean) {
    demoDark.value = v
    Theme.current = if (v) Theme(ThemeColors.dark()) else Theme.default()
    prefs.putBoolean("dark", v)
}

// 启动时按持久化偏好恢复引擎组件库主题（demo 调色板天然走 getter，无需恢复）
private val restoreTheme = run {
    if (demoDark.get()) Theme.current = Theme(ThemeColors.dark())
    true
}

// ── 浅色板（默认）──────────────────────────────────
private val L_BG = rgba(244, 246, 251)
private val L_CARD = rgba(255, 255, 255)
private val L_BORDER = rgba(226, 232, 240)
private val L_TEXT = rgba(30, 41, 59)
private val L_TEXT2 = rgba(100, 116, 139)
private val L_ACCENT = rgba(79, 70, 229)
private val L_ACCENT_HOVER = rgba(99, 91, 255)
private val L_ACCENT_DARK = rgba(63, 55, 216)
private val L_ACCENT_LIGHT = rgba(224, 231, 255)
private val L_BAR = rgba(199, 210, 254)
private val L_GRID = rgba(241, 245, 249)
private val L_PLACEHOLDER = rgba(148, 163, 184)
private val L_HOVER = rgba(241, 245, 245)

// ── 深色板 ─────────────────────────────────────────
// 强调色在深底上整体提亮一档保证对比度；功能色（绿/琥珀/红）两套共用。
private val DK_BG = rgba(15, 18, 26)
private val DK_CARD = rgba(24, 28, 38)
private val DK_BORDER = rgba(45, 51, 65)
private val DK_TEXT = rgba(226, 232, 244)
// 次要文字提亮一档（146,156,174 视觉发灰，夜间层级仍弱于 DK_TEXT）
private val DK_TEXT2 = rgba(178, 188, 206)
private val DK_ACCENT = rgba(122, 114, 255)
private val DK_ACCENT_HOVER = rgba(140, 132, 255)
private val DK_ACCENT_DARK = rgba(99, 91, 255)
private val DK_ACCENT_LIGHT = rgba(58, 62, 88)
private val DK_BAR = rgba(96, 106, 152)
private val DK_GRID = rgba(35, 40, 52)
private val DK_PLACEHOLDER = rgba(104, 114, 134)
private val DK_HOVER = rgba(38, 44, 58)

// ── 语义色（读 demoDark 决定取哪套）────────────────
internal val BG: ColorInt get() = if (demoDark.value) DK_BG else L_BG
internal val CARD: ColorInt get() = if (demoDark.value) DK_CARD else L_CARD
internal val BORDER: ColorInt get() = if (demoDark.value) DK_BORDER else L_BORDER
internal val TEXT: ColorInt get() = if (demoDark.value) DK_TEXT else L_TEXT
internal val TEXT2: ColorInt get() = if (demoDark.value) DK_TEXT2 else L_TEXT2
internal val ACCENT: ColorInt get() = if (demoDark.value) DK_ACCENT else L_ACCENT
internal val ACCENT_HOVER: ColorInt get() = if (demoDark.value) DK_ACCENT_HOVER else L_ACCENT_HOVER
internal val ACCENT_DARK: ColorInt get() = if (demoDark.value) DK_ACCENT_DARK else L_ACCENT_DARK
internal val ACCENT_LIGHT: ColorInt get() = if (demoDark.value) DK_ACCENT_LIGHT else L_ACCENT_LIGHT
internal val BAR: ColorInt get() = if (demoDark.value) DK_BAR else L_BAR
internal val GRID: ColorInt get() = if (demoDark.value) DK_GRID else L_GRID
internal val PLACEHOLDER: ColorInt get() = if (demoDark.value) DK_PLACEHOLDER else L_PLACEHOLDER

/** 非主按钮的悬停底色（原先散落的内联浅灰，收编成语义色）。 */
internal val HOVER: ColorInt get() = if (demoDark.value) DK_HOVER else L_HOVER

// ── 功能色（两套主题共用）──────────────────────────
internal val GREEN = rgba(16, 185, 129)
internal val AMBER = rgba(245, 158, 11)
internal val RED = rgba(239, 68, 68)

internal fun Node.isVisibleToRoot(): Boolean {
    var n: Node? = this
    while (n != null) {
        if (n.hide) return false
        n = n.parent
    }
    return true
}
