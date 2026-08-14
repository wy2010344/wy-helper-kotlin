package org.wy.engine

interface Selectable {
    fun getOffsetAt(localX: Float, localY: Float): Int
    fun getRectsForRange(start: Int, end: Int): List<TextRect>
    fun getText(start: Int, end: Int): String
    fun textLength(): Int
    fun rootToLocal(rootX: Float, rootY: Float): Pair<Float, Float>
    fun localToRoot(localX: Float, localY: Float): Pair<Float, Float>
    fun localWidth(): Float
    fun localHeight(): Float
    val selectionOrder: Int
}
