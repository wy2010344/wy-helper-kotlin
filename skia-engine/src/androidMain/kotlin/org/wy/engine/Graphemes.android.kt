package org.wy.engine

import android.icu.text.BreakIterator

/**
 * Android 平台的字素簇实现：委托 `android.icu.text.BreakIterator`（ICU 新数据，minSdk 24 可用，完整 UAX #29）。
 *
 * 语义映射与 JVM 一致：
 * - [nextBoundary] = `following(index)`；[prevBoundary] = `preceding(index)`；
 * - [clusterCount] = first() 起逐次 next() 至 DONE 的步数。
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