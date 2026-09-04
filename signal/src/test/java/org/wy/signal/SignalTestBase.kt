@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.wy.signal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * signal 测试基类：用 [TestScope]（[StandardTestDispatcher]）替换生产 [batchScope]，
 * 协程排队不立即执行，测试内 `batchSignalEnd()` 同步消费批次，与浏览器 MessageChannel
 * 的同线程异步语义一致。无需 EDT / invokeAndWait。
 *
 * [StandardTestDispatcher] 的行为：`batchScope.launch { batchSignalEnd() }` 将协程
 * 排入队列但不执行。测试代码同步运行，通过 [flushBatches] 手动调用 `batchSignalEnd()`
 * 消费批次。tearDown 中 [advanceUntilIdle] 排空残余协程。
 */
abstract class SignalTestBase {

    private val testScope = TestScope(StandardTestDispatcher())
    private lateinit var savedBatchScope: CoroutineScope

    @BeforeTest
    open fun setUp() {
        savedBatchScope = batchScope
        batchScope = testScope
        resetSignalGlobalState()
    }

    @AfterTest
    open fun tearDown() {
        testScope.advanceUntilIdle()
        batchScope = savedBatchScope
        resetSignalGlobalState()
    }

    /**
     * 同步消费当前批次。[StandardTestDispatcher] 下 `batchScope.launch` 排队的协程
     * 不会干扰——它们在 `advanceUntilIdle()` 中被排空（此时批次已空，no-op）。
     */
    fun flushBatches() {
        batchSignalEnd()
    }
}
