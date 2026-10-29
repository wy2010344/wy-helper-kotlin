package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.lib.getValue
import org.wy.signal.memo
import kotlin.math.max

/**
 * 富文本节点：所有文本区域（含 WrappedTextNode / EditableTextNode）的基类。
 *
 * 文本选择能力放在基类，保证"所有文本区域都可选择"：
 * - 节点是纯数据面：只负责把全局坐标换算成文本偏移（[positionForPoint]）、
 *   提供文本与矩形查询；
 * - 选区真相统一由 [SelectionManager] 持有（信号驱动拉模型），本节点通过
 *   [rangeOf] 派生读取自己被分配的范围并绘制高亮，没有任何反向写回。
 *
 * 编辑器（EditableTextNode）在此之上叠加光标、输入、撤销等能力，
 * 键盘选择写本地光标信号，由 [SelectionManager] 从活跃编辑器直接派生，无同步调用。
 */
open class RichTextNode(
    context: StateHolder<Node,List<Node>>
) : RectNode(context), Selectable {

    open val spans: List<RichTextSpan> = emptyList()
    open val selectionColor: ColorInt = rgba(100, 100, 200, 60)

    protected val fullText by memo { spans.joinToString("") { TabRender.expandTabs(it.text) } }

    open val autoWidth = false
    open val maxLines: Int = Int.MAX_VALUE
    open val ellipsis: String = "\u2026"
    open val textAlign: TextAlign = TextAlign.START

    /**
     * 单行不换行：段落按无限宽排版（永不产生软换行），绘制裁剪到内容盒。
     *
     * 与 [maxLines] 的区别是"截断策略"：`maxLines = 1` 仍然按盒宽折行、只保留首行
     * 并补省略号，行内断点位置仍然受盒宽影响；[noWrap] 则完全不折行，超出部分
     * 靠裁剪隐藏，配合 [textOffsetX] 做水平滚动——这才是输入框的单行语义。
     */
    protected open val noWrap: Boolean get() = false

    /** 单行溢出时的水平滚动量：绘制整体左移该值，保证光标可见。 */
    protected open val textOffsetX: Float get() = 0f

    /**
     * 无限宽排版下的行对齐平移量（对齐在绘制原点上补偿）。
     *
     * 段落按无限宽排版时若仍传 END/CENTER，SkParagraph 会把对齐烘进无穷宽坐标系，
     * 字形被推到无穷远处，超出内容盒裁剪后整列文字消失。因此无限宽统一按 START
     * 排版，再把对齐平移到 [textOriginX] 上——绘制、选区、命中、光标同源一致。
     */
    private val noWrapAlignShift: Float
        get() {
            if (autoWidth || !noWrap) return 0f
            val p = paragraph ?: return 0f
            return when (textAlign) {
                TextAlign.CENTER -> (innerWidth - p.width) / 2f
                TextAlign.END -> innerWidth - p.width
                else -> 0f
            }
        }

    /** 文本绘制原点 x：padding 起点左移 [textOffsetX]，再叠加无限宽对齐平移。 */
    protected val textOriginX: Float get() = paddingInlineStart - textOffsetX + noWrapAlignShift

    /**
     * 单行墨迹垂直居中补偿（光学居中）。
     *
     * Skia 对小字号（≤16px）的段落行高有约 19px 的下限，且多出来的留白全部堆在字形上方，
     * 行盒垂直居中时墨迹会整体下沉 1~2px——这就是"按钮 / 标签文字没上下居中"的根因。
     * 单行文本按紧致墨迹盒把绘制原点平移到内容盒中心；多行保持自然行高（段落排版语义）。
     */
    private val inkShiftY by memo {
        if (!(autoWidth || noWrap || maxLines == 1)) return@memo 0f
        val p = paragraph ?: return@memo 0f
        if (fullText.isEmpty()) return@memo 0f
        val rects = p.getRectsForRange(0, fullText.length, RectStyle.TIGHT)
        if (rects.isEmpty()) return@memo 0f
        val top = rects.minOf { it.top }
        val bottom = rects.maxOf { it.bottom }
        (innerHeight - (bottom - top)) / 2f - top
    }

    /** 文本绘制原点 y：内容盒上缘 + 单行墨迹居中补偿。 */
    protected val textOriginY: Float get() = paddingBlockStart + inkShiftY

    protected val paragraph by memo {
        if (fullText.isEmpty()) return@memo null
        val expanded = spans.map {
            if (it.text.contains('\t')) it.copy(text = TabRender.expandTabs(it.text)) else it
        }
        buildParagraph(
            expanded,
            // 单行模式取无限宽：让"要不要折行"由 noWrap 决定，而不是由盒宽决定
            if (autoWidth || noWrap) Float.MAX_VALUE else innerWidth,
            maxLines,
            ellipsis,
            // 无限宽排版下行对齐会在无穷宽内计算，把字形推到盒外被裁掉；对齐改由 textOriginX 平移补偿
            if (autoWidth || noWrap) TextAlign.START else textAlign
        )
    }

    /** 在内容盒裁剪下执行绘制；非单行模式下等价于直接执行 [block]。 */
    protected inline fun drawInContentBox(
        canvas: PlatformCanvas,
        block: (PlatformCanvas) -> Unit
    ) {
        if (noWrap && !autoWidth) {
            canvas.save()
            canvas.clipRect(paddingInlineStart, paddingBlockStart, innerWidth, innerHeight)
            block(canvas)
            canvas.restore()
        } else {
            block(canvas)
        }
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

    /** 选区协调者：可为 null（脱离引擎环境）。 */
    protected val selectionManager: SelectionManager? =
        context.consume(selectionManagerContext)

    /** 协调者分配给本节点的选中范围 [start, end)；null 表示未被选中。 */
    protected val assignedRange: Pair<Int, Int>?
        get() = selectionManager?.rangeOf(this)

    protected val selStart: Int get() = assignedRange?.first ?: 0
    protected val selEnd: Int get() = assignedRange?.second ?: 0
    protected val hasSel: Boolean get() = assignedRange != null

    /** 是否有选中范围（协调者视角，供业务层查询）。 */
    val hasSelection: Boolean get() = hasSel

    /** 选中文本内容（无选区时为空串） */
    open val selectedText: String
        get() = if (hasSel) fullText.substring(selStart, selEnd) else ""

    /** 选中区域（绝对坐标），供输入法定位 / 业务 overlay 使用 */
    val selectionRect: RectF?
        get() {
            if (!hasSel) return null
            val p = paragraph ?: return null
            val rects = p.getRectsForRange(
                logicToDisplayIndex(selStart),
                logicToDisplayIndex(selEnd),
                RectStyle.TIGHT
            )
            if (rects.isEmpty()) return null
            val first = rects.first()
            val last = rects.last()
            val left = absoluteX + textOriginX + minOf(first.left, last.left)
            val top = absoluteY + textOriginY + minOf(first.top, last.top)
            val right = absoluteX + textOriginX + maxOf(first.right, last.right)
            val bottom = absoluteY + textOriginY + maxOf(first.bottom, last.bottom)
            return RectF(left, top, right, bottom)
        }

    // Selectable 接口实现

    override fun selectionRect(): RectF? = this.selectionRect

    override val textLength: Int get() = fullText.length

    // ---------- 显示域 ↔ 逻辑域 索引转换钩子 ----------

    /**
     * 段落里排的是"显示文本"，而光标、选区、编辑操作工作在"逻辑文本"上。
     * 默认两者恒等；EditableTextNode 在占位 / 掩码显示时覆写做换算。
     */
    protected open fun displayToLogicIndex(displayPos: Int): Int = displayPos
    protected open fun logicToDisplayIndex(logicPos: Int): Int = logicPos

    override fun positionForPoint(globalX: Float, globalY: Float): Int {
        val p = paragraph ?: return 0
        val displayPos = p.getGlyphPositionAtCoordinate(
            globalX - absoluteX - textOriginX,
            globalY - absoluteY - textOriginY
        ).coerceIn(0, fullText.length)
        return displayToLogicIndex(displayPos)
    }

    override fun textInRange(start: Int, end: Int): String =
        if (end > start) fullText.substring(start, end.coerceAtMost(fullText.length)) else ""

    override fun wordRangeAt(offset: Int): Pair<Int, Int>? {
        // 词边界统一到 Words（与 Ctrl+←/→ 导航同源），双击与词跳不再各用一套切分。
        // 空文本（含无占位）无词可选；占位 / 掩码经 displayToLogicIndex 塌缩。
        val t = fullText
        if (t.isEmpty()) return null
        return Words.wordRangeAt(t, offset)?.let { (a, b) ->
            displayToLogicIndex(a) to displayToLogicIndex(b)
        }
    }

    override fun paragraphRangeAt(offset: Int): Pair<Int, Int>? {
        val t = fullText
        if (t.isEmpty()) return null
        val d = logicToDisplayIndex(offset).coerceIn(0, t.length)
        // '\n' 分隔的逻辑段落；点在换行符上归前段，末段延伸到文末
        val start = if (d == 0) 0 else t.lastIndexOf('\n', d - 1) + 1
        val end = t.indexOf('\n', d).let { if (it == -1) t.length else it }
        return displayToLogicIndex(start) to displayToLogicIndex(end)
    }

    // ---------- 链接（RichTextSpan.url 标记的片段） ----------

    /** url 链接区间列表（偏移按 spans 文本累计，[IntRange] 为闭区间）。 */
    private val linkRanges by memo {
        val list = mutableListOf<Pair<IntRange, String>>()
        var offset = 0
        for (span in spans) {
            if (span.text.isNotEmpty() && span.url != null) {
                list.add(IntRange(offset, offset + span.text.length - 1) to span.url)
            }
            offset += span.text.length
        }
        list
    }

    /** 命中 [offset] 的链接 url；未命中返回 null。 */
    open fun linkAt(offset: Int): String? =
        linkRanges.firstOrNull { offset in it.first }?.second

    override fun onPointerClick(e: PointerEvent) {
        super.onPointerClick(e)
        // positionForPoint 期望全局坐标；e.x/e.y 是相对当前节点的局部坐标（随命中链换算），
        // 用局部坐标会让链接命中偏移 / 打开错误的 URL。改用 rootX/rootY。
        linkAt(positionForPoint(e.rootX, e.rootY))?.let { UrlOpener.open(it) }
    }

    // 可选集合由 SelectionManager 从渲染树遍历派生，节点无需注册/注销；
    // 销毁（移出树）与隐藏（purifyList 过滤）即自动退出分配与聚合。

    override fun draw(canvas: PlatformCanvas) {
        val p = paragraph
        if (p != null) {
            drawInContentBox(canvas) {
                if (hasSel) {
                    for (rect in p.getRectsForRange(
                        logicToDisplayIndex(selStart),
                        logicToDisplayIndex(selEnd),
                        RectStyle.TIGHT
                    )) {
                        it.fillRect(
                            rect.left + textOriginX,
                            rect.top + textOriginY,
                            rect.width,
                            rect.height,
                            selectionColor
                        )
                    }
                }
                it.drawParagraph(p, textOriginX, textOriginY)
            }
        }
        super.draw(canvas)
    }
}
