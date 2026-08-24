package org.wy.engine

/**
 * 字素簇（grapheme cluster）边界计算：纯 Kotlin 实现，跨平台可用。
 *
 * 背景：Kotlin String 以 UTF-16 code unit 编址，而用户感知的"一个字符"往往是多个
 * code unit 的组合（emoji 代理对、组合重音、ZWJ 家庭、国旗对等）。光标移动与删除
 * 必须以簇为单位，否则 emoji 会被拆半、退格删出乱码（对标 Web Intl.Segmenter /
 * Flutter characters 包的默认语义）。
 *
 * 实现说明：这是 UAX #29 的**近似实现**，覆盖常见场景：
 * - UTF-16 代理对（😀）；
 * - CR+LF 合并为一个换行簇；
 * - 组合标记常见区段（é = e + U+0301、泰文元音符号等）；
 * - 变体选择符（U+FE00-FE0F）；
 * - ZWJ（U+200D）连接序列：👨‍👩‍👧‍👦 整体为一簇；
 * - 区域指示符（国旗 🇨🇳）严格两两配对。
 * 未实现：Prepend 类文字、完整 Indic 音节规则（遇到时退化为普通字符，不致崩溃）。
 */
object Graphemes {

    private const val ZWJ = '\u200D'

    private fun isHigh(c: Char) = c in '\uD800'..'\uDBFF'
    private fun isLow(c: Char) = c in '\uDC00'..'\uDFFF'
    private fun cp(hi: Char, lo: Char): Int =
        0x10000 + ((hi.code - 0xD800) shl 10) + (lo.code - 0xDC00)
    private fun isRegionalIndicator(v: Int) = v in 0x1F1E6..0x1F1FF

    /** Emoji 肤色修饰符（U+1F3FB..U+1F3FF，代理对形态）：依附于前一个 emoji 核心。 */
    private fun isEmojiModifier(v: Int) = v in 0x1F3FB..0x1F3FF
    private fun isEmojiModifierPair(hi: Char, lo: Char): Boolean =
        isHigh(hi) && isLow(lo) && isEmojiModifier(cp(hi, lo))

    /** 组合标记（Mn/Me 及常见 Mc 区段）：附加到前簇。 */
    private fun isCombining(c: Char) =
        c in '\u0300'..'\u036F' ||   // 拉丁/希腊/西里尔组合附加符号
        c in '\u0483'..'\u0489' ||   // 西里尔
        c in '\u0591'..'\u05BD' ||   // 希伯来
        c in '\u0610'..'\u061A' ||   // 阿拉伯
        c in '\u064B'..'\u065F' ||
        c == '\u0670' ||
        c in '\u06D6'..'\u06DC' ||
        c in '\u20D0'..'\u20F0' ||   // 符号组合标记
        c == '\u0E31' ||             // 泰文
        c in '\u0E34'..'\u0E3A' ||
        c in '\u0E47'..'\u0E4E' ||
        c == '\u0EB1' ||
        c in '\u0EB4'..'\u0EBC' ||
        c in '\uFE20'..'\uFE2F'      // 组合半标志

    /** 变体选择符（文本/emoji 呈现选择）。 */
    private fun isVariationSelector(c: Char) = c in '\uFE00'..'\uFE0F'

    /**
     * [index] 所在位置的下一簇边界（即当前簇的结束偏移）。
     * [index] 已在边界上时返回下一簇的结束；越界钳制到 [0, length]。
     */
    fun nextBoundary(text: String, index: Int): Int {
        val n = text.length
        if (index < 0) return 0
        if (index >= n) return n
        var i = index
        var riCount = 0                       // 本簇已并入的 RI 个数（两两配对，奇数表示待配对）
        // —— 簇核心 ——
        val first = text[i]
        i += when {
            first == '\r' && i + 1 < n && text[i + 1] == '\n' -> 2
            isHigh(first) && i + 1 < n && isLow(text[i + 1]) -> {
                if (isRegionalIndicator(cp(first, text[i + 1]))) riCount = 1
                2
            }
            else -> 1
        }
        // —— 吸收后续 Extend / ZWJ / emoji 核心 / RI 配对 ——
        while (i < n) {
            val c = text[i]
            when {
                c == '\r' || c == '\n' -> break           // 换行另起一簇
                isLow(c) -> break                          // 孤立低代理自成簇
                isVariationSelector(c) || isCombining(c) || c == ZWJ -> i++
                isHigh(c) -> {
                    if (i + 1 >= n || !isLow(text[i + 1])) break
                    when {
                        // 肤色修饰符依附前簇（👋🏽），无论是否经 ZWJ 连接
                        isEmojiModifierPair(c, text[i + 1]) -> i += 2
                        isRegionalIndicator(cp(c, text[i + 1])) ->
                            // RI 两两配对（UAX29 GB12/13）：待配对则并入，已成对则另起一簇
                            if (riCount % 2 == 1) { riCount++; i += 2 } else break
                        text[i - 1] == ZWJ -> i += 2       // 仅当 ZWJ 连接时并入 emoji 核心（👨‍👩‍👧‍👦）
                        else -> break                      // 相邻的普通 emoji 另起一簇
                    }
                }
                else -> break                              // 普通字符另起一簇
            }
        }
        return i
    }

    /** [index] 前一个簇边界；[index] <= 0 时返回 0。约定调用方传入合法索引。 */
    fun prevBoundary(text: String, index: Int): Int {
        if (index <= 0) return 0
        val n = text.length
        var j = index - 1
        // CRLF 整体回退
        if (text[j] == '\n' && j > 0 && text[j - 1] == '\r') return j - 1
        // 从 index-1 向前逐格判定，以下情况继续回退：
        // ① j 在代理对低半 → 回到对首；② j 自身是依附字符（Extend/VS/ZWJ）；
        // ③ j 是被 ZWJ 连接的核心（其前恰为 ZWJ）→ ZWJ 链整体一簇（👨‍👩‍👧‍👦）；
        // ④ j 是肤色修饰符对的高位 → 整对依附前簇（👋🏽）；
        // ⑤ j 是普通 emoji 对的高位、且其后紧跟肤色修饰符对 → 基础+肤色不可拆。
        while (j > 0) {
            val back = when {
                isLow(text[j]) && isHigh(text[j - 1]) -> true
                isAttach(text[j]) -> true
                isHigh(text[j]) && text[j - 1] == ZWJ -> true
                j + 1 < n && isEmojiModifierPair(text[j], text[j + 1]) -> true
                isHigh(text[j]) && j + 3 < n &&
                    isLow(text[j + 1]) &&
                    isEmojiModifierPair(text[j + 2], text[j + 3]) -> true
                else -> false
            }
            if (!back) break
            j--
        }
        return j
    }

    private fun isAttach(c: Char) = c == ZWJ || isCombining(c) || isVariationSelector(c)

    /** [text] 的字素簇数量（密码框掩码长度等用途）。 */
    fun clusterCount(text: String): Int {
        var i = 0
        var count = 0
        while (i < text.length) {
            i = nextBoundary(text, i)
            count++
        }
        return count
    }
}

/**
 * 简单词边界，供 Ctrl+←/→ 词跳与 Ctrl+Backspace/Delete 删词使用。
 *
 * 规则（对标主流编辑器的简化语义）：
 * - 连续的字母/数字为一个词（汉字按 Unicode 类别同样视为词字符，整段连跳）；
 * - 光标紧邻空白时先跳过空白；
 * - 其余字符（标点、符号、emoji）每簇一步。
 *
 * 刻意不依赖平台分词（getWordBoundary / BreakIterator）：键盘导航必须在
 * 无布局环境（段落未构建）下也可用，且行为跨平台一致。
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
}
