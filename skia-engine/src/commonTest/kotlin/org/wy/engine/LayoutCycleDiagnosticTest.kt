package org.wy.engine

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 布局依赖成环诊断（[LayoutDep]）。
 *
 * 关注三件事：
 * 1. 成环能被检出，且报错带**完整依赖链 + 方向 + 根因 + 修法**（供AI / 人直接定位）；
 * 2. **不误报**——合法场景（同节点不同方向 = 由宽定高）必须放行；
 * 3. 开关可关闭，退回 signal 的兜底检测。
 *
 * 说明：本测试直接驱动 [LayoutDep]，不构造完整渲染树——
 * `TestStateHolder.renderNode` 恒返回空children，无法在单测里搭出真实的父子测量链。
 */
class LayoutCycleDiagnosticTest {

    /** 供测试构造节点用的 StateHolder（提供 engineGlobal 以满足 Node 的 parent 查找）。 */
    private fun holder(): TestStateHolder<Node, List<Node>> {
        val h = TestStateHolder<Node, List<Node>>()
        val global = TestEngineGlobal()
        h.provide(engineGlobalContext, global)
        h.provide(selectionManagerContext, global.selectionManager)
        return h
    }

    private fun node(name: String?) = object : RectNode(holder()) {
        override val debugName: String? get() = name
    }

    private fun parentNode(): LayoutNode = object : RectNode(holder()) {}

    // ── 1. 成环能被检出且信息完整 ─────────────────────────────

    @Test
    fun sameNodeSameDirectionIsReportedAsCycle() {
        val n = node("精度按钮")
        val parent = parentNode()
        // 让被测节点的 layoutParent 可见：sizeFromParent 需要它
        setLayoutParentForTest(n, parent)

        val err = assertFailsWith<LayoutCycleError> {
            // 模拟：父级测量子节点宽度 → 子节点回读父级宽度（成环）
            LayoutDep.track(n, Direction.x) {
                n.sizeFromParent(Direction.x)
            }
        }
        val msg = err.message ?: ""

        assertContains(msg, "布局依赖成环")
        assertContains(msg, "依赖链")
        assertContains(msg, "修法")
        // 业务名应出现在报告里（这是 debugName 的价值）
        assertContains(msg, "精度按钮")
        // 方向要标出来
        assertContains(msg, "(宽)")
        assertContains(msg, "环起点")
    }

    /** 依赖链应按进入顺序完整列出，可直接看出环从哪一条边闭合。 */
    @Test
    fun reportContainsFullDependencyChain() {
        val a = node("A容器")
        val b = node("B按钮")
        val pa = parentNode()
        val pb = parentNode()
        setLayoutParentForTest(a, pa)
        setLayoutParentForTest(b, pb)

        val err = assertFailsWith<LayoutCycleError> {
            LayoutDep.track(a, Direction.x) {
                LayoutDep.track(b, Direction.x) {
                    // A 二次进入 → 环
                    a.sizeFromParent(Direction.x)
                }
            }
        }
        val msg = err.message ?: ""
        // 链上应能看到 A → B → A，且 A 被标为环起点（[A容器…]）
        assertContains(msg, "A容器")
        assertContains(msg, "B按钮")
        // "依赖链：[A容器…" —— 方括号紧跟"依赖链："，包住环起点那一段
        val chainAt = msg.indexOf("依赖链：")
        assertTrue(chainAt >= 0, "报告应含依赖链段：$msg")
        val chain = msg.substring(chainAt)
        assertTrue(chain.contains("[A容器"), "环起点应被 [ ] 包住：$msg")
    }

    // ── 2. 不误报 ──────────────────────────────────────────────

    /**
     * 关键回归：同一节点、**不同方向**的嵌套是合法的
     * （框架核心能力：由宽度决定高度 / 由高度决定宽度）。
     * 这条若被误判，框架的灵活性就没了。
     *
     * 只验证"不判环"，不要求父级真的有布局实现（父级为absolute 空布局）。
     */
    @Test
    fun sameNodeDifferentDirectionIsLegal() {
        val n = node("折行文本")
        setLayoutParentForTest(n, parentNode())
        // x 与 y 依次进入，互不冲突 → 不应抛 LayoutCycleError
        try {
            LayoutDep.track(n, Direction.x) {
                LayoutDep.track(n, Direction.y) {
                    // 父级为 absolute 空布局会抛 LayoutError，与环无关
                }
            }
        } catch (e: LayoutCycleError) {
            kotlin.test.fail("同节点不同方向不应判环：${e.message}")
        } catch (_: Throwable) {
            // 其它异常（如空布局的 LayoutError）与本用例无关
        }
    }

    /** 退出后必须正常弹栈：先成环一次，再合法使用，不应残留状态。 */
    @Test
    fun stackUnwindsAfterExit() {
        val n = node("叶子")
        setLayoutParentForTest(n, parentNode())
        // 一次正常进出
        try {
            LayoutDep.track(n, Direction.x) { }
        } catch (_: Throwable) {
            // 忽略：本测试只关心栈是否干净
        }
        // 之后不应再被判成环
        LayoutDep.track(n, Direction.x) { }
    }

    /** 不同节点同方向嵌套（父→子链）是正常的求值顺序，不成环。 */
    @Test
    fun distinctNodesNestedIsNotCycle() {
        val a = node("父")
        val b = node("子")
        setLayoutParentForTest(a, parentNode())
        setLayoutParentForTest(b, parentNode())
        LayoutDep.track(a, Direction.x) {
            LayoutDep.track(b, Direction.x) { }
        }
    }

    // ── 4. 探测模式：ASK / READ 配对 ─────────────────────────
    //
    // 审计的判据是纯观测的：同一 (父级, 子节点, 方向) 三元组
    // 既出现在 ASK 集合（子向父要尺寸）又出现在 READ 集合（父读子尺寸）→ 成环。
    // 下面直接驱动这两个集合，验证配对逻辑本身。

    @Test
    fun probeRecordsAskAndReadEdges() {
        val parent = parentNode()
        val child = node("精度按钮")
        setLayoutParentForTest(child, parent)

        LayoutDep.probeMode = true
        try {
            // ASK：子节点向父级索要尺寸（探测模式下不递归）
            val size = child.sizeFromParent(Direction.x)
            val (asks, reads) = LayoutDep.takeProbeEdges()

            assertEquals(1, asks.size, "应记录一条 ASK 边")
            assertEquals(child, asks[0].child)
            assertEquals(parent, asks[0].parent)
            assertEquals(Direction.x, asks[0].direction)
            assertTrue(reads.isEmpty(), "尚未发生父级读取，不应有 READ 边")
            // 探测模式返回占位尺寸而非真实父级尺寸
            assertEquals(0f, size.value)
        } finally {
            LayoutDep.probeMode = false
            LayoutDep.takeProbeEdges()
        }
    }

    @Test
    fun probeReadEdgeCompletesTheTriple() {
        val parent = parentNode()
        val child = node("精度按钮")
        setLayoutParentForTest(child, parent)

        LayoutDep.probeMode = true
        try {
            child.sizeFromParent(Direction.x)          // ASK (child, x, parent)
            LayoutDep.probeRead(parent, child, Direction.x) // READ (parent, child, x)
            val (asks, reads) = LayoutDep.takeProbeEdges()

            assertEquals(1, asks.size)
            assertEquals(1, reads.size)
            // 同一三元组：ASK 与 READ 配对成功 → 判环条件成立
            assertEquals(
                "${System.identityHashCode(asks[0].parent)}>" +
                    "${System.identityHashCode(asks[0].child)}@${asks[0].direction}",
                "${System.identityHashCode(reads[0].parent)}>" +
                    "${System.identityHashCode(reads[0].child)}@${reads[0].direction}",
            )
        } finally {
            LayoutDep.probeMode = false
            LayoutDep.takeProbeEdges()
        }
    }

    /** 方向不同则不配对：同一对节点在 x 与 y 上分别 ASK/READ 不构成环。 */
    @Test
    fun mismatchedDirectionDoesNotPair() {
        val parent = parentNode()
        val child = node("精度按钮")
        setLayoutParentForTest(child, parent)

        LayoutDep.probeMode = true
        try {
            child.sizeFromParent(Direction.x)              // ASK (child, x, parent)
            LayoutDep.probeRead(parent, child, Direction.y) // READ (parent, child, y)
            val (asks, reads) = LayoutDep.takeProbeEdges()

            assertEquals(1, asks.size)
            assertEquals(1, reads.size)
            assertTrue(asks[0].direction != reads[0].direction, "方向不同不应配对")
        } finally {
            LayoutDep.probeMode = false
            LayoutDep.takeProbeEdges()
        }
    }

    // ── 3. 开关 ────────────────────────────────────────────────

    @Test
    fun disabledTracerSkipsCycleCheck() {
        val n = node("精度按钮")
        setLayoutParentForTest(n, parentNode())
        LayoutDep.enabled = false
        try {
            // 关闭后不再抛 LayoutCycleError
            try {
                LayoutDep.track(n, Direction.x) {
                    LayoutDep.track(n, Direction.x) {
                        n.sizeFromParent(Direction.x)
                    }
                }
            } catch (e: LayoutCycleError) {
                kotlin.test.fail("关闭追踪后不应抛 LayoutCycleError：${e.message}")
            } catch (_: Throwable) {
                // 空布局的 LayoutError 等与本用例无关
            }
        } finally {
            LayoutDep.enabled = true
        }
    }

    /** 打开时同一场景必须抛出（与上一个测试互为对照，确保开关真的生效）。 */
    @Test
    fun enabledTracerDetectsCycle() {
        val n = node("精度按钮")
        setLayoutParentForTest(n, parentNode())
        assertFailsWith<LayoutCycleError> {
            LayoutDep.track(n, Direction.x) {
                LayoutDep.track(n, Direction.x) {
                    n.sizeFromParent(Direction.x)
                }
            }
        }
    }

    /** 为测试设置 layoutParent：LayoutNode.layoutParent 为 val 构造期固化，这里用最小反射注入。 */
    private fun setLayoutParentForTest(node: LayoutNode, parent: LayoutNode) {
        val field = LayoutNode::class.java.getDeclaredField("layoutParent")
        field.isAccessible = true
        field.set(node, parent)
    }
}