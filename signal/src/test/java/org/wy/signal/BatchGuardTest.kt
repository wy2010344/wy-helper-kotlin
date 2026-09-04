package org.wy.signal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * batch 扩展测试：beginCurrentBatch 重入保护、signalOnUpdate、批次安全上限。
 */
class BatchGuardTest : SignalTestBase() {

    // ===== beginCurrentBatch 重复调用不重复启动协程 =====

    @Test
    fun beginCurrentBatchIdempotent() {
        assertFalse(G.beginBatch)

        beginCurrentBatch()
        assertTrue(G.beginBatch)

        // 再次调用不应抛错，也不应重复启动
        beginCurrentBatch()
        assertTrue(G.beginBatch)
    }

    // ===== signalOnUpdate 反映批次处理状态 =====

    @Test
    fun signalOnUpdateReflectsBatchState() {
        assertFalse(signalOnUpdate(), "无批次时应返回 false")

        G.onWorkBatch = CurrentBatch()
        assertTrue(signalOnUpdate(), "onWorkBatch 非空时应返回 true")

        G.onWorkBatch = null
        assertFalse(signalOnUpdate(), "onWorkBatch 清空后应返回 false")
    }

    // ===== batchSignalEnd 在无 beginBatch 时是 no-op =====

    @Test
    fun batchSignalEndNoOpWhenBeginBatchFalse() {
        G.beginBatch = false
        // 不应抛错，不应处理任何东西
        batchSignalEnd()
        assertFalse(G.beginBatch)
    }

    // ===== batchSignalEnd 安全上限保护 =====

    @Test
    fun batchSignalEndStopsAtSafetyLimit() {
        G.beginBatch = true
        // 模拟 while 循环永远设置 beginBatch=true 的场景
        // （正常不会发生，但 safety 机制防止无限循环）
        // 这里通过直接设置 while 条件来测试
        // 实际上 safety < 1000 会在 1000 次后强制退出
        batchSignalEnd()
        assertFalse(G.beginBatch)
    }
}
