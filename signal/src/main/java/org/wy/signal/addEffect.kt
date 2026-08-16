package org.wy.signal

import org.wy.lib.EmptyFun


fun addEffect(level: Int = 0,effect: EmptyFun) {
    if (G.onEffectRun) {
        // 效果执行期间注册的效果排到下一批（G.currentBatch 为批次交换后的累积目标），
        // 不重新触发本批处理，避免自续期效果在本批内无限循环；由下一次真实批次自然消费。
        G.currentBatch.effects.getOrPut(level) { mutableListOf() }.add(effect)
    } else {
        val effects = G.onWorkBatch?.effects ?: run {
            beginCurrentBatch()
            G.currentBatch.effects
        }
        effects.getOrPut(level) { mutableListOf() }.add(effect)
    }
}