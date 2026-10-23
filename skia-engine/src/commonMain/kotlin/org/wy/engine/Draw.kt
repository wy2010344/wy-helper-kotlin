package org.wy.engine

typealias ColorInt = Int

fun rgba( r: Int, g: Int, b: Int,a: Int=255): ColorInt =
    ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

/** 取颜色透明度分量（0..255）。 */
val ColorInt.alpha: Int get() = (this ushr 24) and 0xFF

/** 整体乘算透明度（0f..1f）：保留 RGB，仅缩放 alpha。 */
fun ColorInt.withAlpha(multiplier: Float): ColorInt {
    val a = (alpha * multiplier.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
    return (a shl 24) or (this and 0x00FFFFFF)
}
