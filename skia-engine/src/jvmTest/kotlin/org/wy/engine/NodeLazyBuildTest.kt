package org.wy.engine

import com.wy.mve.Context
import com.wy.mve.DuplicateInfo
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import com.wy.mve.Creater
import org.wy.lib.EmptyFun
import org.wy.lib.GetValue
import org.wy.signal.Memo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证 Node.children 的惰性构建：
 * argChildren() 应在首次访问 children 时才执行，此时派生类属性已初始化，
 * 不会像"基类构造期调用 open 方法"那样读到默认值。
 */
class NodeLazyBuildTest {

    /** 模拟真实 mve 渲染树：renderNode 立即调用 callback（即 argChildren）。 */
    private class RealisticHolder : StateHolderWithNode<Node, List<Node>> {
        private val contextStore = mutableMapOf<Context<*>, Any?>()
        private val nodeList = mutableListOf<Node>()
        override val node: Node get() = error("test holder has no node")
        override val target: GetValue<List<Node>> get() = { nodeList.toList() }

        override fun <T> provide(context: Context<T>, value: T) {
            contextStore[context] = value
        }

        override fun addNode(n: Node) {
            nodeList.add(n)
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T> consume(context: Context<T>): T {
            contextStore[context]?.let { return it as T }
            return context.value
        }

        override fun addDestroy(destroy: EmptyFun) {}

        override val destroyed: Boolean get() = false

        @Suppress("UNCHECKED_CAST")
        override fun <Item, Key, Output> renderForEach(
            forEach: (callback: (Key, Item) -> GetValue<Output>) -> Unit,
            duplicateInfo: DuplicateInfo,
            creater: Creater<Node, List<Node>, Item, Key, Output>
        ): Memo<*> = error("Not supported in test")

        @Suppress("UNCHECKED_CAST")
        override fun renderNode(
            node: Node,
            callback: StateHolderWithNode<Node, List<Node>>.() -> Unit
        ): GetValue<List<Node>> {
            callback()
            return { nodeList.toList() }
        }

        @Suppress("UNCHECKED_CAST")
        override fun <NN, TT> renderNode(
            node: NN,
            config: com.wy.mve.ShareConfig<NN, TT>,
            callback: StateHolderWithNode<NN, TT>.() -> Unit
        ): GetValue<TT> {
            (callback as StateHolderWithNode<Node, List<Node>>.() -> Unit)()
            return { emptyList<Any>() as TT }
        }

        override fun getParent(): Any? {
            root?.let { return it }
            val g = consume(engineGlobalContext)
            root = if (g != null) Node(null, g) else null
            return root
        }

        private var root: Node? = null
    }

    @Test
    fun argChildrenRunsLazilyAfterDerivedPropsInitialized() {
        val holder = RealisticHolder()
        val engineGlobal = TestEngineGlobal()
        holder.provide(engineGlobalContext, engineGlobal)

        var childrenBuilt = false

        class Derived(
            context: StateHolder<*, *>?,
            engineGlobal: EngineGlobal?
        ) : Node(context, engineGlobal) {
            // 派生类属性：构造期尚未初始化时读取会得到 null
            val marker: String = "ready"

            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                // 惰性构建后派生类属性必须已初始化，否则此处 marker == null
                assertEquals("ready", marker)
                childrenBuilt = true
            }
        }

        val node = Derived(holder, engineGlobal)
        // 构造完成后 argChildren 不应已执行（否则说明仍是"构造期调用 open 方法"）
        assertFalse(childrenBuilt)

        // 首次访问 children 触发构建，且派生类属性已就绪
        node.children
        assertTrue(childrenBuilt)
    }

    @Test
    fun childrenBuiltOnFirstAccess() {
        val holder = RealisticHolder()
        val engineGlobal = TestEngineGlobal()
        holder.provide(engineGlobalContext, engineGlobal)

        var buildCount = 0

        class Leaf(
            context: StateHolder<*, *>?,
            engineGlobal: EngineGlobal?
        ) : Node(context, engineGlobal)

        class Derived(
            context: StateHolder<*, *>?,
            engineGlobal: EngineGlobal?
        ) : Node(context, engineGlobal) {
            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                buildCount++
                Leaf(this, engineGlobal)
            }
        }

        val node = Derived(holder, engineGlobal)
        // 构造完成后 argChildren 不应已执行
        assertEquals(0, buildCount)

        // 首次访问触发构建
        val first = node.children
        assertEquals(1, buildCount)

        // 幂等：再次访问返回同一份内容，且不重复构建
        val second = node.children
        assertEquals(first, second)
        assertEquals(1, buildCount)
    }
}
