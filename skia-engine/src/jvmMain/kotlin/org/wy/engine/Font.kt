package org.wy.engine

import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Typeface

val asName = System.getProperty("os.name").lowercase()

val chineseFontName = when {
    asName.contains("win") -> "Microsoft YaHei"
    asName.contains("mac") -> "PingFang SC"
    asName.contains("linux") -> "Noto Sans CJK SC"
    else -> "WenQuanYi Micro Hei"
}

fun loadSystemFont(
    familyName: String, style: FontStyle
): Typeface {
    return FontMgr.default.matchFamilyStyle(familyName, style) ?: FontMgr.default.matchFamilyStyle(
        null,
        style
    )!!
}