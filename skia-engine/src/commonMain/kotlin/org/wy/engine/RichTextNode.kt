package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.lib.getValue
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.memo
import org.wy.signal.setValue
import kotlin.math.max
import kotlin.math.min

/**
 * 富文本节点：所有文本区域（含 WrappedTextNode / EditableTextNode）的基类。
 *
 * 文本选择能力放在基类，保证"所有文本区域都可选择"：
 * - 点击定位、拖拽/Shift 点击扩展选区
 * - 选区高亮绘制、选中文本/矩形读取
 * - 实现 Selectable，可被 SelectionManager 统一登记，享受全局快捷键
 *
 * 编辑器（EditableTextNode）在此之上叠加光标、输入、撤销等能力。
 */
open class RichTextNode(
    context: StateHolder<Node,List<Node>>
) : RectNode(context), Selectable {

    open val spans: List<RichTextSpan> = emptyList()
    open val selectionColor: ColorInt = rgba(100, 100, 200, 60)

    protected val fullText by memo { spans.joinToString("") { it.text } }

    open val autoWidth = false
    open val maxLines: Int = Int.MAX_VALUE
    open val ellipsis: String = "\u2026"
    open val textAlign: TextAlign = TextAlign.START

    protected val paragraph by memo {
        if (fullText.isEmpty()) return@memo null
        else buildParagraph(
            spans,
            if (autoWidth) Float.MAX_VALUE else innerWidth,
            maxLines,
            ellipsis,
            textAlign
        )
    }

    override val argWidth: LayoutSize
        get() = if (autoWidth) LayoutSize(paragraph?.width ?: 0f, true) else super.argWidth

    override val argHeight: LayoutSize
        get() {
            val p = paragraph
            val h = p?.height ?: (maxFontSizeInSpans() * 1.4f)
            return LayoutSize(h, true)
        }

    private fun maxFontSizeInSpans(): Float {
        var maxFs = 0f
        for (span in spans) {
            maxFs = max(maxFs, span.style.fontSize)
        }
        return max(maxFs, 1f)
    }

    // ---------- 文本选择（所有文本区域共用） ----------

    protected var anchorIndex by createSignal(-1)
    protected var focusIndex by createSignal(-1)
    private var dragging by createSignal(false)
    private var capture: PointerCapture? = null

    protected val selStart: Int
        get() = min(anchorIndex, focusIndex).coerceAtLeast(0)
    protected val selEnd: Int
        get() = max(anchorIndex, focusIndex).coerceIn(0, fullText.length)
    protected val hasSel: Boolean
        get() = anchorIndex >= 0 && focusIndex >= 0 && anchorIndex != focusIndex

    override val hasSelection: Boolean get() = hasSel

    /** 选中文本内容（无选区时为空串） */
    val selectedText: String
        get() = if (hasSel) fullText.substring(selStart, selEnd) else ""

    /** 选中区域（绝对坐标），供输入法定位 / 业务 overlay 使用 */
    val selectionRect: RectF?
        get() {
            if (!hasSel) return null
            val p = paragraph ?: return null
            val rects = p.getRectsForRange(selStart, selEnd, RectStyle.TIGHT)
            if (rects.isEmpty()) return null
            val first = rects.first()
            val last = rects.last()
            val left = absoluteX + paddingInlineStart + minOf(first.left, last.left)
            val top = absoluteY + paddingBlockStart + minOf(first.top, last.top)
            val right = absoluteX + paddingInlineStart + maxOf(first.right, last.right)
            val bottom = absoluteY + paddingBlockStart + maxOf(first.bottom, last.bottom)
            return RectF(left, top, right, bottom)
        }

    // Selectable 接口实现
    override fun selectionText(): String? = selectedText.ifEmpty { null }

    override fun selectionRect(): RectF? = this.selectionRect

    override fun setSelected(selected: Boolean) {
        if (!selected && hasSel) focusIndex = anchorIndex
    }

    override fun selectAll() {
        anchorIndex = 0
        focusIndex = fullText.length
    }

    /**
     * 文本拖拽选区：按下（onPointerDownCapture 置 dragging=true）并捕获指针，
     * 之后 Move / Up 事件只投递给捕获回调，即使指针拖出节点范围也有效。
     */
    init {
        context.addDestroy {
            capture?.release()
            capture = null
        }
    }

    override fun onPointerDownCapture(e: PointerEvent) {
        super.onPointerDownCapture(e)
        val p = paragraph ?: return
        val pos = p.getGlyphPositionAtCoordinate(e.x - paddingInlineStart, e.y - paddingBlockStart)
        if (engineGlobal.shift && anchorIndex >= 0) {
            focusIndex = pos.coerceIn(0, fullText.length)
        } else {
            anchorIndex = pos.coerceIn(0, fullText.length)
            focusIndex = anchorIndex
        }
        dragging = true
        capture?.release()
        capture = engineGlobal.capturePointer(
            id = e.id,
            onMove = { me ->
                if (!dragging) return@capturePointer
                val pp = paragraph ?: return@capturePointer
                focusIndex = pp.getGlyphPositionAtCoordinate(
                    me.rootX - absoluteX - paddingInlineStart,
                    me.rootY - absoluteY - paddingBlockStart
                ).coerceIn(0, fullText.length)
            },
            onUp = { dragging = false }
        )
    }

    override fun draw(canvas: PlatformCanvas) {
        val p = paragraph
        if (p != null) {
            if (hasSel) {
                for (rect in p.getRectsForRange(selStart, selEnd, RectStyle.TIGHT)) {
                    canvas.fillRect(
                        rect.left + paddingInlineStart,
                        rect.top + paddingBlockStart,
                        rect.width,
                        rect.height,
                        selectionColor
                    )
                }
            }
            canvas.drawParagraph(p, paddingInlineStart, paddingBlockStart)
        }
        super.draw(canvas)
    }
}
