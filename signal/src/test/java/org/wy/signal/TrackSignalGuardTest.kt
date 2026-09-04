package org.wy.signal

import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * TrackSignal.collect 防护：禁止在"受观察处"（currentFun 已非空）发起 collect，
 * 防止在派生求值中递归收集导致状态错乱。
 *
 * 独立成类：collect 在异常路径会残留 G.currentFun，故不与其它用例共享 fixture，
 * 以保证隔离。
 */
class TrackSignalGuardTest : SignalTestBase() {

    @Test
    fun collectForbiddenInsideObservation() {
        // addFun 会设 currentFun = probe，其 get 内调用 collect 即命中
        // "禁止在受观察处发起"检查 → 抛错
        val probe = object : TrackSignal<Int>() {
            override fun get(old: Int?, inited: Boolean): Int = collect { 0 }
        }

        assertFailsWith<Error> {
            probe.addFun()
        }
    }
}
