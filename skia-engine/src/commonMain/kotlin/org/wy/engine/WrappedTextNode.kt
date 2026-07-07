package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.engine.layout.LayoutSize
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.memo
import org.wy.signal.setValue
import kotlin.math.max
import kotlin.math.min

/**
 * 换行策略，灵感来自 CSS word-break / overflow-wrap 与 harfbuzz-kmp 的 WordBreak。
 *
 * - PHRASE:   只允许在 UAX #14 定义的断行机会处断行。
 *             如果整行放不下一个单词，让单词溢出容器（对应 CSS overflow-wrap: normal）。
 * - BREAK_WORD: 优先 UAX #14 断行机会，长单词按字符边界截断（对应 CSS overflow-wrap: break-word）。
 *               Compose Text / Android StaticLayout 的默认行为。
 * - ANY_CHAR:   任意字符均可断行（对应 CSS word-break: break-all，CJK 排版风格）。
 */
enum class WordBreak {
    PHRASE, BREAK_WORD, ANY_CHAR
}

open class WrappedTextNode(
    context: StateHolder<Node>
) : RectNode(context) {
    open val text: String = ""
    open val fontFamily: String? = null
    open val fontSize: Float = 16f
    open val fontWeight: Int = 400
    open val color: ColorInt = rgba(0, 0, 0)
    open val selectionColor: ColorInt = rgba(0, 100, 200, 60)
    open val lineHeight: Float
        get() = fontSize * 1.4f
    open val wrappingWidth: Float = Float.MAX_VALUE
    open val wordBreak: WordBreak = WordBreak.BREAK_WORD
    open val locale: String? = null

    protected data class TextLine(val start: Int, val end: Int)

    private fun isSpace(ch: Char) = ch == ' ' || ch == '\t'

    private fun measureWidth(text: String, fontFamily: String?, fontWeight: Int, fontSize: Float) =
        PlatformCanvas.measureText(text, fontFamily, fontWeight, fontSize)

    private fun measureWidth(text: String, start: Int, end: Int, fontFamily: String?, fontWeight: Int, fontSize: Float) =
        if (start >= end) 0f else PlatformCanvas.measureText(
            text.substring(start, end), fontFamily, fontWeight, fontSize
        )

    private fun TextLine.width(): Float =
        measureWidth(text, start, end, fontFamily, fontWeight, fontSize)

    private val lines = memo {
        if (text.isEmpty()) return@memo emptyList()
        val maxW = wrappingWidth
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
        val segmentText = text.substring(segStart, segEnd)
        val breaks = lineBreakOpportunities(segmentText, locale)
            .map { it + segStart }
            .filter { it > segStart && it < segEnd }

        var lineStart = segStart
        while (lineStart < segEnd) {
            while (lineStart < segEnd && isSpace(text[lineStart])) lineStart++
            if (lineStart >= segEnd) break

            if (wordBreak == WordBreak.ANY_CHAR) {
                var cx = 0f
                var ci = lineStart
                while (ci < segEnd) {
                    val cw = measureWidth(
                        text[ci].toString(), fontFamily, fontWeight, fontSize
                    )
                    if (cx + cw > maxW && ci > lineStart) break
                    cx += cw
                    ci++
                }
                out.add(TextLine(lineStart, ci))
                lineStart = ci
                continue
            }

            val lastFitIdx: Int? = breaks.lastOrNull { bp ->
                bp > lineStart && measureWidth(text, lineStart, bp, fontFamily, fontWeight, fontSize) <= maxW
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
                    val w = measureWidth(text, lineStart, mid, fontFamily, fontWeight, fontSize)
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

    private val charPositions = memo {
        val lineList = lines()
        val result = mutableListOf<Pair<Float, Float>>()
        for ((li, line) in lineList.withIndex()) {
            var x = 0f
            for (i in line.start until line.end) {
                result.add(Pair(x, li * lineHeight))
                x += measureWidth(text[i].toString(), fontFamily, fontWeight, fontSize)
            }
        }
        result
    }

    private fun charAt(px: Float, py: Float): Int {
        val lineList = lines()
        if (lineList.isEmpty()) return 0
        val lineIdx = (py / lineHeight).toInt().coerceIn(0, lineList.size - 1)
        val line = lineList[lineIdx]

        var low = line.start
        var high = line.end
        while (low < high) {
            val mid = (low + high) / 2
            val w = measureWidth(text, line.start, mid + 1, fontFamily, fontWeight, fontSize)
            if (w <= px) low = mid + 1 else high = mid
        }
        return low.coerceIn(line.start, line.end)
    }

    override fun size(direction: Direction): LayoutSize {
        val lineList = lines()
        return when (direction) {
            Direction.x -> if (wrappingWidth < Float.MAX_VALUE)
                LayoutSize(wrappingWidth, false)
            else
                LayoutSize(lineList.firstOrNull()?.width() ?: 0f, true)
            Direction.y -> LayoutSize(
                max(lineList.size * lineHeight, fontSize * 1.4f), true
            )
        }
    }

    private var anchorIndex by createSignal(-1)
    private var focusIndex by createSignal(-1)

    val selectionText: String?
        get() {
            if (anchorIndex < 0 || focusIndex < 0 || anchorIndex == focusIndex) return null
            val s = min(anchorIndex, focusIndex)
            val e = max(anchorIndex, focusIndex)
            return text.substring(s, e)
        }

    private var onMouseDown = false

    override fun mouseDown(e: MouseEvent) {
        anchorIndex = charAt(e.x, e.y)
        focusIndex = anchorIndex
        onMouseDown = true
        e.stopPropagation()
    }

    init{
        val engineGlobal =context. consume(engineGlobalContext)!!
        val d1 = engineGlobal.registerMouseUp { _, _ -> onMouseDown = false }
        val ax = memo { absolutePosition(Direction.x) }
        val ay = memo { absolutePosition(Direction.y) }
        val d2 = engineGlobal.registerMouseMove { x, y ->
            if (onMouseDown) focusIndex = charAt(x - ax(), y - ay())
        }
       context.addDestroy { d1(); d2() }
    }

    override fun drawSelf(canvas: PlatformCanvas) {
        if (text.isEmpty()) return
        val lineList = lines()
        val h = lineHeight

        if (anchorIndex >= 0 && focusIndex >= 0 && anchorIndex != focusIndex) {
            val selStart = min(anchorIndex, focusIndex)
            val selEnd = max(anchorIndex, focusIndex)
            for ((li, line) in lineList.withIndex()) {
                val ls = max(selStart, line.start)
                val le = min(selEnd, line.end)
                if (ls < le) {
                    val leftX = if (ls == line.start) 0f
                    else measureWidth(text, line.start, ls, fontFamily, fontWeight, fontSize)
                    val rightX = measureWidth(text, line.start, le, fontFamily, fontWeight, fontSize)
                    canvas.drawRect(x = leftX, y = li * h, w = rightX - leftX, h = h, color = selectionColor)
                }
            }
        }

        for ((li, line) in lineList.withIndex()) {
            val lineText = text.substring(line.start, line.end)
            if (lineText.isNotEmpty()) {
                canvas.drawText(lineText, 0f, li * h + fontSize, fontFamily, fontWeight,
                    fontSize = fontSize, color = color)
            }
        }
    }
}
