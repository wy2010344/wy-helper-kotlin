package org.wy.signal

import org.wy.lib.EmptyFun


fun addEffect(level: Int = 0, effect: EmptyFun) {
    if (G.onEffectRun && level > G.onEffectLevel) {
        // 效果执行期间，且新 level 大于当前正在执行的 level：
        // 可在当前区间插入——加入 nextBatch.effects（下一次批次消费），
        // 并把该 level 并入 onEffectKeys（保持降序、去重），供批次继续消费。
        // 这样新增的更高级 effect 能随后续批次执行，而不会重跑已执行过的区间
        // （level <= onEffectLevel 时走正常分支延迟到下一批，防止死循环）。
        if (G.nextBatch.effects[level] == null) {
            G.nextBatch.effects[level] = mutableListOf(effect)
            val idx = G.onEffectKeys.indexOfFirst { it < level }
            if (idx < 0) G.onEffectKeys.add(level) else G.onEffectKeys.add(idx, level)
        } else {
            G.nextBatch.effects[level]!!.add(effect)
        }
    } else {
        val effects = G.onWorkBatch?.effects ?: run {
            beginCurrentBatch()
            G.currentBatch.effects
        }
        effects.getOrPut(level) { mutableListOf() }.add(effect)
    }
}