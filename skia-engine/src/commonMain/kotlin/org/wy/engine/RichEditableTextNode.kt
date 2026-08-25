package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue
import kotlin.math.min

/**
 * 富文本编辑节点：在 [EditableTextNode] 的编辑能力（光标、导航、撤销、IME、
 * 占位 / 掩码显示）之上，把内容从纯文本升级为"样式分段列表"。
 *
 * 设计要点：
 * - 逻辑文本仍是唯一真相源，光标 / 导航 / 撤销全部复用父类；
 * - 样式段采用"边界表"表示：每段记录自己的结束偏移与样式，第 i 个字符的样式
 *   = 第一个 end > i 的段的样式（无则 [baseStyle]）；
 * - 父类所有写入都经 [writeText]：这里按公共前缀 / 后缀对齐新旧文本，
 *   被替换区间的段裁剪平移，新插入区间继承插入点左侧字符的样式——因此
 *   打字、退格、选区替换、撤销重做、IME 提交都自动维持样式一致性。
 */
open class RichEditableTextNode(
    context: StateHolder<Node, List<Node>>,
    maxHistorySize: Int = 100
) : EditableTextNode(context, maxHistorySize) {

    /** 样式段：覆盖 (prevEnd, end] 区间；style 为 null 表示使用 [baseStyle]。 */
    data class Segment(val end: Int, val style: RichTextStyle?)

    /** 无显式样式时的兜底样式（默认由节点排版属性构成）。 */
    open val baseStyle: RichTextStyle
        get() = RichTextStyle(
            fontFamily,
            fontSize,
            fontWeight,
            italic,
            color,
            letterSpacing,
            wordSpacing,
            lineHeightMultiplier
        )

    private var segmentList by createSignal(emptyList<Segment>())

    // ---------- 样式查询 ----------

    /** 覆盖第 [idx] 个字符的显式样式；无显式段时返回 null（即基础样式）。 */
    fun styleAt(idx: Int): RichTextStyle? {
        if (text.isEmpty()) return null
        val i = idx.coerceIn(0, text.length - 1)
        for (seg in segmentList) {
            if (i < seg.end) return seg.style
        }
        return null
    }

    /**
     * 将区间 [start, end) 设为给定样式；style 为 null 表示回退到 [baseStyle]。
     * 不进入文本撤销栈。
     */
    fun styleRange(start: Int, end: Int, style: RichTextStyle?) {
        val len = text.length
        val s = start.coerceIn(0, len)
        val e = end.coerceIn(s, len)
        if (s >= e) return
        val out = mutableListOf<Segment>()
        var prev = 0
        for (seg in segmentList) {
            val segStart = prev
            prev = seg.end
            when {
                seg.end <= s || segStart >= e -> out.add(seg)   // 完全在区间外
                else -> {                                       // 与区间相交：裁剪两侧保留
                    if (segStart < s) out.add(Segment(s, seg.style))
                    if (seg.end > e) out.add(Segment(seg.end, seg.style))
                }
            }
        }
        out.add(Segment(e, style))
        segmentList = normalize(out, len)
    }

    // ---------- 写入钩子：差异对齐样式段 ----------

    override fun writeText(newValue: String) {
        val old = text
        if (newValue != old) {
            val (p, insEnd) = diffBounds(old, newValue)
            segmentList = alignSegments(old.length, newValue.length, p, insEnd)
        }
        super.writeText(newValue)
    }

    /** 公共前缀长度 p 与新串插入终点 insEnd（旧串删除终点 = insEnd - delta）。 */
    private fun diffBounds(old: String, new: String): Pair<Int, Int> {
        var p = 0
        val m = min(old.length, new.length)
        while (p < m && old[p] == new[p]) p++
        var q = 0
        while (q < m - p && old[old.length - 1 - q] == new[new.length - 1 - q]) q++
        return p to (new.length - q)
    }

    private fun alignSegments(oldLen: Int, newLen: Int, p: Int, insEnd: Int): List<Segment> {
        val delta = newLen - oldLen
        val oldRemoveEnd = insEnd - delta
        val out = mutableListOf<Segment>()
        var prev = 0
        for (seg in segmentList) {
            val start = prev
            prev = seg.end
            when {
                seg.end <= p -> out.add(seg)                            // 整段在被删区之前
                start >= oldRemoveEnd ->                                // 整段在后：整体平移
                    out.add(Segment(seg.end + delta, seg.style))
                else -> {                                               // 相交：裁剪拼接
                    if (start < p) out.add(Segment(p, seg.style))       // 左半保留到 p
                    if (seg.end > oldRemoveEnd)                         // 右半接在新插入区之后
                        out.add(Segment(insEnd + (seg.end - oldRemoveEnd), seg.style))
                }
            }
        }
        if (insEnd > p) {
            // 新插入区间继承插入点左侧字符的样式（文档首无左邻时取右邻）
            val inherit = if (oldLen == 0) null else styleAt((p - 1).coerceIn(0, oldLen - 1))
            out.add(Segment(insEnd, inherit))
        }
        return normalize(out, newLen)
    }

    /** 排序、钳制边界、合并相邻同款，保证边界表铺满 [0, len]。 */
    private fun normalize(segments: List<Segment>, len: Int): List<Segment> {
        if (len == 0) return emptyList()
        val out = mutableListOf<Segment>()
        for (s in segments.sortedBy { it.end }) {
            val e = s.end.coerceIn(1, len)
            val last = out.lastOrNull()
            when {
                last == null -> out.add(Segment(e, s.style))
                e <= last.end -> {}                                     // 被覆盖
                s.style == last.style -> out[out.size - 1] = Segment(e, last.style)
                else -> out.add(Segment(e, s.style))
            }
        }
        if ((out.lastOrNull()?.end ?: 0) < len) out.add(Segment(len, null))
        return out
    }

    // ---------- 显示片段（占位由父类 spans 处理） ----------

    override fun displaySpans(): List<RichTextSpan> {
        if (!obscureText) return contentSpans()
        // 掩码：逐簇圆点，样式取簇起点所在段，相邻同款圆点合并成段
        val out = mutableListOf<RichTextSpan>()
        var i = 0
        while (i < text.length) {
            val st = styleAt(i) ?: baseStyle
            val last = out.lastOrNull()
            if (last != null && last.style == st) {
                out[out.size - 1] = RichTextSpan(last.text + "•", st)
            } else {
                out.add(RichTextSpan("•", st))
            }
            i = Graphemes.nextBoundary(text, i)
        }
        return out
    }

    /** 由样式段 + 当前文本还原内容片段。 */
    private fun contentSpans(): List<RichTextSpan> {
        val t = text
        if (t.isEmpty()) return emptyList()
        val out = mutableListOf<RichTextSpan>()
        var prev = 0
        for ((end, style) in segmentList) {
            val e = min(end, t.length)
            if (e > prev) {
                out.add(RichTextSpan(t.substring(prev, e).replace("\t", "    "), style ?: baseStyle))
                prev = e
                if (prev >= t.length) break
            }
        }
        if (prev < t.length) out.add(RichTextSpan(t.substring(prev).replace("\t", "    "), baseStyle))
        return out
    }
}
