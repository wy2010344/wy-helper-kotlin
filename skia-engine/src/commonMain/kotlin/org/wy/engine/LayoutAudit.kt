package org.wy.engine

/**
 * 布局依赖审计（runtime audit）。
 *
 * ## 为什么不是"静态 linter"
 *
 * 本框架的节点是 `argChildren()` 里的**匿名对象**，运行时由信号驱动创建：
 *
 * ```kotlin
 * override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
 *     dRow(gap = 8f) { dTabButton(...) }   // 编译期不存在这棵树
 * }
 * ```
 *
 * 编译期没有树，AST 分析无从下手。**源码级静态分析在��框架里做不到。**
 *
 * ## 真正的做法：运行时审计（runtime audit）
 *
 * 树在 `argChildren()` 执行后就是**具体的对象图**——有真实的 `layoutParent`、
 * `layoutIndex`、真实的布局属性。因此可以对**已构建的树**做检查。
 *
 * 这与"撞一个修一个"的本质区别：
 *
 * | | 崩溃驱动 | 运行时审计 |
 * |---|---|---|
 * | 发现时机 | 渲染到那一条路径才炸 | 一次跑出**全部**违规点 |
 * | 覆盖 | 取决于这次渲染走到哪 | 整棵树（可遍历） |
 * | 成本 | 每次一个编译+渲染周期 | 一次遍历 |
 *
 * ## 怎么在不崩溃的前提下拿到依赖图
 *
 * 成环的形态是 `父级 cache → 子.outerSize → 子.sizeFromParent → 父级 cache`。
 * 若真让它跑，环闭合处立刻抛异常，只能拿到**第一个**环。
 *
 * [LayoutDep] 的**探测模式**把这一步改成「记录依赖边、不再递归」：
 * `sizeFromParent` 返回占位尺寸而不回调父级，于是整棵树可以安全测量一遍，
 * 拿到完整的依赖图。判环退化为**图上的环检测**——纯算法，不依赖运行时异常。
 *
 * ## 用法
 *
 * ```kotlin
 * val findings = LayoutAudit.audit(root)      // 遍历 + 探测 + 判环
 * findings.forEach { println(it.report()) }
 * ```
 *
 * 审计只读，不改变任何布局结果；探测模式结束后自动恢复。
 */
object LayoutAudit {

    /** 一条违规：某个节点的尺寸依赖其父级，而父级反过来又依赖该节点的尺寸。 */
    data class Finding(
        /** 成环涉及的节点（子节点侧）。 */
        val node: LayoutNode,
        /** 成环涉及的父级。 */
        val parent: LayoutNode,
        /** 节点索要尺寸的方向。 */
        val direction: Direction,
        /** 环上的依赖边（按求值顺序）。 */
        val cycle: List<Edge>,
    ) {

        /** 依赖边：child 在某方向上向 parent 索要尺寸。 */
        data class Edge(
            val child: LayoutNode,
            val parent: LayoutNode,
            val direction: Direction,
        )

        /** 人类可读的报告：定位到业务名、方向、根因与修法。 */
        fun report(): String = buildString {
            append("布局依赖成环：")
            append(describe(node))
            append(" 的").append(dirName(direction))
            append("依赖父级 ")
            append(describe(parent))
            append("，而父级测量时又回读了它。\n")
            append("  依赖链：")
            cycle.forEachIndexed { i, e ->
                if (i > 0) append(" → ")
                append(describe(e.child)).append("→").append(describe(e.parent))
            }
            append("  ← 环在此闭合\n")
            append("  含义：该节点未提供确定尺寸，沿 argSize 回落到 sizeFromParent，")
            append("而其父级正在测量时又读回了它。\n")
            append("  修法（二选一）：\n")
            append("    1) 让该节点有确定尺寸（不依赖父级）。宽度由内容测量得出时，\n")
            append("       写成 LayoutSize(测得宽度, false)；高度同理。\n")
            append("    2) 让该节点成为 grow 子节点，由父级分配尺寸，父级便不会回读它：\n")
            append("       object : …, GrowChild { override fun argGrow(d) = 1f }")
        }

        private fun dirName(d: Direction): String = if (d == Direction.x) "宽度" else "高度"

        private fun describe(n: LayoutNode): String {
            val name = n.debugName ?: (n::class.simpleName ?: "Node")
            val idx = try {
                "#" + n.layoutIndex
            } catch (_: Throwable) {
                "#?"
            }
            return "$name$idx"
        }
    }

    /**
     * 审计一棵已构建的树，返回全部成环点。
     *
     * @param root 树根（通常是 Renderer / 顶层容器）。
     * @param maxFindings 最多返回多少条（避免超大树的报告刷屏；默认不限）。
     */
    fun audit(root: Node, maxFindings: Int = Int.MAX_VALUE): List<Finding> {
        // 1) 遍历树，收集所有 LayoutNode（布局节点才有父子尺寸关系）
        val nodes = collectLayoutNodes(root)

        // 2) 探测模式测量：记录 ASK / READ 两条边而不递归，环不会炸
        val (asks, reads) = measureAndCollectEdges(nodes)

        // 3) 配对找环
        return findCycles(asks, reads, maxFindings)
    }

    /** 遍历树，收集所有布局节点。 */
    private fun collectLayoutNodes(root: Node): List<LayoutNode> {
        val out = ArrayList<LayoutNode>()
        fun walk(n: Node) {
            if (n is LayoutNode) out.add(n)
            // children 的构建本身可能触发布局；此处只取结构，不测尺寸
            n.children.forEach(::walk)
        }
        walk(root)
        return out
    }

    /**
     * 在探测模式下测量整棵树，收集依赖边。
     *
     * 逐个节点触碰尺寸（[LayoutNode.argWidth] / [LayoutNode.argHeight]），
     * 让引擎自己走完求值路径；探测模式保证不会成环崩溃。
     */
    /**
     * 在探测模式下测量整棵树，收集 ASK / READ 两条边。
     *
     * 每个节点触碰四样东西，缺一不可：
     * - [LayoutNode.argWidth] / [LayoutNode.argHeight]：触发 **ASK** 边
     *   （子节点向父级索要尺寸）。
     * - [LayoutNode.argPosition]：触发父级的 `childPosition` → 父级 cache 首算 →
     *   父级遍历子节点取 `outerSize` → 触发 **READ** 边。
     *   只测尺寸不够：`justify=between` 这类容器的 cache 只在算位置时才被求值。
     *
     * 全程 `runCatching`：某个节点测量失败不应中断整棵树的审计。
     */
    private fun measureAndCollectEdges(nodes: List<LayoutNode>): Pair<List<LayoutDep.ProbeEdge>, List<LayoutDep.ProbeEdge>> {
        val prev = LayoutDep.probeMode
        LayoutDep.probeMode = true
        try {
            for (n in nodes) {
                runCatching { n.argWidth }
                runCatching { n.argHeight }
                runCatching { n.argPosition(Direction.x) }
                runCatching { n.argPosition(Direction.y) }
            }
            return LayoutDep.takeProbeEdges()
        } finally {
            LayoutDep.probeMode = prev
        }
    }

    /**
     * 在依赖图上找环 —— **纯观测判据，不复述引擎规则**。
     *
     * 两个集合：
     * - **ASK**：子节点向父级索要尺寸（在 [LayoutNode.sizeFromParent] 处记录）
     * - **READ**：父级测量时读取子节点尺寸（在布局的 `outerSize` 处记录）
     *
     * 同一个 `(父级, 子节点, 方向)` 三元组同时出现在两个集合中 ⟹ 互依 ⟹ 成环。
     *
     * ## 为什么这样判
     *
     * 早期版本在 auditor 里**复述**引擎规则（"父级是 Flex 就必然回读子节点"），
     * 结果 363 处误报：漏了 grow 子节点那一支、也漏了交叉轴的 alignFix / alignItem 语义。
     * 规则一复述就会与引擎漂移。
     *
     * 改成观测后，判据与引擎行为**天然一致**——分支怎么走是引擎自己决定的，
     * auditor 只负责把两个事实配对。引擎将来改了分支语义，审计自动跟上。
     */
    private fun findCycles(
        asks: List<LayoutDep.ProbeEdge>,
        reads: List<LayoutDep.ProbeEdge>,
        maxFindings: Int,
    ): List<Finding> {
        if (asks.isEmpty() || reads.isEmpty()) return emptyList()

        // READ 边按 (父级, 子节点, 方向) 建索引
        val readIndex = HashSet<String>()
        for (r in reads) {
            readIndex.add(key(r.parent, r.child, r.direction))
        }

        val findings = ArrayList<Finding>()
        val reported = HashSet<String>()
        for (a in asks) {
            // 同一三元组既被"索要"又被"读取" → 互依
            if (!readIndex.contains(key(a.parent, a.child, a.direction))) continue
            val k = key(a.parent, a.child, a.direction)
            if (!reported.add(k)) continue

            findings.add(
                Finding(
                    node = a.child,
                    parent = a.parent,
                    direction = a.direction,
                    cycle = listOf(
                        Finding.Edge(
                            child = a.child,
                            parent = a.parent,
                            direction = a.direction,
                        )
                    ),
                )
            )
            if (findings.size >= maxFindings) break
        }
        return findings
    }

    /** 三元组标识（用身份比较，避免不同节点撞 key）。 */
    private fun key(parent: LayoutNode, child: LayoutNode, direction: Direction): String =
        "${System.identityHashCode(parent)}>${System.identityHashCode(child)}@$direction"
}
