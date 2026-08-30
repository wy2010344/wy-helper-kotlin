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

    // 批次执行期间的错误（memo 出入栈不匹配、listener/effect 异常等）代表程序错误，
    // 捕获后批次数据已部分消费、无法恢复一致性，因此不在此吞掉：
    // 由 batchScope 协程结束处的异常处理器记录（与引擎各事件处理器独立捕获的模式一致）。
    // 这里仅在异常/成功路径统一还原批次标志，防止批次系统永久停摆。
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

            // 监听器执行期间的错误同样向上传播；onWorkBatch 需要及时还原
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

            // 效果执行期间的错误向上传播；onEffectRun 相关标志需要及时还原
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
    } finally {
        G.beginBatch = false
    }
}
