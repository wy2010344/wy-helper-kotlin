package org.wy.signal

import kotlinx.coroutines.launch

internal fun beginCurrentBatch() {
    if (!G.beginBatch) {
        G.beginBatch = true
        batchScope.launch { batchSignalEnd() }
    }
}


@Suppress("NewApi")
fun batchSignalEnd() {
    if (G.onEffectRun) return
    if (G.onWorkBatch != null) return

    checkMemoStack()
    try {
        var safety = 0
        while (G.beginBatch && safety < 1000) {
            safety++
            G.beginBatch = false
            val currentBatch = G.currentBatch
            G.currentBatch = G.nextBatch
            G.nextBatch = currentBatch

            val deps = currentBatch.deps
            val effects = currentBatch.effects
            val listeners = currentBatch.listeners

            G.onWorkBatch = currentBatch
            try {
                listeners.forEach { it.addFun() }

                while (deps.isNotEmpty()) {
                    deps.removeFirst().addFun()
                }
            } finally {
                listeners.clear()
                deps.clear()
                G.onWorkBatch = null
            }

            G.onEffectRun = true
            try {
                val keys = effects.keys.sortedDescending().toMutableList()
                G.onEffectKeys = keys
                while (keys.isNotEmpty()) {
                    val key = keys.removeLast()
                    G.onEffectLevel = key
                    effects[key]?.forEach { it() }
                }
            } finally {
                effects.clear()
                G.onEffectRun = false
                G.onEffectKeys = mutableListOf()
                G.onEffectLevel = 0
            }
        }
    } finally {
        G.beginBatch = false
    }
}
