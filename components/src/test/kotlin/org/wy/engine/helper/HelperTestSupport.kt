// 与 skia-engine 的 HelperTestSupport.kt 保持同步（KMP 不支持 testFixtures，故复制共享，勿单侧修改）。
package org.wy.engine.helper

import com.wy.mve.StateHolder
import org.wy.engine.*

/** 构造最小测试环境（Mock StateHolder + EngineGlobal + SelectionManager）。 */
fun createHelperEnv(): Pair<TestStateHolder<Node, List<Node>>, TestEngineGlobal> {
    val state = TestStateHolder<Node, List<Node>>()
    val g = TestEngineGlobal()
    state.provide(engineGlobalContext, g)
    state.provide(selectionManagerContext, SelectionManager())
    return state to g
}

/** 构造一个只包含单个节点的命中链（x/y 均为 0）。 */
fun hit(node: Node) = HitestResult(listOf(NodeWithPosition(node, 0f, 0f)), 0L, 0f, 0f)
