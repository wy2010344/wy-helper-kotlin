package org.wy.signal

import org.wy.lib.EmptyFun


fun addEffect(level: Int = 0,effect: EmptyFun) {
    if (G.onEffectRun && level > G.onEffectLevel) {
        val olds = G.nextBatch.effects.getOrPut(level) { mutableListOf() }
        olds.add(effect)
        val keys = G.onEffectKeys
        val idx = keys.indexOfFirst { it < level }
        if (idx < 0) keys.add(level) else keys.add(idx, level)
    } else {
        val effects = G.onWorkBatch?.effects ?: run {
            beginCurrentBatch()
            G.currentBatch.effects
        }
        effects.getOrPut(level) { mutableListOf() }.add(effect)
    }
}