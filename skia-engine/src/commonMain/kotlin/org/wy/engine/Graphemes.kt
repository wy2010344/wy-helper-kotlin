package org.wy.engine

/**
 * 字素簇（grapheme cluster）边界计算：**expect 声明，平台实现**。
 *
 * 背景：Kotlin String 以 UTF-16 code unit 编址，而用户感知的"一个字符"往往是多个
 * code unit 的组合（emoji 代理对、组合重音、ZWJ 家庭、国旗对等）。光标移动与删除
 * 必须以簇为单位，否则 emoji 会被拆半、退格删出乱码（对标 Web Intl.Segmenter /
 * Flutter characters 包的默认语义；完整 UAX #29 由平台 BreakIterator / ICU 保证）。
 *
 * 实现说明：
 * - JVM：`java.text.BreakIterator.getCharacterInstance()`
 * - Android：`android.icu.text.BreakIterator.getCharacterInstance()`（minSdk 24，ICU 新数据）
 *
 * API 语义与平台迭代器精确对齐：
 * - [nextBoundary] 返回**严格大于** [index] 的第一个边界（即当前簇的结束偏移）；
 * - [prevBoundary] 返回**严格小于** [index] 的最后一个边界；
 * - [clusterCount] 为从头遍历到尾的簇数量。
 */
expect object Graphemes {
    /**
     * [index] 所在位置的下一簇边界（即当前簇的结束偏移）。
     * [index] 已在边界上时返回下一簇的结束；越界钳制到 [0, length]。
     */
    fun nextBoundary(text: String, index: Int): Int

    /** [index] 前一个簇边界；[index] <= 0 时返回 0。约定调用方传入合法索引。 */
    fun prevBoundary(text: String, index: Int): Int

    /** [text] 的字素簇数量（密码框掩码长度等用途）。 */
    fun clusterCount(text: String): Int
}

/**
 * 简单词边界，供 Ctrl+←/→ 词跳与 Ctrl+Backspace/Delete 删词使用。
 *
 * 规则（对标主流编辑器的简化语义）：
 * - 连续的字母/数字为一个词（汉字按 Unicode 类别同样视为词字符，整段连跳）；
 * - 光标紧邻空白时先跳过空白；
 * - 其余字符（标点、符号、emoji）每簇一步。
 *
 * 词的边界基于 [Graphemes] 的字素簇（平台 BreakIterator / ICU 实现）推导：
 * 键盘导航必须在无布局环境（段落未构建）下也可用，故不依赖排版层分词。
 */
object Words {

    private fun isWordChar(c: Char) = c.isLetterOrDigit() && !isHigh(c)

    private fun isHigh(c: Char) = c in '\uD800'..'\uDBFF'

    /** [pos] 前一个词边界。 */
    fun prevBoundary(text: String, pos: Int): Int {
        var i = pos.coerceIn(0, text.length)
        // 1) 跳过紧邻空白簇
        while (i > 0) {
            val p = Graphemes.prevBoundary(text, i)
            if (!text[p].isWhitespace()) break
            i = p
        }
        if (i <= 0) return 0
        // 2a) 紧邻是词字符：连续吞词簇；2b) 否则退一簇（标点/emoji）
        val p = Graphemes.prevBoundary(text, i)
        return if (isWordChar(text[p])) {
            var j = i
            while (j > 0) {
                val q = Graphemes.prevBoundary(text, j)
                if (!isWordChar(text[q])) break
                j = q
            }
            j
        } else {
            p
        }
    }

    /** [pos] 后一个词边界。 */
    fun nextBoundary(text: String, pos: Int): Int {
        val n = text.length
        var i = pos.coerceIn(0, n)
        // 1) 跳过紧邻空白簇
        while (i < n) {
            if (!text[i].isWhitespace()) break
            i = Graphemes.nextBoundary(text, i)
        }
        if (i >= n) return n
        // 2a) 紧邻是词字符：连续吞词簇；2b) 否则进一簇（标点/emoji）
        return if (isWordChar(text[i])) {
            var j = i
            while (j < n && isWordChar(text[j])) {
                j = Graphemes.nextBoundary(text, j)
            }
            j
        } else {
            Graphemes.nextBoundary(text, i)
        }
    }

    /**
     * 包含 [offset] 的整词区间（半开区间），供**双击选词**使用。
     *
     * 语义与导航（[prevBoundary]/[nextBoundary]）完全一致：
     * - offset 落在词字符（字母/数字/中文等，见 [isWordChar]）上 → 扩展为连续词字符簇区间；
     * - 落在标点 / 符号 / emoji / 空白上 → 该字素簇单独作为一个"词"。
     * 这样双击与 Ctrl+←/→ 的边界来自同一规则，杜绝"同一文本两套词界"的分歧。
     *
     * [offset] 越界钳制到有效索引；空文本返回 null。
     */
    fun wordRangeAt(text: String, offset: Int): Pair<Int, Int>? {
        val n = text.length
        if (n == 0) return null
        val pos = offset.coerceIn(0, n - 1)
        // 吸附到所在字素簇的起点（offset 可能指向 unicode 双单元簇的内部）
        val anchor = Graphemes.prevBoundary(text, pos + 1)
        if (!isWordChar(text[anchor])) {
            // 标点 / emoji / 空白：该簇自身即一词
            return anchor to Graphemes.nextBoundary(text, anchor)
        }
        // 向前扩展到词首
        var start = anchor
        while (start > 0) {
            val p = Graphemes.prevBoundary(text, start)
            if (p == start || !isWordChar(text[p])) break
            start = p
        }
        // 向后扩展到词尾
        var end = Graphemes.nextBoundary(text, anchor)
        while (end < n) {
            val q = Graphemes.nextBoundary(text, end)
            if (q == end || !isWordChar(text[end])) break
            end = q
        }
        return start to end
    }
}
