package org.wy.engine


/**
 * 布局依赖链诊断（纯观测层，不改变任何求值语义）。
 *
 * ## 为什么放在这里
 *
 * 本引擎是**通用的信号驱动求值网络**，并不是"只支持 flex"：
 * `argHeight` 依赖 `argWidth`（由宽度决定高度）完全合法，反向依赖也合法。
 * 唯一被禁止的是**依赖成环**。
 *
 * 全仓库唯一能让"子节点向父级要尺寸"发生的地方是 [sizeFromParent]，
 * 因此这里记录到的 `(节点, 方向)` 进出顺序，就是一条真实的依赖链。
 *
 * ## 与 signal 的关系（重要）
 *
 * 本文件**不修改、不依赖** `signal` 的检测逻辑，只在其之外**补充上下文**。
 *
 * 成环的形态是 `父级 cache → 子.outerSize → 子.sizeFromParent → 父级 cache`，
 * 环闭合在**memo** 上，所以任何一个 `(节点,方向)` 都不会二次进入 ——
 * 靠本地栈无法判定成环，真正发现环的仍是 `signal.Memo.checkEnter`。
 * 因此这里的职责不是"替代检测"，而是：
 *
 * 1. **在 signal 抛错时补上它看不到的东西**：谁在向谁要尺寸、整条链是什么、
 *    根因与修法。signal 只知道"某个 memo 被重复进入"。
 * 2. **独立判环**（兜底）：若同 `(节点,方向)` 真的二次进入，直接给
 *    [LayoutCycleError]，不依赖 signal 的参与。
 *
 * ## 开销
 *
 * 正常路径只有一次列表 push/pop 与一次小扫描；字符串仅在异常路径构造。
 */
internal object LayoutDep {

    /** 依赖栈的一条边：某节点在某个方向上向其父级索要尺寸。 */
    private class Edge(
        val node: LayoutNode,
        val direction: Direction,
        val parent: LayoutNode?,
    )

    /**
     * 当前正在求值的依赖边。
     *
     * 与 `signal` 的 `stackMemos` 一致，沿用"布局求值为单线程"这一既有约定。
     */
    private val stack = ArrayList<Edge>(32)

    /** 诊断开关：置 false 可完全关闭追踪（仅保留原有 signal 的兜底检测）。 */
    var enabled: Boolean = true

    // ── 探测模式（probe）────────────────────────────────────
    //
    // 审计一棵**已构建**的树时，不能真让它跑完布局——环会立刻炸掉。
    // 探测模式让 sizeFromParent **只记录这条依赖边、不再递归**，
    // 于是整棵树可以安全地测量一遍，拿到完整的依赖图。
    //
    // 判定成环不再依赖"跑出异常"，而是跑完后在依赖图上找环。
    //
    // 判据是**纯观测**的，不复制引擎规则：
    // - ASK 边：子节点向父级索要尺寸（在 sizeFromParent 处记录）
    // - READ 边：父级测量时读取子节点尺寸（在布局的 outerSize 处记录）
    // 同一 `(父级, 子节点, 方向)` 三元组同时出现在两个集合中 → 成环。
    // 这样 grow 分支、alignFix / alignItem 等分支语义由引擎自己保证，
    // auditor 无需复述，也就不可能与引擎行为不一致。

    /** 探测模式记录的边。 */
    private val probeEdges = LinkedHashSet<ProbeEdge>()

    /** 探测模式开关。 */
    var probeMode: Boolean = false

    /**
     * 一条边：[child] 在 [direction] 方向上与 [parent] 的尺寸相关。
     *
     * 出现在两个集合中时含义不同：
     * - ASK 集合：child **向** parent 索要尺寸（child → parent）
     * - READ 集合：parent **读取** child 的尺寸（parent → child）
     */
    data class ProbeEdge(
        val child: LayoutNode,
        val direction: Direction,
        val parent: LayoutNode,
    )

    /** ASK 边：子节点向父级索要尺寸。 */
    private val probeAsks = LinkedHashSet<ProbeEdge>()

    /** READ 边：父级测量时读取子节点尺寸。 */
    private val probeReads = LinkedHashSet<ProbeEdge>()

    /**
     * 探测模式下的 [sizeFromParent] 替身：记录 ASK 边并返回占位尺寸，**不递归**。
     *
     * 占位值取 0：审计只关心依赖拓扑，不关心具体像素。
     */
    fun probeTrack(node: LayoutNode, direction: Direction, parent: LayoutNode): LayoutSize {
        probeAsks.add(ProbeEdge(node, direction, parent))
        return LayoutSize(0f, false)
    }

    /** 记录 READ 边：父级在测量中读取了子节点的尺寸。 */
    fun probeRead(parent: LayoutNode, child: LayoutNode, direction: Direction) {
        probeReads.add(ProbeEdge(child, direction, parent))
    }

    /** 取走探测模式收集到的边（并清空）。 */
    fun takeProbeEdges(): Pair<List<ProbeEdge>, List<ProbeEdge>> {
        val asks = probeAsks.toList()
        val reads = probeReads.toList()
        probeEdges.clear()
        probeAsks.clear()
        probeReads.clear()
        return asks to reads
    }

    /**
     * 进入一条依赖边并执行 [block]。
     *
     * - 同 `(节点,方向)` 二次进入 → 直接抛 [LayoutCycleError]（本地即可判定）；
     * - [block] 内抛出任何异常 → 补上依赖链上下文后原样重抛。
     */
    fun <T> track(node: LayoutNode, direction: Direction, block: () -> T): T {
        if (!enabled) return block()

        val repeatedAt = stack.indexOfFirst { it.node === node && it.direction == direction }
        if (repeatedAt >= 0) {
            throw LayoutCycleError(cycleReport(node, direction, repeatedAt))
        }

        val parent = node.layoutParent
        stack.add(Edge(node, direction, parent))
        try {
            return block()
        } catch (e: Throwable) {
            // 成环时环闭合在 memo 上，本地栈看不出重复，只能在此补充上下文。
            // 栈深为 1 也可能是环（如父级 cache → 子.sizeFromParent → 父级 cache），
            // 因此只要在追踪中且尚未包装过，就补上下文。
            //
            // 必须用 LayoutCycleError（继承 Error）而非 LayoutError：
            // RectNode.argPosition 会捕获 LayoutError 并降级为日志，那种异常会被吞掉。
            if (e !is LayoutCycleError) {
                throw enrich(e)
            }
            throw e
        } finally {
            stack.removeAt(stack.size - 1)
        }
    }

    /** 给任意布局异常补上"发生在哪条依赖链上"的上下文。 */
    private fun enrich(e: Throwable): Throwable {
        val chain = chainText(repeatIndexOfTop())
        val top = stack.last()
        return LayoutCycleError(
            buildString {
                append(e.message ?: e::class.simpleName.orEmpty())
                append("\n\n")
                append("── 布局依赖上下文 ──────────────────────────────\n")
                append("  依赖链：").append(chain).append('\n')
                append("  最后向父级索要尺寸：").append(describe(top.node, top.direction))
                if (top.parent != null) {
                    append("（其父级 ").append(simpleName(top.parent)).append("）")
                }
                append('\n')
                append("  含义：该节点的尺寸来自父级分配，而父级此刻正在测量它 —— ")
                append("尺寸与测量互相依赖，求值顺序成环。\n")
                append("  修法（二选一）：\n")
                append("    1) 让该节点有确定尺寸（不依赖父级）。宽度由内容测量得出时，\n")
                append("       写成 LayoutSize(测得宽度, false)；高度同理。\n")
                append("    2) 让该节点成为 grow 子节点，由父级分配尺寸，父级便不会回读它：\n")
                append("       object : …, GrowChild { override fun argGrow(d) = 1f }")
            }
        )
    }

    /** 同 `(节点,方向)` 重复进入时（本可本地判环）的完整报告。 */
    private fun cycleReport(node: LayoutNode, direction: Direction, repeatedAt: Int): String {
        val top = stack.last()
        return buildString {
            append("布局依赖成环：")
            append(describe(node, direction))
            append(" 的尺寸依赖其父级，而父级正在测量它。\n\n")
            append("── 布局依赖上下文 ──────────────────────────────\n")
            append("  依赖链：").append(chainText(repeatedAt)).append('\n')
            append("  环起点：").append(describe(stack[repeatedAt].node, stack[repeatedAt].direction))
            append('\n')
            append("  最后向父级索要尺寸：").append(describe(top.node, top.direction))
            if (top.parent != null) append("（其父级 ").append(simpleName(top.parent)).append("）")
            append('\n')
            append("  含义：同一节点的同一方向被二次索要，求值顺序成环。\n")
            append(hint())
        }
    }

    private fun hint(): String = buildString {
        append("  修法（二选一）：\n")
        append("    1) 让该节点有确定尺寸（不依赖父级）。宽度由内容测量得出时，\n")
        append("       写成 LayoutSize(测得宽度, false)；高度同理。\n")
        append("    2) 让该节点成为 grow 子节点，由父级分配尺寸，父级便不会回读它：\n")
        append("       object : …, GrowChild { override fun argGrow(d) = 1f }\n")
        append("  排查顺序（「没有子节点的尺寸」这类 LayoutError 的真凶常常是父容器）：\n")
        append("    a) 父容器带 children 却没指定 layout —— 给它一个 flex 布局；\n")
        append("    b) 子节点在该轴上没有自身尺寸 —— 给显式尺寸，或让父容器拉伸分配；\n")
        append("    c) 该子节点本就不该参与布局 —— 标 notInLayout。")
    }

    /** 渲染依赖链；[from] 为环起点索引（-1 表示无重复，仅列出当前链）。 */
    private fun chainText(from: Int): String = buildString {
        for (i in stack.indices) {
            val e = stack[i]
            if (i == from) append('[')
            append(describe(e.node, e.direction))
            if (e.parent != null) append(" → ").append(simpleName(e.parent))
            if (i == from) append(']')
            if (i != stack.size - 1) append(" → ")
        }
    }

    /** 栈顶若与某个更早的同节点同方向项重复，返回其首次出现索引，否则 -1。 */
    private fun repeatIndexOfTop(): Int {
        if (stack.isEmpty()) return -1
        val top = stack.last()
        return stack.indexOfFirst { it.node === top.node && it.direction == top.direction }
    }

    /** 节点的可读描述：优先业务名（[LayoutNode.debugName]），回落类名 + 父内序号。 */
    private fun describe(node: LayoutNode?, direction: Direction): String {
        if (node == null) return "（无父级）"
        val name = node.debugName ?: simpleName(node)
        val idx = try {
            "#" + node.layoutIndex
        } catch (_: Throwable) {
            "#?"
        }
        val dir = if (direction == Direction.x) "宽" else "高"
        return "$name$idx($dir)"
    }

    private fun simpleName(node: LayoutNode): String =
        node::class.simpleName ?: "Node"
}

/**
 * 布局依赖成环错误。
 *
 * 刻意不继承 `com.wy.layout.LayoutError`：`RectNode.argPosition` 会捕获
 * `LayoutError` 并降级为日志，本错误需要向上抛出才能被开发期看到。
 */
class LayoutCycleError(message: String) : Error(message)