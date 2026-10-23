package org.wy.signal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.wy.lib.EmptyFun
import org.wy.lib.GetValue


internal class CurrentBatch(
    val listeners: MutableSet<TrackSignal<*>> = LinkedHashSet(),
    val effects: MutableMap<Int, MutableList<EmptyFun>> = mutableMapOf(),
    val deps: ArrayDeque<TrackSignal<*>> = ArrayDeque()
)

internal object G {
    var currentFun: TrackSignal<*>? = null
    var beginBatch: Boolean = false
    var currentBatch: CurrentBatch = CurrentBatch()
    var nextBatch: CurrentBatch = CurrentBatch()
    var onWorkBatch: CurrentBatch? = null
    var onEffectRun: Boolean = false
    var onEffectLevel: Int = 0
    var onEffectKeys: MutableList<Int> = mutableListOf()
    var callGet: Boolean = false
    var stateVersion: Any = Any()
    var currentRelay: MutableMap<GetValue<*>, Any?>? = null
}

// ═══════════════════════════════════════════
// Batch System
// ═══════════════════════════════════════════

var batchScope: CoroutineScope = CoroutineScope(
    SupervisorJob() + try {
        Dispatchers.Main
    } catch (_: IllegalStateException) {
        // 无 UI 环境（classpath 上没有 Main 实现）时，绝不能退到无界多线程的
        // Dispatchers.Default：G 是无同步的可变全局状态，批次并发执行会竞态。
        // limitedParallelism(1) 保证同一时刻至多一个批次协程在跑（串行，可能换线程）。
        Dispatchers.Default.limitedParallelism(1)
    }
)


fun signalOnUpdate(): Boolean = G.onWorkBatch != null

/**
 * 重置所有 signal 全局状态。供跨模块测试基类调用，
 * 防止测试间 G / stackMemos 残留导致状态污染。
 */
fun resetSignalGlobalState() {
    G.currentFun = null
    G.beginBatch = false
    G.currentBatch = CurrentBatch()
    G.nextBatch = CurrentBatch()
    G.onWorkBatch = null
    G.onEffectRun = false
    G.onEffectLevel = 0
    G.onEffectKeys = mutableListOf()
    G.callGet = false
    G.stateVersion = Any()
    G.currentRelay = null
    resetStackMemos()
}
