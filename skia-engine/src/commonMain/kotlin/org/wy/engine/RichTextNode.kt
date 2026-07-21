package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.memo
import org.wy.signal.setValue
import kotlin.math.max
import kotlin.math.min

data class RichTextSpan(
    val text: String,
    val fontFamily: String? = null,
    val fontSize: Float = 16f,
    val fontWeight: Int = 400,
    val color: ColorInt = rgba(0, 0, 0)
)

open class RichTextNode(
    context: StateHolder<Node>
) : RectNode(context) {

    open val spans: List<RichTextSpan> = emptyList()
    open val selectionColor: ColorInt = rgba(100, 100, 200, 60)
    open val wordBreak: WordBreak = WordBreak.BREAK_WORD
    open val locale: String? = null

    private data class SpanRange(
        val start: Int,
        val end: Int,
        val fontFamily: String?,
        val fontWeight: Int,
        val fontSize: Float,
        val color: ColorInt
    )

    private val fullText: String
        get() = spans.joinToString("") { it.text }

    private val spanRanges: List<SpanRange>
        get() {
            val result = mutableListOf<SpanRange>()
            var pos = 0
            for (span in spans) {
                if (span.text.isNotEmpty()) {
                    result.add(
                        SpanRange(
                            pos, pos + span.text.length,
                            span.fontFamily, span.fontWeight, span.fontSize, span.color
                        )
                    )
                    pos += span.text.length
                }
            }
            return result
        }

    private fun measureRichRange(start: Int, end: Int): Float {
        if (start >= end) return 0f
        val text = fullText
        var total = 0f
        var pos = start
        for (sr in spanRanges) {
            if (pos >= end) break
            if (sr.end <= pos) continue
            val segStart = max(pos, sr.start)
            val segEnd = min(end, sr.end)
            if (segStart < segEnd) {
                total += measureText(
                    text.substring(segStart, segEnd),
                    sr.fontFamily, sr.fontWeight, sr.fontSize
                )
            }
            pos = segEnd
        }
        return total
    }

    private fun measureRichChar(index: Int): Float {
        val text = fullText
        if (index < 0 || index >= text.length) return 0f
        for (sr in spanRanges) {
            if (index >= sr.start && index < sr.end) {
                return measureText(
                    text[index].toString(),
                    sr.fontFamily, sr.fontWeight, sr.fontSize
                )
            }
        }
        return 0f
    }

    private fun charStyleAt(index: Int): SpanRange? {
        for (sr in spanRanges) {
            if (index >= sr.start && index < sr.end) return sr
        }
        return null
    }

    private fun isSpace(ch: Char) = ch == ' ' || ch == '\t'

    protected data class TextLine(val start: Int, val end: Int)

    private val lines = memo {
        val text = fullText
        if (text.isEmpty()) return@memo emptyList()
        val maxW = innerSize(Direction.x)
        val result = mutableListOf<TextLine>()
        var pos = 0
        val len = text.length

        while (pos < len) {
            val nl = text.indexOf('\n', pos)
            val segEnd = if (nl >= 0) nl else len
            if (segEnd > pos) {
                wrapSegment(pos, segEnd, maxW, result)
            } else {
                if (nl >= 0) result.add(TextLine(pos, pos))
            }
            pos = if (nl >= 0) nl + 1 else len
        }
        result
    }

    private fun wrapSegment(
        segStart: Int, segEnd: Int, maxW: Float, out: MutableList<TextLine>
    ) {
        if (maxW >= Float.MAX_VALUE) {
            out.add(TextLine(segStart, segEnd))
            return
        }
        val text = fullText
        val segmentText = text.substring(segStart, segEnd)
        val breaks = lineBreakOpportunities(segmentText, locale)
            .map { it + segStart }
            .filter { it in (segStart + 1)..<segEnd }

        var lineStart = segStart
        while (lineStart < segEnd) {
            while (lineStart < segEnd && isSpace(text[lineStart])) lineStart++
            if (lineStart >= segEnd) break

            if (wordBreak == WordBreak.ANY_CHAR) {
                var cx = 0f
                var ci = lineStart
                while (ci < segEnd) {
                    val cw = measureRichChar(ci)
                    if (cx + cw > maxW && ci > lineStart) break
                    cx += cw
                    ci++
                }
                out.add(TextLine(lineStart, ci))
                lineStart = ci
                continue
            }

            val lastFitIdx: Int? = breaks.lastOrNull { bp ->
                bp > lineStart && measureRichRange(lineStart, bp) <= maxW
            }

            val lineEnd: Int
            if (lastFitIdx != null) {
                lineEnd = lastFitIdx
            } else if (wordBreak == WordBreak.PHRASE) {
                val nextSpace = text.indexOf(' ', lineStart)
                lineEnd = if (nextSpace in lineStart until segEnd) nextSpace else segEnd
            } else {
                var low = lineStart
                var high = segEnd
                while (low < high) {
                    val mid = (low + high + 1) / 2
                    val w = measureRichRange(lineStart, mid)
                    if (w <= maxW) low = mid else high = mid - 1
                }
                if (low <= lineStart) low = lineStart + 1
                lineEnd = low
            }

            if (lineEnd > lineStart) {
                out.add(TextLine(lineStart, lineEnd))
            }
            lineStart = lineEnd
        }
    }

    private data class LineInfo(
        val start: Int,
        val end: Int,
        val y: Float,
        val height: Float
    )

    private fun maxFontSizeInLine(lineStart: Int, lineEnd: Int): Float {
        var maxFs = 0f
        for (sr in spanRanges) {
            if (sr.start < lineEnd && sr.end > lineStart) {
                maxFs = max(maxFs, sr.fontSize)
            }
        }
        return max(maxFs, 1f)
    }

    private val lineInfos = memo {
        val lineList = lines()
        val result = mutableListOf<LineInfo>()
        var y = 0f
        for (line in lineList) {
            val h = maxFontSizeInLine(line.start, line.end) * 1.4f
            result.add(LineInfo(line.start, line.end, y, h))
            y += h
        }
        result
    }

    private val charPositions = memo {
        val infoList = lineInfos()
        val result = mutableListOf<Pair<Float, Float>>()
        for (info in infoList) {
            var x = 0f
            for (i in info.start until info.end) {
                result.add(Pair(x, info.y))
                x += measureRichChar(i)
            }
        }
        result
    }

    private fun charAt(px: Float, py: Float): Int {
        val infoList = lineInfos()
        if (infoList.isEmpty()) return 0

        var lineIdx = 0
        for ((i, info) in infoList.withIndex()) {
            if (py < info.y + info.height) {
                lineIdx = i
                break
            }
            if (i == infoList.lastIndex) lineIdx = i
        }
        val info = infoList[lineIdx]

        var low = info.start
        var high = info.end
        while (low < high) {
            val mid = (low + high) / 2
            val w = measureRichRange(info.start, mid + 1)
            if (w <= px) low = mid + 1 else high = mid
        }
        return low.coerceIn(info.start, info.end)
    }

    override val argHeight: LayoutSize
        get() {
            val infoList = lineInfos()
            val totalH = if (infoList.isNotEmpty()) {
                val last = infoList.last()
                last.y + last.height
            } else {
                maxFontSizeInLine(0, 0) * 1.4f
            }
            return LayoutSize(totalH, true)
        }

    private var anchorIndex by createSignal(-1)
    private var focusIndex by createSignal(-1)

    val selectionText: String?
        get() {
            if (anchorIndex < 0 || focusIndex < 0 || anchorIndex == focusIndex) return null
            val s = min(anchorIndex, focusIndex)
            val e = max(anchorIndex, focusIndex)
            return fullText.substring(s, e)
        }

    private var onMouseDown = false

    override fun mouseDown(e: MouseEvent) {
        anchorIndex = charAt(e.x, e.y)
        focusIndex = anchorIndex
        onMouseDown = true
        e.stopPropagation()
    }

    init {
        val engineGlobal = context.consume(engineGlobalContext)!!
        val d1 = engineGlobal.registerMouseUp { e -> onMouseDown = false }
        val ax = memo { absolutePosition(Direction.x) }
        val ay = memo { absolutePosition(Direction.y) }
        val d2 = engineGlobal.registerMouseMove { e ->
            if (onMouseDown) focusIndex = charAt(e.x - ax(), e.y - ay())
        }
        context.addDestroy { d1(); d2() }
    }

    override fun draw(canvas: PlatformCanvas) {
        val text = fullText
        if (text.isEmpty()) return
        val infoList = lineInfos()

        if (anchorIndex >= 0 && focusIndex >= 0 && anchorIndex != focusIndex) {
            val selStart = min(anchorIndex, focusIndex)
            val selEnd = max(anchorIndex, focusIndex)
            for (info in infoList) {
                val ls = max(selStart, info.start)
                val le = min(selEnd, info.end)
                if (ls < le) {
                    val leftX = if (ls == info.start) 0f
                    else measureRichRange(info.start, ls)
                    val rightX = measureRichRange(info.start, le)
                    canvas.fillRect(
                        x = leftX,
                        y = info.y,
                        w = rightX - leftX,
                        h = info.height,
                        color = selectionColor
                    )
                }
            }
        }

        for (info in infoList) {
            var x = 0f
            var i = info.start
            while (i < info.end) {
                val sr = charStyleAt(i) ?: break
                val segEnd = min(info.end, sr.end)
                val segText = text.substring(i, segEnd)
                if (segText.isNotEmpty()) {
                    canvas.drawText(
                        segText, x, info.y + sr.fontSize,
                        sr.fontFamily, sr.fontWeight, sr.fontSize, sr.color
                    )
                    x += measureRichRange(i, segEnd)
                }
                i = segEnd
            }
        }
        super.draw(canvas)
    }
}
