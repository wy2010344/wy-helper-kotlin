package org.wy.signal

import org.wy.lib.EmptyFun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * signal 模块约束与防护测试：验证各机制的"防滥用"守卫在违规时正确报错，
 * 作为重构时不破坏行为约束的规格。
 */
class SignalGuardTest : SignalTestBase() {

    // ===== createLateSignal + getOnlySet：只允许写一次 =====

    @Test
    fun lateSignalOnlySetsOnce() {
        val s = createLateSignal<Int>(1)
        assertEquals(1, s.get())

        val write = s.getOnlySet()
        assertEquals(5, write(5))
        assertEquals(5, s.get())

        // 第二次 getOnlySet 应抛（只允许取一次写引用）
        assertFailsWith<IllegalStateException> {
            s.getOnlySet()
        }
    }

    @Test
    fun lateSignalGetOnlySetCustomMessage() {
        val s = createLateSignal<Int>(1)
        s.getOnlySet()
        assertFailsWith<IllegalStateException>("自定义错误信息") {
            s.getOnlySet("自定义错误信息")
        }
    }

    // ===== 计算期间不允许修改受观察 signal =====

    @Test
    fun cannotMutateSignalBeingObservedWithinBatch() {
        val count = createSignal(1)
        val probe = object : TrackSignal<Int>() {
            override fun get(old: Int?, inited: Boolean): Int = count.get()
            override fun set(v: Int, oldV: Int?, inited: Boolean): EmptyFun? = null
        }
        probe.addFun()
        assertEquals(1, count.get())

        // 在批次求值上下文中（onWorkBatch 非空）修改已被观察的 signal → 抛错
        G.onWorkBatch = CurrentBatch()
        assertFailsWith<Error> {
            count.set(5)
        }

        // 抛错发生在 storage 赋值前：signal 值不变，无状态破坏
        assertEquals(1, count.get())
    }

    // ===== 自定义 shouldChange =====

    @Test
    fun customShouldChangeAcceptsEqualValues() {
        // shouldChange=true → storage 总是更新，但 TrackSignal.set 只在值变化时调用
        val s = createSignal(1) { _, _ -> true }
        val received = mutableListOf<Int>()
        val probe = object : TrackSignal<Int>() {
            override fun get(old: Int?, inited: Boolean): Int = s.get()
            override fun set(v: Int, oldV: Int?, inited: Boolean): EmptyFun? {
                received.add(v)
                return null
            }
        }
        probe.addFun()
        assertEquals(listOf(1), received)

        s.set(1) // shouldChange=true → storage 更新，但值相同 → TrackSignal.set 不触发
        flushBatches()
        assertEquals(listOf(1), received, "值不变时 TrackSignal.set 不应触发")
    }

    @Test
    fun customShouldChangeRejectsDifferentValues() {
        // shouldChange=false → storage 不更新 → 不触发批次
        val s = createSignal(1) { _, _ -> false }
        val received = mutableListOf<Int>()
        val probe = object : TrackSignal<Int>() {
            override fun get(old: Int?, inited: Boolean): Int = s.get()
            override fun set(v: Int, oldV: Int?, inited: Boolean): EmptyFun? {
                received.add(v)
                return null
            }
        }
        probe.addFun()
        assertEquals(listOf(1), received)

        s.set(5) // shouldChange=false → storage 不更新 → 不触发批次
        flushBatches()
        assertEquals(listOf(1), received, "shouldChange=false 时值变化也不应触发更新")
    }

    // ===== StoreRef 属性委托 =====

    @Test
    fun storeRefPropertyDelegate() {
        val box = createSignal(10)
        var value by box
        assertEquals(10, value)
        value = 20
        assertEquals(20, box.get())
    }
}
