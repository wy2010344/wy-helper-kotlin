package org.wy.engine

import com.wy.mve.DuplicateInfo
import com.wy.mve.StateHolderWithNode
import org.wy.signal.batchSignalEnd
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 列表项销毁/隐藏后的选区一致性（DemoList 删除行崩溃回归）：
 * - 可选集合是渲染树的纯派生（遍历），节点销毁或隐藏即自动出局，无命令式注册表；
 * - 定格的指针会话仍引用已销毁行时，选区派生必须安全回退（清空），
 *   不得对死节点做几何计算（此前在此处触发 StackLayout 数组越界）。
 */
class DestroyedSelectionTest {

    @org.junit.After
    fun drainSignalBatch() {
        Thread.sleep(50)
        batchSignalEnd()
    }

    private class Row(val key: Long)

    private class Env {
        lateinit var renderer: Renderer
        val rows = mutableListOf<WrappedTextNode>()

        var list by createSignal(listOf(Row(1), Row(2), Row(3)))
        var hideSecond by createSignal(false)

        val g: EngineGlobal get() = renderer.engineGlobal
        val manager: SelectionManager get() = renderer.engineGlobal.selectionManager

        fun build() {
            renderer = object : Renderer(null) {
                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    renderForEach(
                        { callback -> list.forEach { callback(it.key, it) } },
                        DuplicateInfo.WARN
                    ) { key, _ ->
                        object : WrappedTextNode(this) {
                            override val autoWidth: Boolean get() = true
                            override val text: String get() = "row-$key"
                            override val hide: Boolean get() = key == 2L && hideSecond
                        }.also(rows::add)
                    }
                }
            }
            renderer.children
        }

        /** 定格指针会话：press 在行首附近、release 在远端（换算为文本末尾），形成非零选区。 */
        fun freezeSelectionOn(row: WrappedTextNode) {
            val press = HitestResult(
                chain = listOf(NodeWithPosition(row, 0.5f, 5f)),
                time = 0L, x = 0.5f, y = 5f
            )
            val release = HitestResult(
                chain = listOf(NodeWithPosition(row, 999f, 5f)),
                time = 0L, x = 999f, y = 5f
            )
            g.pointerSelect = PointerSelect(press, release)
        }
    }

    @Test
    fun deletingFrozenRowClearsSelectionWithoutCrash() {
        val env = Env()
        env.build()
        env.freezeSelectionOn(env.rows[1])
        assertEquals("row-2", env.manager.selectedText)

        // 删除被定格引用的行：派生链不得触碰死节点（此前此处数组越界）
        env.list = env.list.filter { it.key != 2L }
        batchSignalEnd()

        assertNull(env.manager.anchorSel, "定格端点指向已销毁节点，选区应整体清空")
        assertNull(env.manager.selectedText)
    }

    @Test
    fun destroyedRowLeavesSelectableSetAndSelectAll() {
        val env = Env()
        env.build()

        env.list = env.list.filter { it.key != 2L }
        batchSignalEnd()

        env.manager.selectAll()
        assertEquals("row-1row-3", env.manager.selectedText, "全选聚合不应包含已销毁行")
    }

    @Test
    fun hiddenRowIsNotSelectable() {
        val env = Env()
        env.build()

        // hide 行由 children 的 purifyList 过滤，树遍历天然不可见
        env.hideSecond = true
        batchSignalEnd()

        env.manager.selectAll()
        assertEquals("row-1row-3", env.manager.selectedText, "全选聚合不应包含隐藏行")
    }
}
