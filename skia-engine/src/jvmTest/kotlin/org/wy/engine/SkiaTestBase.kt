@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.wy.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.wy.signal.batchScope
import org.wy.signal.batchSignalEnd
import org.wy.signal.resetSignalGlobalState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * skia-engine 测试基类：用 [TestScope]（[StandardTestDispatcher]）替换生产 [batchScope]，
 * 协程排队不立即执行，测试内 `batchSignalEnd()` 同步消费批次。
 * 每个测试前后重置所有 signal 全局状态（G + stackMemos），防止跨测试污染。
 */
abstract class SkiaTestBase {

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
     * 同步消费当前批次。
     */
    fun flushBatches() {
        batchSignalEnd()
    }
}
