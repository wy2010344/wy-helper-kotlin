package org.wy.engine


fun inRange(before: Float, n: Float, size: Float): Boolean {
    return before < n && n < before + size
}