package org.wy.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 简单词边界测试：Ctrl+←/→ 词跳与 Ctrl+Backspace/Delete 删词的定位规则。
 * 词 = 连续字母/数字；紧邻空白先跳过；标点/emoji 每簇一步。
 */
class WordsTest {

    @Test
    fun prevFromWordEndGoesToWordStart() {
        // "hello world" 光标在末尾 → world 首（6）
        assertEquals(6, Words.prevBoundary("hello world", 11))
    }

    @Test
    fun prevAcrossSpaceGoesToPreviousWordStart() {
        // 光标在 "hello |world" → hello 首（0）
        assertEquals(0, Words.prevBoundary("hello world", 6))
    }

    @Test
    fun nextFromWordStartGoesToWordEnd() {
        assertEquals(5, Words.nextBoundary("hello world", 0))
    }

    @Test
    fun nextAcrossSpaceSkipsToNextWordEnd() {
        // "hello |world" → world 尾（11）
        assertEquals(11, Words.nextBoundary("hello world", 5))
    }

    @Test
    fun punctuationStepsOneCluster() {
        // "foo.bar" 光标在 4（'.' 后）：向前一步到 '.' 前
        assertEquals(3, Words.prevBoundary("foo.bar", 4))
        // 向后从 '.' 起步：一步到 'b' 前？——紧邻是词字符 'b'，应吞整个 bar
        assertEquals(7, Words.nextBoundary("foo.bar", 4))
    }

    @Test
    fun emojiTreatedAsSingleStep() {
        val s = "a\uD83D\uDE00b"
        // 从 b 前方向前：emoji 整簇一步跳过，落在 a 后（1）
        assertEquals(1, Words.prevBoundary(s, 3))
        // 从 a 后方向后：emoji 非词字符，一步跨过整簇，落在 b 前（3）
        assertEquals(3, Words.nextBoundary(s, 1))
    }

    @Test
    fun clampsOutOfRange() {
        assertEquals(0, Words.prevBoundary("abc", 0))
        assertEquals(3, Words.nextBoundary("abc", 3))
        assertEquals(0, Words.prevBoundary("abc", -5))
        assertEquals(3, Words.nextBoundary("abc", 99))
    }
}
