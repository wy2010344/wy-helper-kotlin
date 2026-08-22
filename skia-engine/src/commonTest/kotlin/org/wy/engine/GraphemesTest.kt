package org.wy.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 字素簇（grapheme cluster）边界测试：
 * emoji（代理对 / ZWJ 序列）、组合字符、国旗（RI 对）、CRLF 在光标移动与删除时不可拆半。
 */
class GraphemesTest {

    /** 从头到尾遍历，收集全部簇边界。 */
    private fun boundaries(text: String): List<Int> {
        val list = mutableListOf<Int>()
        var i = 0
        while (i < text.length) {
            i = Graphemes.nextBoundary(text, i)
            list.add(i)
        }
        return list
    }

    @Test
    fun asciiEachCharIsCluster() {
        assertEquals(listOf(1, 2, 3), boundaries("abc"))
    }

    @Test
    fun surrogatePairIsOneCluster() {
        // 😀 = U+1F600，UTF-16 占 2 个 code unit
        val s = "\uD83D\uDE00"
        assertEquals(2, s.length)
        assertEquals(listOf(2), boundaries(s))
    }

    @Test
    fun zwjFamilyIsOneCluster() {
        // 👨‍👩‍👧‍👦：4 个 emoji（各 2 单元）+ 3 个 ZWJ = 11 个单元，整体一簇
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66"
        assertEquals(11, family.length)
        assertEquals(listOf(11), boundaries(family))
    }

    @Test
    fun combiningAccentStaysWithBase() {
        // e + U+0301（组合尖音符）= é，一簇两单元
        assertEquals(listOf(2), boundaries("e\u0301"))
    }

    @Test
    fun crlfIsOneCluster() {
        // 簇：[a][\r\n][b] → 边界（簇尾）为 1、3、4
        assertEquals(listOf(1, 3, 4), boundaries("a\r\nb"))
    }

    @Test
    fun flagPairIsOneClusterAndAdjacentFlagsSplit() {
        // 🇨🇳 = 两个 Regional Indicator（各为代理对），整体一簇
        val cn = "\uD83C\uDDE8\uD83C\uDDF3"
        val us = "\uD83C\uDDFA\uD83C\uDDF8"
        assertEquals(4, cn.length)
        assertEquals(listOf(4), boundaries(cn))
        // 相邻两面国旗按对拆分：每面国旗一簇
        assertEquals(listOf(4, 8), boundaries(cn + us))
    }

    @Test
    fun variationSelectorStaysWithBase() {
        // ☀ + FE0F（emoji 呈现变体选择符）
        assertEquals(listOf(2), boundaries("☀\uFE0F"))
    }

    @Test
    fun zwjThenLetterSplitsAfterZwj() {
        // emoji + ZWJ + 普通字母：ZWJ 并入前簇，字母另起一簇
        assertEquals(listOf(3, 4), boundaries("\uD83D\uDE00\u200Db"))
    }

    @Test
    fun adjacentEmojiStartsNewCluster() {
        // 回归：相邻的普通 emoji 不得并入前一簇（曾把 b+😀 并成一簇）。
        // 簇：[a][😀][b] → 边界 1、3、4
        assertEquals(listOf(1, 3, 4), boundaries("a\uD83D\uDE00b"))
        assertEquals(listOf(1, 2, 4), boundaries("ab😊"))
    }

    @Test
    fun zwjJoinsAdjacentEmojiSequences() {
        // 😊 + ZWJ + 家庭序列：ZWJ 连接的整体为一簇（长度 2+11=13）
        val seq = "\uD83D\uDE0A\u200D\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66"
        assertEquals(listOf(seq.length), boundaries(seq))
    }

    @Test
    fun prevBoundaryRoundTripsThroughNext() {
        val samples = listOf(
            "hello",
            "a\uD83D\uDE00b",
            "\uD83D\uDC68\u200D\uD83D\uDC69x",
            "e\u0301f\r\ng",
            "\uD83C\uDDE8\uD83C\uDDF3!",
            "☀\uFE0Fok"
        )
        for (text in samples) {
            assertEquals(0, Graphemes.prevBoundary(text, 0))
            for (b in boundaries(text)) {
                val p = Graphemes.prevBoundary(text, b)
                assertEquals(b, Graphemes.nextBoundary(text, p),
                    "round-trip 失败：text=$text boundary=$b")
            }
        }
    }

    @Test
    fun prevBoundaryHandlesEmojiAndCombining() {
        // 😀 之后：前一簇起点是 0
        assertEquals(0, Graphemes.prevBoundary("\uD83D\uDE00x", 2))
        // é 的组合符之前：回到 e
        assertEquals(0, Graphemes.prevBoundary("e\u0301", 2))
        // CRLF 整体回退
        assertEquals(1, Graphemes.prevBoundary("a\r\nb", 3))
    }

    @Test
    fun clusterCount() {
        assertEquals(3, Graphemes.clusterCount("abc"))
        assertEquals(1, Graphemes.clusterCount("\uD83D\uDE00"))
        assertEquals(2, Graphemes.clusterCount("e\u0301a"))
        assertEquals(2, Graphemes.clusterCount("a\r\n"))
        assertEquals(0, Graphemes.clusterCount(""))
    }
}
