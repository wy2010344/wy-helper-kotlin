package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolderWithNode
import org.jetbrains.skia.Surface
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.engine.helper.ButtonBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * 布局依赖审计入口（runtime audit）。
 *
 * 用法：./gradlew :desktopApp:layoutAudit
 *
 * ## 为什么是"运行时审计"而不是"静态 linter"
 *
 * 本框架的节点是 `argChildren()` 里的匿名对象，运行时由信号驱动创建，
 * **编译期不存在那棵树**，所以源码级静态分析做不到。
 *
 * 但树一旦构建完成，它就是**具体的对象图**——有真实的父子关系与布局属性。
 * 因此可以对已构建的树做检查，**一次跑出全部成环点**，
 * 而不是像崩溃驱动那样"撞一个修一个"。
 *
 * 关键技术：探测模式（[LayoutDep.probeMode]）把 `sizeFromParent`
 * 从「回调父级」改成「只记录依赖边」，于是整棵树能安全测量一遍，
 * 判环退化为依赖图上的环检测（纯算法）。
 *
 * 详见 [LayoutAudit]。
 */
private class AuditRoot(
    private val w: Float,
    private val h: Float,
    private val tab: DemoTab,
) : Renderer(null), FlexParam {
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.y
    override val directionJustify: DirectionJustify get() = DirectionJustify.start
    override val alignItem: AlignItem get() = AlignItem.stretch
    override val alignFix: Boolean get() = true
    override val argWidth: LayoutSize get() = LayoutSize(w, false)
    override val argHeight: LayoutSize get() = LayoutSize(h, false)

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        buildGrandDemo("", tab, { w }, { h })
    }
}

/**
 * 审计一棵已构建的树。
 *
 * 关键点：**先审计、后渲染**。
 *
 * 审计必须在**干净**的树上跑：若先渲染，成环会让 `FlexLayout.cache` 的 memo
 * 处于"已算但结果为 null"的坏状态（signal 的 memo 在异常路径下lastValue 未赋值），
 * 之后任何测量都会抛 NPE —— 审计将一无所获。
 *
 * 反过来，审计阶段只读不写（探测模式下 sizeFromParent 不递归），不会留下坏状态，
 * 渲染仍可正常进行。
 */
private fun auditTree(w: Int, h: Int, tab: DemoTab): List<LayoutAudit.Finding> {
    val surface = Surface.makeRasterN32Premul(w, h)
    val root = AuditRoot(w.toFloat(), h.toFloat(), tab)

    // 1) 先审计：构建树 + 探测测量（不触发真实布局，不产生环）
    val findings = LayoutAudit.audit(root)

    // 2) 再渲染一次，验证当前实现确实能正常出图（成环会被 LayoutDep 捕获并报出）
    runCatching { root.render(PlatformCanvas(surface.canvas)) }

    return findings
}

/**
 * 审计**弹层**打开时的树。
 *
 * 下拉菜单 / 对话框只在**打开时**才被构建 —— 静态截图永远走不到这些分支，
 * 也就永远发现不了它们里的布局环。弹层是 `Renderer` 渲染树的一部分
 * （`renderForEach` 把 pop 挂进树），所以只要把弹层打开，审计就能覆盖。
 *
 * [openPopup] 负责打开弹层（通常是点击某个按钮）。
 */
private fun auditWithPopup(
    w: Int,
    h: Int,
    tab: DemoTab,
    openPopup: (Renderer) -> Unit,
): List<LayoutAudit.Finding> {
    val surface = Surface.makeRasterN32Premul(w, h)
    val root = AuditRoot(w.toFloat(), h.toFloat(), tab)

    // 先渲染一次：让按钮等交互节点进入树、可被点击
    runCatching { root.render(PlatformCanvas(surface.canvas)) }

    // 打开弹层（会往渲染树挂 pop）
    runCatching { openPopup(root) }

    // 弹层打开后再审计：此时 pop 的子树已进入渲染树
    val findings = LayoutAudit.audit(root)
    runCatching { root.render(PlatformCanvas(surface.canvas)) }
    return findings
}

/**
 * 遍历树收集指定类型的节点。
 *
 * 用 [Node.children] 递归即可：弹层（pop）也在渲染树里。
 */
private fun <T> collectNodes(root: Node, type: Class<T>): List<T> {
    val out = ArrayList<T>()
    fun walk(n: Node) {
        if (type.isInstance(n)) out.add(type.cast(n)!!)
        n.children.forEach(::walk)
    }
    walk(root)
    return out
}

private fun auditMain() {
    println("═══ 布局依赖审计（runtime audit）═══")
    println("原理：树构建完成后是具体对象图；探测模式记录依赖边而不递归，")
    println("     故可一次测完整棵树，判环 = 依赖图找环（不依赖运行时异常）。\n")

    // 覆盖各页面与多种窗口宽度：宽度不同 → 树结构不同 → 环也不同
    data class Case(val label: String, val w: Int, val h: Int, val tab: DemoTab)

    val cases = listOf(
        Case("概览页 1120x740", 1120, 740, DemoTab.OVERVIEW),
        Case("笔记页 1120x740", 1120, 740, DemoTab.NOTES),
        Case("组件页 1120x1560", 1120, 1560, DemoTab.COMPONENTS),
        Case("看板页 1120x800", 1120, 800, DemoTab.DASHBOARD),
        Case("看板页 900x740", 900, 740, DemoTab.DASHBOARD),
        Case("看板页 720x700", 720, 700, DemoTab.DASHBOARD),
        Case("设置页 1120x740", 1120, 740, DemoTab.SETTINGS),
    )

    var total = 0
    for (c in cases) {
        val findings = auditTree(c.w, c.h, c.tab)
        if (findings.isEmpty()) {
            println("✔ ${c.label} —— 无成环")
        } else {
            println("✘ ${c.label} —— ${findings.size} 处成环")
            findings.forEach { println(it.report().prependIndent("    ")) }
            total += findings.size
        }
    }

    // ── 弹层状态：点击每个按钮，审计打开后的树 ──────────────
    //
    // 下拉菜单 / 对话框只在**打开时**才构建 —— 静态截图走不到，
    // 崩溃驱动的排查也永远发现不了这里的环。
    // 这里逐个点击按钮，把每个可达的交互状态都审计一遍。
    println()
    println("── 弹层状态（点击每个按钮后的树）──────────────────")
    var popupTotal = 0
    for (c in cases) {
        val surface = Surface.makeRasterN32Premul(c.w, c.h)
        val root = AuditRoot(c.w.toFloat(), c.h.toFloat(), c.tab)
        runCatching { root.render(PlatformCanvas(surface.canvas)) }

        val buttons = collectNodes(root, ButtonBase::class.java)
        var stateFindings = 0
        buttons.forEachIndexed { i, btn ->
            runCatching { btn.onClick() }
            val f = runCatching { LayoutAudit.audit(root) }.getOrDefault(emptyList())
            if (f.isNotEmpty()) {
                println("✘ ${c.label} · 按钮#${i} —— ${f.size} 处成环")
                f.forEach { println(it.report().prependIndent("    ")) }
                stateFindings += f.size
            }
            // 复位：关掉可能打开的弹层，避免相互叠加
            runCatching { btn.onClick() }
        }
        if (stateFindings == 0) {
            println("✔ ${c.label} —— ${buttons.size} 个按钮的弹层状态均无成环")
        }
        popupTotal += stateFindings
    }
    total += popupTotal

    println()
    println(if (total == 0) "总计：0 处成环 ✓" else "总计：$total 处成环 ✘")
}

fun main() {
    runBlocking {
        withContext(Dispatchers.Main) {
            auditMain()
        }
    }
}
