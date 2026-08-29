package org.wy.engine

import java.text.BreakIterator

/**
 * JVM 平台的字素簇实现：委托 JDK `java.text.BreakIterator.getCharacterInstance()`（ICU 数据，完整 UAX #29）。
 *
 * 语义映射（BreakIterator 边界集恒含 0 与 text.length）：
 * - [nextBoundary] = `following(index)`：返回严格大于 index 的第一个边界；
 * - [prevBoundary] = `preceding(index)`：返回严格小于 index 的最后一个边界；
 * - [clusterCount] = 以 first() 起逐次 next() 至 DONE 的步数。
 *
 * 每次调用新建实例：可重入、线程安全、无跨调用状态残留。切换文本无需顾虑索引缓存。
 */
actual object Graphemes {

    actual fun nextBoundary(text: String, index: Int): Int {
        val n = text.length
        if (index < 0) return 0
        if (index >= n) return n
        val bi = BreakIterator.getCharacterInstance()
        bi.setText(text)
        val b = bi.following(index)
        return if (b == BreakIterator.DONE) n else b
    }

    actual fun prevBoundary(text: String, index: Int): Int {
        if (index <= 0) return 0
        val bi = BreakIterator.getCharacterInstance()
        bi.setText(text)
        val b = bi.preceding(index)
        return if (b == BreakIterator.DONE) 0 else b
    }

    actual fun clusterCount(text: String): Int {
        val bi = BreakIterator.getCharacterInstance()
        bi.setText(text)
        var count = 0
        while (bi.next() != BreakIterator.DONE) count++
        return count
    }
}