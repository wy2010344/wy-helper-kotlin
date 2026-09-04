package org.wy.signal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * memo 防护与特性测试：循环 memo 检测、getValue 委托、重入栈清理。
 */
class MemoGuardTest : SignalTestBase() {

    // ===== 循环 memo 检测 =====

    @Test
    fun circularMemoDetectsDuplicate() {
        lateinit var inner: Memo<Int>
        // inner 调用时会触发 outer，outer 又触发 inner → checkEnter 检测到重复
        val outer = memo {
            inner() + 1
        }
        inner = memo {
            outer() + 1
        }

        assertFailsWith<Error> {
            outer()
        }
    }

    // ===== Memo.getValue 属性委托 =====

    @Test
    fun memoGetValueDelegate() {
        val base = createSignal(2)
        val doubled by memo { base.get() * 2 }

        assertEquals(4, doubled)

        base.set(5)
        flushBatches()
        assertEquals(10, doubled)
    }

    // ===== memo 栈异常后 resetStackMemos 可恢复 =====

    @Test
    fun memoStackRecoversAfterReset() {
        val s = createSignal(1)
        val m = memo { s.get() }
        // 首次正常
        assertEquals(1, m())

        // resetSignalGlobalState 会重置 stateVersion → memo 重算
        resetSignalGlobalState()
        assertEquals(1, m(), "resetSignalGlobalState 后 memo 应可正常重算（signal 值仍为 1）")
    }
}
