package org.wy.engine.animation

import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 可手动驱动时间的假帧源：测试中用 [pump] 精确控制帧推进，
 * 不依赖真实定时器（确定性、无等待）。
 */
private class FakeFrameSource : FrameSource {
    private class Sub(
        val callback: (diffTimeMs: Float) -> Boolean,
        val onFinish: (Boolean) -> Unit,
    ) {
        var canceled = false
        var finished = false

        fun fire(diff: Float) {
            if (canceled || finished) return
            // 与 loopFrameSource 契约一致：回调异常视为打断
            val stop = try {
                callback(diff)
            } catch (err: Throwable) {
                finished = true
                onFinish(false)
                return
            }
            if (stop) {
                finished = true
                onFinish(true)
            }
        }
    }

    private val subs = mutableListOf<Sub>()

    override fun subscribe(
        callback: (diffTimeMs: Float) -> Boolean,
        onFinish: (Boolean) -> Unit,
    ): FrameSubscription {
        val sub = Sub(callback, onFinish)
        subs.add(sub)
        // 与真实帧源契约一致：回调仅在 diffTime>0 时异步调用（pump 驱动）
        return object : FrameSubscription {
            override fun cancel() {
                if (!sub.canceled && !sub.finished) {
                    sub.canceled = true
                    onFinish(false)
                }
            }
        }
    }

    /** 推进 [elapsedMs] 毫秒，按 [stepMs] 步长逐帧回调所有活跃订阅 */
    fun pump(elapsedMs: Float, stepMs: Float = 16f) {
        var t = 0f
        while (t < elapsedMs) {
            t += stepMs
            subs.toList().forEach { it.fire(t) }
        }
    }
}

class AnimateSignalTest {

    // ---------- tween：按时长收敛 ----------

    @Test
    fun tweenConvergesToTargetAtDurationEnd() {
        val frames = FakeFrameSource()
        val a = AnimateSignal(0f, frames)

        // 中途未到时长：仍在动画中，值按缓动推进
        val done = a.animateTo(100f, tween(200f))
        frames.pump(100f)
        assertTrue(a.value in 1f..99f, "中途应有过渡值，实际 ${a.value}")
        assertTrue(a.onAnimation, "动画进行中")

        // 超过时长：收敛、结束
        frames.pump(200f)
        assertEquals(100f, a.value)
        assertFalse(a.onAnimation, "结束后 onAnimation 应为 false")
        runBlocking { assertTrue(done.await(), "自然完成应回报 true") }
    }

    @Test
    fun tweenValueFollowsEaseCurve() {
        val frames = FakeFrameSource()
        val a = AnimateSignal(0f, frames)
        a.animateTo(100f, tween(100f))
        frames.pump(50f, stepMs = 25f)
        assertEquals(50f, a.value, "线性 ease 在半程应为半位移")
    }

    @Test
    fun tweenRejectsNonPositiveDuration() {
        assertFailsWith<IllegalArgumentException> { tween(0f) }
        assertFailsWith<IllegalArgumentException> { tween(-100f) }
    }

    // ---------- spring：物理弹簧收敛 ----------

    @Test
    fun springConvergesWithinThreshold() {
        val frames = FakeFrameSource()
        val a = AnimateSignal(0f, frames)
        a.animateTo(200f, spring(SpringAnimationArg(config = SpringBaseArg(omega0 = 20f, zeta = 1f))))
        frames.pump(2000f)
        assertTrue(abs(a.value - 200f) < 0.5f, "弹簧应收敛到目标附近，实际 ${a.value}")
        assertFalse(a.onAnimation)
    }

    @Test
    fun springSnapsExactlyToTargetOnStop() {
        val frames = FakeFrameSource()
        val a = AnimateSignal(0f, frames)
        a.animateTo(80f, spring())
        frames.pump(1500f)
        assertEquals(80f, a.value, "停止判定后应精确落在目标值")
    }

    @Test
    fun springNearUnityzetaStaysFiniteAndConverges() {
        val frames = FakeFrameSource()
        val a = AnimateSignal(0f, frames)
        // ζ 极接近 1 时欠阻尼分支数值不稳，应归并入临界阻尼处理
        a.animateTo(200f, spring(SpringAnimationArg(config = SpringBaseArg(omega0 = 20f, zeta = 0.99995f))))
        frames.pump(3000f)
        assertTrue(a.value.isFinite(), "数值不得发散")
        assertEquals(200f, a.value, "snap 应保证精确落点")
    }

    // ---------- 帧回调异常兜底（僵尸动画） ----------

    @Test
    fun frameCallbackExceptionResolvesAsInterrupted() = runBlocking {
        val frames = FakeFrameSource()
        val a = AnimateSignal(0f, frames)
        var calls = 0
        val done = a.change(object : AnimateSignalConfig {
            override fun create(out: SilentDiff): ((Float) -> Boolean)? = { _ ->
                calls++
                if (calls == 2) error("boom")
                out.setDisplacement(10f)
                false
            }
        })

        frames.pump(100f)
        assertFalse(a.onAnimation, "异常后必须退出动画态，不得卡死")
        assertFalse(done.await(), "异常视为打断（回报 false），Deferred 必须完成")
        assertEquals(10f, a.value, "第一帧已写入的值保留")

        val frozen = a.value
        frames.pump(100f)
        assertEquals(frozen, a.value, "异常后帧链不得续期")
    }

    // ---------- 锁内 animateTo：先检查再产生副作用 ----------

    @Test
    fun animateToInsideFrameCallbackThrowsButSurvivesIfCaught() {
        val frames = FakeFrameSource()
        val a = AnimateSignal(0f, frames)
        var caught: Throwable? = null
        var n = 0
        a.change(object : AnimateSignalConfig {
            override fun create(out: SilentDiff): ((Float) -> Boolean)? = { _ ->
                try {
                    a.animateTo(50f)
                } catch (t: Throwable) {
                    caught = t
                }
                // setDisplacement 是绝对位移（相对基准），按帧数递增验证帧链仍在续期
                n++
                out.setDisplacement(n * 10f)
                false
            }
        })
        frames.pump(32f)
        assertTrue(caught is IllegalStateException, "锁内启动新动画应抛异常")
        assertTrue(a.onAnimation, "调用方捕获异常后当前动画应存活（副作用不得先行）")
        assertEquals(20f, a.value, "存活动画的后续帧继续推进")
    }

    // ---------- 打断与直接写 ----------

    @Test
    fun setInterruptsRunningAnimation() = runBlocking {
        val frames = FakeFrameSource()
        val a = AnimateSignal(0f, frames)
        val done = a.animateTo(100f, tween(500f))
        frames.pump(100f)

        a.set(42f)
        assertEquals(42f, a.value)
        assertFalse(a.onAnimation, "打断后不再处于动画态")
        assertFalse(done.await(), "被打断应回报 false")

        // 原动画的帧回调不应再改值
        frames.pump(600f)
        assertEquals(42f, a.value, "打断后旧动画不得继续推进")
    }

    @Test
    fun animateToSameValueDoesNotStartAnimation() {
        val frames = FakeFrameSource()
        val a = AnimateSignal(7f, frames)
        a.animateTo(7f, tween(100f))
        assertFalse(a.onAnimation, "零位移不应启动动画")
        assertEquals(7f, a.value)
    }

    @Test
    fun stopFreezesAtCurrentValue() {
        val frames = FakeFrameSource()
        val a = AnimateSignal(0f, frames)
        a.animateTo(100f, tween(400f))
        frames.pump(100f)
        val frozen = a.value
        a.stop()
        frames.pump(400f)
        assertEquals(frozen, a.value, "stop 后值应冻结")
        assertFalse(a.onAnimation)
    }

    // ---------- 外部增量：silentDiff / silentChangeTo / changeDiff ----------

    @Test
    fun silentDiffDuringAnimationShiftsTargetAndBase() {
        val frames = FakeFrameSource()
        val a = AnimateSignal(0f, frames)
        a.animateTo(100f, tween(200f))
        frames.pump(100f, stepMs = 50f)
        assertEquals(50f, a.value)

        // 外推 +30：当前值立即平移，基准与目标同步推进，动画时钟不变
        a.silentDiff(30f)
        assertEquals(80f, a.value, "当前值同步外推")
        assertEquals(130f, a.getTarget(), "目标同步推进")

        frames.pump(200f)
        assertEquals(130f, a.value, "动画应朝新目标收敛")
    }

    @Test
    fun silentDiffWithoutAnimationAddsDirectly() {
        val frames = FakeFrameSource()
        val a = AnimateSignal(10f, frames)
        a.silentDiff(5f)
        assertEquals(15f, a.value)
    }

    @Test
    fun silentChangeToRewritesTargetWithoutRestarting() {
        val frames = FakeFrameSource()
        val a = AnimateSignal(0f, frames)
        a.animateTo(100f, tween(200f))
        a.silentChangeTo(160f)
        assertTrue(a.onAnimation, "改目标不重启动画")
        assertEquals(160f, a.getTarget())
        frames.pump(250f)
        assertEquals(160f, a.value, "动画应朝新目标收敛")
    }

    @Test
    fun changeDiffAddsWhenIdle() {
        val frames = FakeFrameSource()
        val a = AnimateSignal(3f, frames)
        a.changeDiff(4f)
        assertEquals(7f, a.value)
    }

    // ---------- getTarget ----------

    @Test
    fun getTargetReturnsCurrentWhenIdle() {
        val frames = FakeFrameSource()
        val a = AnimateSignal(9f, frames)
        assertEquals(9f, a.getTarget())
    }

    // ---------- 回调内禁止修改（lock） ----------

    @Test
    fun mutationInsideFrameCallbackThrows() {
        val frames = FakeFrameSource()
        var caught: Throwable? = null
        val a = AnimateSignal(0f, frames)
        a.change(object : AnimateSignalConfig {
            override fun create(out: SilentDiff): ((Float) -> Boolean)? {
                return { _ ->
                    try {
                        a.set(5f)
                    } catch (t: Throwable) {
                        caught = t
                    }
                    false
                }
            }
        })
        frames.pump(16f)
        assertTrue(caught != null, "帧回调内的 set 应被 lock 拦截")
    }

    // ---------- 打断回报 false / 立即完成 ----------

    @Test
    fun stopDuringAnimationReportsNotFinished() = runBlocking {
        val frames = FakeFrameSource()
        val a = AnimateSignal(0f, frames)
        val done = a.animateTo(50f, tween(200f))
        assertTrue(a.onAnimation)

        a.stop()
        assertFalse(a.onAnimation)
        assertFalse(done.await())
    }

    @Test
    fun configReturningNullCompletesImmediately() = runBlocking {
        val frames = FakeFrameSource()
        val a = AnimateSignal(5f, frames)
        val done = a.change(object : AnimateSignalConfig {
            override fun create(out: SilentDiff): ((Float) -> Boolean)? = null
        })
        assertTrue(done.await())
        assertFalse(a.onAnimation)
    }
}
