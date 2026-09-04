package org.wy.signal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BatchSignalEndTest : SignalTestBase() {

    /**
     * 批次内效果函数抛错时，错误必须向上抛出（不能被批次吞掉）。
     */
    @Test
    fun effectErrorShouldPropagate() {
        G.beginBatch = true
        G.currentBatch.effects.getOrPut(0) { mutableListOf() }.add {
            throw IllegalStateException("boom")
        }

        assertFailsWith<IllegalStateException> { batchSignalEnd() }
    }

    /**
     * 批次内 listener 抛错时，错误同样必须向上抛出。
     */
    @Test
    fun listenerErrorShouldPropagate() {
        G.beginBatch = true
        val badListener = object : TrackSignal<Int>() {
            override fun get(old: Int?, inited: Boolean): Int {
                throw IllegalArgumentException("bad listener")
            }
        }
        G.currentBatch.listeners.add(badListener)

        assertFailsWith<IllegalArgumentException> { batchSignalEnd() }
    }

    /**
     * 抛错后 G 的批次标志必须还原，避免批次系统永久停摆。
     */
    @Test
    fun errorShouldNotLeaveBatchStuck() {
        G.beginBatch = true
        G.currentBatch.effects.getOrPut(0) { mutableListOf() }.add {
            throw IllegalStateException("boom")
        }

        assertFailsWith<IllegalStateException> { batchSignalEnd() }
        assertFalse(G.beginBatch)
        assertNull(G.onWorkBatch)
        assertFalse(G.onEffectRun)
        assertEquals(0, G.onEffectLevel)
        assertEquals(0, G.onEffectKeys.size)
    }

    /**
     * 成功后正常消除批次并执行效果，行为不回归。
     */
    @Test
    fun normalBatchStillWorks() {
        var ran = 0
        G.beginBatch = true
        G.currentBatch.effects.getOrPut(0) { mutableListOf() }.add { ran++ }

        batchSignalEnd()

        assertEquals(1, ran)
        assertFalse(G.beginBatch)
    }

    /**
     * 同一批次内 effect 按 level 升序执行（level 越小越先）。
     */
    @Test
    fun effectsRunInLevelOrder() {
        val order = mutableListOf<Int>()
        G.beginBatch = true
        G.currentBatch.effects.getOrPut(1) { mutableListOf() }.add { order.add(1) }
        G.currentBatch.effects.getOrPut(0) { mutableListOf() }.add { order.add(0) }
        G.currentBatch.effects.getOrPut(2) { mutableListOf() }.add { order.add(2) }

        batchSignalEnd()

        assertEquals(listOf(0, 1, 2), order)
        assertFalse(G.beginBatch)
    }

    /**
     * effect 执行期间（onEffectRun=true）注册**更高级别**（level > onEffectLevel）
     * 的 effect：加入 nextBatch 并在 onEffectKeys 中并入该 level，供后续批次消费——
     * 而不是立即执行或触发新批次。
     */
    @Test
    fun effectRegisteredDuringEffectRunWithHigherLevelGoesToNextBatch() {
        G.onEffectRun = true
        G.onEffectLevel = 5
        var ran = 0

        addEffect(7) { ran++ }

        assertFalse(G.beginBatch, "重入注册不应触发新批次启动")
        assertNull(G.onWorkBatch)
        assertEquals(0, ran, "应加入 nextBatch 而非立即执行")
        assertEquals(1, G.nextBatch.effects[7]?.size ?: 0, "应排入 nextBatch")
        assertTrue(G.onEffectKeys.contains(7), "应把新 level 并入 onEffectKeys（供批次消费）")
    }

    /**
     * effect 执行期间注册**不高**（level <= onEffectLevel）的 effect：
     * 走正常分支（延迟到后续批次），不参与当前区间的 keys 再消费——防止已执行过的
     * 区间被重复运行导致死循环。
     */
    @Test
    fun effectRegisteredDuringEffectRunWithLowerOrEqualLevelGoesToNormalPath() {
        G.onEffectRun = true
        G.onEffectLevel = 5
        G.onWorkBatch = CurrentBatch()
        var ran = 0

        addEffect(3) { ran++ }

        assertFalse(G.beginBatch, "正常分支且 onWorkBatch 非空，不应触发新批次")
        assertEquals(0, ran, "应延迟到后续批次，不立即执行")
        assertEquals(1, G.onWorkBatch!!.effects[3]?.size ?: 0, "应排入 onWorkBatch 累积")
        assertFalse(G.onEffectKeys.contains(3), "不应并入 onEffectKeys 参与当前消费")
    }
}
