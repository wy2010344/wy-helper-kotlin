package org.wy.signal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * addEffect 扩展测试：高 level 执行顺序、effect 期间注册 effect 的行为。
 */
class AddEffectTest : SignalTestBase() {

    // ===== 高 level effect 按升序执行 =====

    @Test
    fun effectsRunInAscendingLevelOrder() {
        val order = mutableListOf<Int>()
        addEffect(0) { order.add(0) }
        addEffect(2) { order.add(2) }
        addEffect(1) { order.add(1) }

        flushBatches()
        assertEquals(listOf(0, 1, 2), order, "effect 应按 level 升序执行")
    }

    // ===== 同一 level 多个 effect 按注册顺序执行 =====

    @Test
    fun effectsSameLevelRunInRegistrationOrder() {
        val order = mutableListOf<String>()
        addEffect(0) { order.add("a") }
        addEffect(0) { order.add("b") }
        addEffect(0) { order.add("c") }

        flushBatches()
        assertEquals(listOf("a", "b", "c"), order, "同 level 应按注册顺序执行")
    }

    // ===== effect 执行期间注册更高级 effect：并入当前消费 =====

    @Test
    fun higherLevelEffectDuringExecutionInsertedIntoCurrentKeys() {
        val log = mutableListOf<String>()

        addEffect(0) {
            log.add("L0")
            addEffect(2) { log.add("L2-high") }
        }

        flushBatches()
        assertEquals(listOf("L0", "L2-high"), log, "高级 effect 应并入 onEffectKeys 继续消费")
    }

    // ===== effect 执行期间注册低级 effect：beginCurrentBatch → 新批次执行 =====

    @Test
    fun lowerLevelEffectDuringExecutionDeferred() {
        val log = mutableListOf<String>()

        addEffect(2) {
            log.add("L2")
            addEffect(0) { log.add("L0-low") }
        }

        flushBatches()
        // effect 执行期间 G.onWorkBatch=null → addEffect 走 run 分支 → beginCurrentBatch
        // → 新批次在后续 batchSignalEnd 中执行
        assertEquals(listOf("L2", "L0-low"), log, "低级 effect 通过新批次在同次 flush 中执行")
    }

    // ===== effect 中注册的 effect 跨批次执行 =====

    @Test
    fun effectRegisteredDuringEffectRunsInNextBatch() {
        val log = mutableListOf<String>()

        addEffect(0) {
            log.add("batch1")
            addEffect(0) { log.add("batch2") }
        }

        flushBatches()
        assertEquals(listOf("batch1", "batch2"), log)
    }
}
