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

internal val batchScope by lazy {
    val d = try {
        Dispatchers.Main
    } catch (_: IllegalStateException) {
        Dispatchers.Default
    }
    CoroutineScope(SupervisorJob() + d)
}


fun signalOnUpdate(): Boolean = G.onWorkBatch != null
