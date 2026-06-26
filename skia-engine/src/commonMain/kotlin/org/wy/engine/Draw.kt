package org.wy.engine

typealias ColorInt = Int

fun rgba( r: Int, g: Int, b: Int,a: Int=255): ColorInt =
    ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
