package org.wy.signal

import org.wy.lib.EmptyFun
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * TrackSignal 正常行为测试：collect 正常路径、set 返回 destroy 函数、多次 addFun 幂等。
 */
class TrackSignalNormalTest : SignalTestBase() {

    // ===== collect 正常路径（在非 addFun 上下文中调用） =====

    @Test
    fun collectWorksWhenCurrentFunIsNull() {
        val count = createSignal(10)
        // collect 只能在非 addFun 上下文中使用（即 G.currentFun 为 null）
        val probe = object : TrackSignal<Int>() {
            override fun get(old: Int?, inited: Boolean): Int = count.get()
            override fun set(v: Int, oldV: Int?, inited: Boolean): EmptyFun? = null
        }
        // 手动调用 collect（模拟非订阅场景）
        val result = probe.collect<Int> { count.get() }
        assertEquals(10, result, "collect 在非观察上下文中应正常工作")
    }

    // ===== set 返回 destroy 函数 =====

    @Test
    fun setDestroyFunctionIsCalledOnValueChange() {
        var destroyCalled = false
        val count = createSignal(1)
        val probe = object : TrackSignal<Int>() {
            override fun get(old: Int?, inited: Boolean): Int = count.get()
            override fun set(v: Int, oldV: Int?, inited: Boolean): EmptyFun? {
                return { destroyCalled = true }
            }
        }
        probe.addFun()
        assertEquals(false, destroyCalled)

        count.set(2)
        flushBatches()
        // dispose 会调用 destroy
        probe.dispose()
        assertEquals(true, destroyCalled)
    }

    // ===== TrackSignal.init 依赖 onWorkBatch 路径 =====

    @Test
    fun trackSignalCreatedDuringBatchJoinsOnWorkBatchDeps() {
        val batch = CurrentBatch()
        G.onWorkBatch = batch

        // 此时创建的 TrackSignal 应加入 onWorkBatch.deps
        val probe = object : TrackSignal<Int>() {
            override fun get(old: Int?, inited: Boolean): Int = 42
            override fun set(v: Int, oldV: Int?, inited: Boolean): EmptyFun? = null
        }

        assertEquals(1, batch.deps.size, "应加入 onWorkBatch.deps")
        G.onWorkBatch = null
    }

    // ===== dispose 后 addFun 不执行 =====

    @Test
    fun disposedTrackSignalDoesNotExecuteAddFun() {
        var execCount = 0
        val probe = object : TrackSignal<Int>() {
            override fun get(old: Int?, inited: Boolean): Int {
                execCount++
                return 1
            }
            override fun set(v: Int, oldV: Int?, inited: Boolean): EmptyFun? = null
        }
        probe.dispose()
        probe.addFun()
        assertEquals(0, execCount, "dispose 后 addFun 应直接跳过，不调用 get")
    }

    // ===== TrackSignal 值变化触发 set =====

    @Test
    fun trackSignalSetCalledWhenValueChanges() {
        val count = createSignal(1)
        val received = mutableListOf<String>()
        val probe = object : TrackSignal<Int>() {
            override fun get(old: Int?, inited: Boolean): Int = count.get()
            override fun set(v: Int, oldV: Int?, inited: Boolean): EmptyFun? {
                received.add("$oldV->$v")
                return null
            }
        }
        probe.addFun()
        assertEquals(listOf("null->1"), received, "首次初始化触发 set(old=null)")

        count.set(5)
        flushBatches()
        assertEquals(listOf("null->1", "1->5"), received, "值变化触发 set(old=1, new=5)")
    }

    @Test
    fun trackSignalSetNotCalledWhenValueSame() {
        val count = createSignal(1)
        var setCount = 0
        val probe = object : TrackSignal<Int>() {
            override fun get(old: Int?, inited: Boolean): Int = count.get()
            override fun set(v: Int, oldV: Int?, inited: Boolean): EmptyFun? {
                setCount++
                return null
            }
        }
        probe.addFun()
        assertEquals(1, setCount)

        count.set(1) // 相同值
        flushBatches()
        assertEquals(1, setCount, "值不变时 set 不应被调用")
    }
}
