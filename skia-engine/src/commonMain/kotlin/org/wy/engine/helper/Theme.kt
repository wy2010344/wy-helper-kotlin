package org.wy.engine.helper

import org.wy.engine.ColorInt
import org.wy.engine.rgba

/**
 * 组件库样式 token：集中管理颜色 / 圆角 / 字号等样式值。
 *
 * helper 组件的默认样式一律读取 [Theme.current]，不写死颜色值；
 * 业务层可整体替换 [Theme.current] 实现全局换肤，或逐组件 override 视觉方法实现局部定制。
 */
data class ThemeColors(
    /** 主色（默认按钮背景）。 */
    val primary: ColorInt,
    val primaryHover: ColorInt,
    val primaryPressed: ColorInt,
    /** 主色禁用态（背景 / 文字通用）。 */
    val primaryDisabled: ColorInt,
    /** 主色上的前景（按钮文字）。 */
    val onPrimary: ColorInt,
    /** 主色浅背景（选中项背景 / 分段控件选中卡片）。 */
    val primarySoft: ColorInt,
    /** 表面色（次级按钮背景、卡片）。 */
    val surface: ColorInt,
    val surfaceHover: ColorInt,
    val surfaceDisabled: ColorInt,
    /** 边框色。 */
    val border: ColorInt,
    /** hover 边框色（输入框 / 滑块悬停描边）。 */
    val borderHover: ColorInt,
    /** 正文文字色。 */
    val text: ColorInt,
    /** 次要文字色。 */
    val textSecondary: ColorInt,
    /** 禁用文字色。 */
    val textDisabled: ColorInt,
    /** 焦点环颜色。 */
    val focus: ColorInt,
    /** 滑块 / 开关轨道色。 */
    val track: ColorInt,
    /** 滑块 / 开关滑块色。 */
    val thumb: ColorInt,
    /** 输入框占位符色。 */
    val placeholder: ColorInt,
    /** 反色表面（暗色提示条等深色浮层背景）。 */
    val inverseSurface: ColorInt = rgba(30, 41, 59),
    /** 反色表面上的前景文字。 */
    val inverseOnSurface: ColorInt = rgba(255, 255, 255),
) {
    companion object {
        /** 浅色默认主题（与 desktop demo 调色板一致）。 */
        fun light(): ThemeColors = ThemeColors(
            primary = rgba(79, 70, 229),
            primaryHover = rgba(99, 91, 255),
            primaryPressed = rgba(63, 55, 216),
            primaryDisabled = rgba(165, 160, 240),
            onPrimary = rgba(255, 255, 255),
            primarySoft = rgba(224, 231, 255),
            surface = rgba(255, 255, 255),
            surfaceHover = rgba(241, 245, 249),
            surfaceDisabled = rgba(241, 245, 249),
            border = rgba(226, 232, 240),
            borderHover = rgba(199, 210, 254),
            text = rgba(30, 41, 59),
            textSecondary = rgba(100, 116, 139),
            textDisabled = rgba(160, 160, 170),
            focus = rgba(79, 70, 229),
            track = rgba(203, 213, 225),
            thumb = rgba(255, 255, 255),
            placeholder = rgba(148, 163, 184),
        )
    }
}

/** 圆角 token。 */
data class ThemeRadius(
    /** 按钮圆角。 */
    val button: Float = 8f,
    /** 输入框 / 开关 / 滑块等控件圆角。 */
    val control: Float = 8f,
    /** 卡片圆角。 */
    val card: Float = 10f,
)

/** 字号 token。 */
data class ThemeTextSize(
    /** 控件正文（按钮 / 开关行等）。 */
    val label: Float = 13f,
    /** 辅助说明（角标 / 提示）。 */
    val caption: Float = 11f,
)

/**
 * 全局主题：组件库默认样式的取值来源。
 * 替换 [Theme.current] 即可整体换肤；每个组件仍可通过覆盖视觉方法局部定制。
 */
data class Theme(
    val colors: ThemeColors,
    val radius: ThemeRadius = ThemeRadius(),
    val textSize: ThemeTextSize = ThemeTextSize(),
) {
    companion object {
        /** 当前主题，默认浅色。业务启动时可整体替换。 */
        var current: Theme = default()

        fun default(): Theme = Theme(colors = ThemeColors.light())
    }
}
