package org.wy.signal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class BatchSignalEndTest {

    /**
     * 每个用例前重置全局批次状态，避免用例间互相污染。
     */
    private fun resetGlobalState() {
        G.beginBatch = false
        G.currentBatch = CurrentBatch()
        G.nextBatch = CurrentBatch()
        G.onWorkBatch = null
        G.onEffectRun = false
        G.onEffectLevel = 0
        G.onEffectKeys = mutableListOf()
    }

    /**
     * 批次内效果函数抛错时，错误必须向上抛出（不能被批次吞掉）。
     */
    @Test
    fun effectErrorShouldPropagate() {
        resetGlobalState()
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
        resetGlobalState()
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
        resetGlobalState()
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
        resetGlobalState()
        var ran = 0
        G.beginBatch = true
        G.currentBatch.effects.getOrPut(0) { mutableListOf() }.add { ran++ }

        batchSignalEnd()

        assertEquals(1, ran)
        assertFalse(G.beginBatch)
    }
}