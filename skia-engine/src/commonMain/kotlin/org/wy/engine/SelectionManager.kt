package org.wy.engine

import com.wy.mve.Context
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.memo
import org.wy.signal.setValue

/**
 * Selectable：可被 SelectionManager 统一管理的可选文本区域接口。
 *
 * 对标 Flutter 的 Selectable / SelectionRegistrar 模型，但分发方向相反（拉模型）：
 * 协调者不向节点推送范围，也不提供任何"设置选区"的命令——选区是原始信号的纯函数，
 * 各节点按需读取自己被分配的 [SelectionManager.rangeOf] 并绘制高亮。
 *
 * 业务层只需实现此接口（或直接使用 RichTextNode 系），自动享受：
 * - Cmd+A 全选 / Cmd+C 复制等统一快捷键
 * - 跨组件拖拽选择与 selectedText 聚合读取
 */
interface Selectable {

    /** 选中区域（全局坐标），供 popover 定位等使用；无选区返回 null。 */
    fun selectionRect(): RectF?

    /** 文本总长度（越界钳制 / 全选用）。 */
    val textLength: Int

    /** 全局坐标 → 本节点文本偏移（跨节点拖拽时由目标节点换算）。 */
    fun positionForPoint(globalX: Float, globalY: Float): Int

    /** 读取指定范围文本（selectedText 聚合拼接用）。 */
    fun textInRange(start: Int, end: Int): String

    /**
     * 包含 [offset] 的词边界（半开区间 [start, end)），供双击选词；无法分词返回 null。
     * 默认不支持分词（纯展示 mock 等），RichTextNode 系由段落布局提供。
     */
    fun wordRangeAt(offset: Int): Pair<Int, Int>? = null

    /**
     * 包含 [offset] 的逻辑段落范围（半开区间），段落以 '\n' 分隔，供三击选段。
     * 默认不支持（返回 null）。
     */
    fun paragraphRangeAt(offset: Int): Pair<Int, Int>? = null

    fun cut() {}
    fun copy() {}
    fun paste() {}
}

/**
 * 双击后按住拖拽的词级扩展（纯函数）：锚词 [anchorStart, anchorEnd) 固定，
 * 目标偏移在锚词之外时，对应端吸附到目标处整词的边界；无分词能力时退化为字符粒度。
 */
internal fun expandWordSelection(
    anchorStart: Int,
    anchorEnd: Int,
    target: Int,
    wordRangeAt: (Int) -> Pair<Int, Int>?
): Pair<Int, Int> = when {
    target < anchorStart -> (wordRangeAt(target)?.first ?: target) to anchorEnd
    target > anchorEnd -> anchorStart to (wordRangeAt(target)?.second ?: target)
    else -> anchorStart to anchorEnd
}

/** 选区端点：某个可选节点内的偏移位置。 */
internal data class SelPoint(val node: Selectable, val offset: Int)

/** 一对端点：锚点与焦点（编辑器本地光标也以此形态暴露给派生链）。 */
internal data class SelPair(val anchor: SelPoint, val focus: SelPoint)

/**
 * SelectionManager：全局唯一的选中协调者（信号驱动，零命令式状态）。
 *
 * "什么地方被选中了"完全由初始原因**计算**得出，本类没有任何 set 选区的入口：
 * - **指针会话**：[PointerSelect]（press/release 快照）+ [EngineGlobal.moveHitTest]
 *   推导两端。按住中焦点跟随移动；release 填入即定格（hover 自然失效）；
 *   press 落在可选文本之外 → 整个选区为空（浏览器语义：点击空白清除）；
 *   Shift 扩展的锚点继承由引擎在 mouseDown 时复用上一会话的 press 实现，
 *   无需任何锚点记忆字段。
 * - **键盘选择**：活跃编辑器（[EngineGlobal.activeEditor]）的光标信号
 *   （anchor/focus）直接参与派生——编辑器只维护自己的光标，无同步调用。
 * - **程序化指令**（Cmd+A 全选、clear）：一次性脉冲事件物化为一个信号
 *   [programmatic]，任何更高优先级的来源出现即自动失效。
 *
 * 各节点的范围分配（[rangeOf]）是端点与注册表的纯派生（memo），无反向写回：
 * 没有 applySelection 推送、没有本地副本，"谁被选中"只有这一份真相。
 */
class SelectionManager(
    private val g: EngineGlobal? = null,
    /** headless 测试专用：脱离渲染树的孤立可选节点补充清单（生产恒为 null）。 */
    private val extraSelectables: (() -> List<Selectable>)? = null
) {

    // ---------- 可选集合（纯派生，无命令式注册表） ----------

    /**
     * 当前可参与选择的节点集合（memo）：从渲染树根遍历计算。
     * 节点销毁（移出树）、隐藏（children 的 purifyList 过滤）即自动出局——
     * 不存在任何需要生命周期配对的注册/注销调用，从根上杜绝"过期注册表"。
     */
    private val selectables by memo {
        val list = mutableListOf<Selectable>()
        g?.rootNode?.let { collectSelectables(it, list) }
        extraSelectables?.invoke()?.let { list.addAll(it) }
        list
    }

    private fun collectSelectables(n: Node, out: MutableList<Selectable>) {
        for (child in n.children) {
            if (child.selectionEnabled) {
                if (child is Selectable) out.add(child)
                collectSelectables(child, out)
            }
            // selectionEnabled = false：整个子树剪枝（按钮内标签等声明性退出选择）
        }
    }

    /** 对象当前是否在可选集合内（活性 + selectionEnabled 子树声明的统一判定）。 */
    internal fun isSelectable(node: Any?): Boolean = selectables.any { it === node }

    /** 文档序快照（memo）：仅集合变化时重排，拖拽过程中零排序成本。 */
    private val sortedRegistry by memo {
        selectables.sortedWith { x, y -> compareDocumentOrder(x, y) }
    }

    // ---------- 派生源 ----------

    /** 命中链从深到浅找第一个"仍在树上"的可选文本节点，并把命中点换算为该节点的文本偏移。 */
    private fun pointFrom(hit: HitestResult?): SelPoint? {
        if (hit == null) return null
        for (i in hit.chain.indices.reversed()) {
            val node = hit.chain[i].node
            if (node is Selectable && isSelectable(node)) {
                // 快照链可能引用已销毁/隐藏的节点（如列表删除行后定格的选区），
                // 其布局坐标已失效，必须跳过，否则会触发过期 layoutIndex 的越界崩溃
                return SelPoint(node, node.positionForPoint(hit.x, hit.y).coerceIn(0, node.textLength))
            }
        }
        return null
    }

    /**
     * 指针会话推导端点对；press 落在可选文本之外返回 null（整个选区清空）。
     * - 按住中（release 未填）：焦点跟随 moveHitTest，尚未移动时退化为锚点；
     * - 已松手（release 已填）：焦点冻结在释放位置，hover 不再影响。
     */
    private fun pointerPair(s: PointerSelect): SelPair? {
        val a = pointFrom(s.press) ?: return null
        val f = if (s.release != null) pointFrom(s.release) else pointFrom(g?.moveHitTest)
        return SelPair(a, f ?: a)
    }

    /**
     * 当前生效端点对。优先级从高到低（后者被前者自动压制，无需失效命令）：
     * 1. 按住中的指针会话 —— 编辑器内拖拽同样由此驱动；
     * 2. 活跃编辑器的本地光标 —— 键盘选择 / 点击定位的真相源；
     * 3. 已定格的指针会话 —— 松手后保留，hover 不影响；
     * 4. 程序化会话 —— Cmd+A 等，任何交互发生即让位。
     */
    private val currentPair: SelPair?
        get() {
            val s = g?.pointerSelect
            if (s != null && s.release == null) return pointerPair(s)
            g?.activeEditor?.cursorSelPair?.let { return it }
            if (s != null) return pointerPair(s)
            return programmatic
        }

    /** 当前生效的锚点 / 焦点（供测试断言）。 */
    internal val anchorSel: SelPoint? get() = currentPair?.anchor
    internal val focusSel: SelPoint? get() = currentPair?.focus

    // ---------- 程序化指令的唯一落点 ----------

    /**
     * 一次性指令（Cmd+A / clear）的物化状态：脉冲事件没有持续信号可供推导，
     * 这是全类唯一可写的选择状态；任何指针/键盘/焦点来源出现即自动失效。
     */
    private var programmatic by createSignal<SelPair?>(null)
        private set

    // ---------- 范围分配（纯派生） ----------

    /**
     * 全表分配快照（memo）：按文档序把 [anchorSel, focusSel] 区间映射到各节点。
     * - 起点/终点同节点 → 局部范围；仅起点 → [offset, len]；仅终点 → [0, offset]
     * - 夹在中间的节点 → 全文；其余不出现
     * LinkedHashMap 保持文档序，供 selectedText 直接按序拼接。
     */
    private val ranges by memo {
        val map = LinkedHashMap<Selectable, Pair<Int, Int>>()
        val a = anchorSel ?: return@memo map
        val f = focusSel ?: a
        val forward = compareDocumentOrder(a.node, f.node) <= 0
        val first = if (forward) a else f
        val second = if (forward) f else a

        for (s in sortedRegistry) {
            val range = when {
                s === first.node && s === second.node ->
                    minOf(first.offset, second.offset) to maxOf(first.offset, second.offset)
                s === first.node -> first.offset to s.textLength
                s === second.node -> 0 to second.offset
                isInBetween(s, first.node, second.node) -> 0 to s.textLength
                else -> null
            }?.takeIf { it.second > it.first } ?: continue
            map[s] = range
        }
        map
    }

    /** 指定节点当前被分配的选中范围 [start, end)；null 表示未被选中。 */
    fun rangeOf(selectable: Selectable): Pair<Int, Int>? = ranges[selectable]

    private fun isInBetween(target: Selectable, n1: Selectable, n2: Selectable): Boolean =
        compareDocumentOrder(target, n1) > 0 && compareDocumentOrder(target, n2) < 0

    // ---------- 程序化指令（脉冲事件的物化入口） ----------

    /**
     * 全选：聚焦编辑器时全选其内部文本；否则从注册表首节点开头到末节点末尾。
     * 无注册节点时不做任何事。
     * 新指令物化即撤除旧指针会话（含定格），否则冻结端点会在派生链中压制本指令。
     */
    fun selectAll() {
        val ed = g?.activeEditor
        if (ed != null) {
            ed.selectAll()
            return
        }
        g?.pointerSelect = null
        val all = sortedRegistry
        if (all.isEmpty()) return
        programmatic = SelPair(SelPoint(all.first(), 0), SelPoint(all.last(), all.last().textLength))
    }

    /** 清除程序化选区。（指针定格选区由下一次按下自动覆盖清除。） */
    fun clear() {
        programmatic = null
    }

    /** 单节点内设置选区区间 [start, end)（编程式），见完整版 [select]。 */
    fun select(node: Selectable, start: Int, end: Int): Boolean =
        select(node, start, node, end)

    /**
     * 编程式设置任意选区区间（声明式写入初始原因，非反向同步）：
     * - 聚焦编辑器时路由为其内部选区（编辑器语境的唯一真相源是本地光标）；
     * - 否则物化为 programmatic 会话（先撤除旧指针会话保证生效），
     *   任何指针交互出现即自动让位，无需失效命令。
     *
     * @return 目标不可达（聚焦编辑器但区间指向其他节点 / 节点未注册）时返回 false
     */
    fun select(
        anchor: Selectable,
        anchorOffset: Int,
        focus: Selectable = anchor,
        focusOffset: Int = anchorOffset,
    ): Boolean {
        val ed = g?.activeEditor
        if (ed != null) {
            if (anchor !== ed || focus !== ed) return false
            ed.selectRange(anchorOffset, focusOffset)
            return true
        }
        val all = selectables
        if (all.none { it === anchor } || all.none { it === focus }) return false
        g?.pointerSelect = null
        programmatic = SelPair(
            SelPoint(anchor, anchorOffset.coerceIn(0, anchor.textLength)),
            SelPoint(focus, focusOffset.coerceIn(0, focus.textLength))
        )
        return true
    }

    // ---------- 聚合读取 ----------

    /** 选中的矩形区域（供 popover 定位）：取所有选中片段的包围盒。 */
    val selectedRect: RectF?
        get() {
            var result: RectF? = null
            for ((s, range) in ranges) {
                if (range.second <= range.first) continue
                val r = s.selectionRect() ?: continue
                result = if (result == null) r else RectF(
                    minOf(result.left, r.left),
                    minOf(result.top, r.top),
                    maxOf(result.right, r.right),
                    maxOf(result.bottom, r.bottom)
                )
            }
            return result
        }

    /** 是否存在选中 */
    val hasSelection: Boolean get() = selectedText != null

    /** 选中文本聚合：按文档序拼接各节点被分配片段。 */
    val selectedText: String?
        get() {
            val sb = StringBuilder()
            for ((s, range) in ranges) {
                sb.append(s.textInRange(range.first, range.second))
            }
            return sb.toString().ifEmpty { null }
        }
}

/**
 * 先序遍历文档序比较：this 在 other 之前返回负数。
 * 实现方式：分别取根→自身的父链路径，比较字典序；
 * 分叉处为兄弟节点，按其在父 children 中的索引定序。
 */
internal fun compareDocumentOrder(x: Selectable, y: Selectable): Int {
    if (x === y) return 0
    val nx = x as? Node ?: return 0
    val ny = y as? Node ?: return 0
    val px = pathFromRoot(nx)
    val py = pathFromRoot(ny)
    val n = minOf(px.size, py.size)
    for (i in 0 until n) {
        if (px[i] !== py[i]) {
            val siblings = px[i].parent?.children ?: return 0
            // 节点已被隐藏/销毁时不在 children 里（indexOfFirst 为 -1），
            // 回退到最后一次编号（indexValue），保证过期端点仍可稳定定序
            val ix = siblings.indexOfFirst { it === px[i] }.takeIf { it >= 0 } ?: px[i].indexValue
            val iy = siblings.indexOfFirst { it === py[i] }.takeIf { it >= 0 } ?: py[i].indexValue
            return ix - iy
        }
    }
    return px.size - py.size
}

private fun pathFromRoot(node: Node): List<Node> {
    val path = ArrayList<Node>()
    var cur: Node? = node
    while (cur != null) {
        path.add(cur)
        cur = cur.parent
    }
    path.reverse()
    return path
}

val selectionManagerContext = Context<SelectionManager?>(null)
