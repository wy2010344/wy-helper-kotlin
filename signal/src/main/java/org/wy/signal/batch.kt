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

    try {
        checkMemoStack()
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
                listeners.clear()

                while (deps.isNotEmpty()) {
                    deps.removeFirst().addFun()
                }
            } finally {
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
                effects.clear()
            } finally {
                G.onEffectRun = false
                G.onEffectKeys = mutableListOf()
                G.onEffectLevel = 0
            }
        }
    }catch (err: Throwable){
        println("batchSignalEnd error---$err")
        G.onWorkBatch = null
        G.onEffectRun = false
        G.onEffectKeys = mutableListOf()
        G.onEffectLevel = 0
        G.beginBatch = false
    }
}
