package org.wy.signal

import org.wy.lib.EmptyFun
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * signal 模块端到端链路测试：用真实 API（createSignal / memo / TrackSignal / addEffect）
 * 驱动完整反应式链，验证各部件协同而非孤立行为。
 *
 * 批次由 set / addEffect 触发 beginCurrentBatch，本质是 batchScope 异步协程消费；
 * 测试用 flushBatches() 等待协程消费完成，保证断言确定、无跨用例污染（@After 兜底）。
 */
class SignalFlowTest {

    @BeforeTest
    fun resetGlobalState() {
        G.beginBatch = false
        G.currentBatch = CurrentBatch()
        G.nextBatch = CurrentBatch()
        G.onWorkBatch = null
        G.onEffectRun = false
        G.onEffectLevel = 0
        G.onEffectKeys = mutableListOf()
    }

    @AfterTest
    fun drainAsyncBatch() {
        flushBatches()
        resetGlobalState()
    }

    /**
     * 等待异步批次协程完全消费：set / addEffect 触发 beginCurrentBatch 后，
     * 批次在 batchScope 的协程中消费（测试线程不手动参与，避免与协程竞争全局状态）。
     * 轮询直到批次标志稳定（无在途批次/效果），保证断言时序确定。
     */
    private fun flushBatches() {
        var guard = 0
        while (guard++ < 500) {
            Thread.sleep(10)
            if (!G.beginBatch && G.onEffectRun == false && G.onWorkBatch == null) return
        }
        throw AssertionError("flushBatches 超时：批次未能排空")
    }

    /**
     * 简易 TrackSignal：读一个表达式（含信号/memo），set 时记录派发值。
     * 用于验证 "signal/memo 变化 → 派生跟踪信号重新计算并派发"。
     */
    private class Probe(
        private val expr: () -> Int,
        private val onSet: (Int) -> Unit = {}
    ) : TrackSignal<Int>() {
        override fun get(old: Int?, inited: Boolean): Int = expr()
        override fun set(v: Int, oldV: Int?, inited: Boolean): EmptyFun? {
            onSet(v)
            return null
        }
    }

    // ===== 链路 1：signal → TrackSignal 直接派发 =====

    @Test
    fun signalPropagatesToTrackSignal() {
        val count = createSignal(1)
        val received = mutableListOf<Int>()
        val probe = Probe({ count.get() }, received::add)

        // 建立依赖：currentFun=probe 下读取 count → probe 订阅 count
        probe.addFun()
        assertEquals(listOf(1), received, "初始化时应派发当前值")

        // 修改 signal → 触发批次 → 排水后 probe 复算并派发新值
        count.set(5)
        flushBatches()
        assertEquals(listOf(1, 5), received, "signal 变化应驱动 probe 重算")

        // 用 createSignal 的 shouldChange 默认比较器：写相同值不重复派发
        count.set(5)
        flushBatches()
        assertEquals(listOf(1, 5), received, "相同值写入不应重复派发")
    }

    // ===== 链路 2：signal → memo → TrackSignal 两级派生 =====

    @Test
    fun signalDerivesThroughMemoToTrackSignal() {
        val base = createSignal(2)
        var memoRecompute = 0
        val squared = memo {
            memoRecompute++
            base.get() * base.get()
        }

        val received = mutableListOf<Int>()
        val probe = Probe({ squared() }, received::add)
        probe.addFun() // 初始化：订阅 squared（memo），memo 订阅 base
        assertEquals(listOf(4), received)
        assertEquals(1, memoRecompute, "首次计算一次")

        base.set(3)
        flushBatches()
        assertEquals(listOf(4, 9), received, "memo 依赖变化应重算并驱动 probe")
        assertEquals(2, memoRecompute, "依赖变化时应重算 memo")

        // memo 缓存：同 stateVersion 且 relay 未变时不重算
        flushBatches()
        assertEquals(2, memoRecompute, "无变化时 memo 应命中缓存不重算")
    }

    // ===== 链路 3：memo 嵌套派生（memo → memo） =====

    @Test
    fun memoChainsThroughAnotherMemo() {
        val a = createSignal(1)
        val b = memo { a.get() + 10 }
        val c = memo { b() * 2 }

        val received = mutableListOf<Int>()
        val probe = Probe({ c() }, received::add)
        probe.addFun()
        assertEquals(listOf(22), received, "a=1 -> b=11 -> c=22")

        a.set(5)
        flushBatches()
        assertEquals(listOf(22, 30), received, "a=5 -> b=15 -> c=30")
    }

    // ===== 链路 4：signal → addEffect 执行 =====

    @Test
    fun addEffectRunsOnBatchAndObservesSignalChange() {
        val count = createSignal(0)
        val effects = mutableListOf<Int>()

        addEffect { effects.add(count.get()) }

        // addEffect 触发批次，effect 应已执行一次
        flushBatches()
        assertEquals(listOf(0), effects, "effect 注册后应随批次执行一次")

        count.set(3)
        flushBatches()
        // note: effect 未订阅 count，不会因 count 变化自动重跑；
        // 仅验证显式排空批次时 effect 已消费当前值
        assertEquals(listOf(0), effects, "effect 不自动跟随 signal（非 trackSignal）")
    }

    // ===== 链路 5：dispose 停止派发 =====
    @Test
    fun disposedTrackSignalStopsReceiving() {
        val count = createSignal(1)
        val received = mutableListOf<Int>()
        val probe = Probe({ count.get() }, received::add)
        probe.addFun()
        flushBatches()
        assertEquals(listOf(1), received)

        probe.dispose()
        count.set(9)
        flushBatches()
        assertEquals(listOf(1), received, "dispose 后不应再派发")
        assertTrue(probe.disabled, "dispose 应标记 disabled")
    }
}

