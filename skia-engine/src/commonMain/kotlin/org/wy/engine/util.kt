package org.wy.engine


fun inRange(before: Float, n: Float, size: Float): Boolean {
    return before < n && n < before + size
}

fun String.insert(index: Int, text: String): String {
    return substring(0, index) + text + substring(index)
}