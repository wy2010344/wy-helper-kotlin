package org.wy.signal

import org.wy.lib.EmptyFun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * signal 模块端到端链路测试：用真实 API（createSignal / memo / TrackSignal / addEffect）
 * 驱动完整反应式链，验证各部件协同而非孤立行为。
 *
 * 继承 [SignalTestBase]：用 TestScope（StandardTestDispatcher）替换生产 batchScope，
 * 协程排队不立即执行，测试内 `batchSignalEnd()` 同步消费批次，与浏览器 MessageChannel
 * 的同线程异步语义一致。无需 EDT / invokeAndWait。
 */
class SignalFlowTest : SignalTestBase() {

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

        probe.addFun()
        assertEquals(listOf(1), received, "初始化时应派发当前值")

        count.set(5)
        flushBatches()
        assertEquals(listOf(1, 5), received, "signal 变化应驱动 probe 重算")

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
        probe.addFun()
        assertEquals(listOf(4), received)
        assertEquals(1, memoRecompute, "首次计算一次")

        base.set(3)
        flushBatches()
        assertEquals(listOf(4, 9), received, "memo 依赖变化应重算并驱动 probe")
        assertEquals(2, memoRecompute, "依赖变化时应重算 memo")

        flushBatches()
        assertEquals(2, memoRecompute, "无变化时 memo 应命中缓存不重算")
    }

    // ===== 链路 3：memo 幂等性（过程体不重复执行） =====

    @Test
    fun memoProcessRunsOnceWithinSameStateVersion() {
        val base = createSignal(2)
        var recompute = 0
        val doubled = memo {
            recompute++
            base.get() * 2
        }

        assertEquals(4, doubled())
        assertEquals(4, doubled())
        assertEquals(4, doubled())
        assertEquals(1, recompute, "同 stateVersion 内多次读取过程体只执行一次")

        base.set(3)
        flushBatches()
        assertEquals(6, doubled())
        assertEquals(2, recompute, "上游变化应导致过程体重算")
    }

    @Test
    fun memoProcessNotReRunWhenNewObserverReplaysRelay() {
        val base = createSignal(5)
        var recompute = 0
        val squared = memo {
            recompute++
            base.get() * base.get()
        }

        val a = mutableListOf<Int>()
        val probeA = Probe({ squared() }, a::add)
        probeA.addFun()
        assertEquals(listOf(25), a)
        assertEquals(1, recompute, "首次计算一次")

        val b = mutableListOf<Int>()
        val probeB = Probe({ squared() }, b::add)
        probeB.addFun()
        assertEquals(listOf(25), b, "新观察者应拿到缓存值")
        assertEquals(1, recompute, "新增观察者重放 relays 不得重算 memo 过程体")

        base.set(6)
        flushBatches()
        assertEquals(listOf(25, 36), a, "观察者 A 应感知变化")
        assertEquals(listOf(25, 36), b, "观察者 B 应感知变化")
        assertEquals(2, recompute, "一次上游变化只重算一次")
    }

    // ===== 链路 4：memo afters 回调（值变化时触发） =====

    @Test
    fun memoAftersFireOnValueChange() {
        val base = createSignal(2)
        val squared = memo { base.get() * base.get() }
        val afterValues = mutableListOf<Int>()
        squared.afters.add { afterValues.add(it) }

        assertEquals(4, squared())
        assertEquals(listOf(4), afterValues, "首次计算应触发 afters")

        assertEquals(4, squared())
        assertEquals(listOf(4), afterValues, "值不变时 afters 不应触发")

        base.set(3)
        flushBatches()
        assertEquals(9, squared())
        assertEquals(listOf(4, 9), afterValues, "值变化时应触发 afters")
    }

    // ===== 链路 5：memo 嵌套派生（memo → memo） =====

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

    // ===== 链路 6：signal → addEffect 执行 =====

    @Test
    fun addEffectRunsOnBatchAndObservesSignalChange() {
        val count = createSignal(0)
        val effects = mutableListOf<Int>()

        addEffect { effects.add(count.get()) }

        flushBatches()
        assertEquals(listOf(0), effects, "effect 注册后应随批次执行一次")

        count.set(3)
        flushBatches()
        assertEquals(listOf(0), effects, "effect 不自动跟随 signal（非 trackSignal）")
    }

    // ===== 链路 7：dispose 停止派发 =====

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
