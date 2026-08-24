package org.wy.engine.animation

import org.wy.engine.engineLogError

/**
 * 帧源抽象：由平台提供节拍（desktop≈16ms EDT 节拍 / android=Choreographer vsync）。
 *
 * 线程约束：所有方法必须在 UI 线程调用（与引擎渲染/事件同线程）。
 *
 * 契约（移植自 TS createSubscribeRequestAnimationFrame）：
 * - [callback] 仅以 diffTime > 0 调用（自订阅时刻起的毫秒数），且不会在
 *   subscribe 的调用栈内同步触发，因此订阅方可以先登记再订阅；
 * - callback 抛异常视为打断：帧链终止并回调 onFinish(false)；
 * - callback 返回 true = 动画自然结束 → onFinish(true)；
 * - [FrameSubscription.cancel] = 外部取消 → onFinish(false)；
 * - onFinish 至多回调一次。
 */
fun interface FrameSource {
    fun subscribe(
        callback: (diffTimeMs: Float) -> Boolean,
        onFinish: (success: Boolean) -> Unit,
    ): FrameSubscription
}

/** 帧订阅句柄：外部取消 */
interface FrameSubscription {
    fun cancel()
}

/** 平台单调时钟（毫秒）。各平台必须使用同一种时钟供 diff 计算一致 */
internal expect fun animNowMs(): Long

/** 请求在下一帧执行 callback（参数为当时时间戳毫秒）。实现须转发到 UI 线程执行 */
internal expect fun scheduleAnimFrame(callback: (nowMs: Long) -> Unit)

/** 平台默认帧源 */
expect val DefaultFrameSource: FrameSource

/**
 * 通用订阅循环：cancel 标志、diffTime 计算、onFinish 幂等。
 * 平台 actual 只需提供 [animNowMs] 与 [scheduleAnimFrame] 两个原语。
 */
internal fun loopFrameSource(): FrameSource = object : FrameSource {
    override fun subscribe(
        callback: (diffTimeMs: Float) -> Boolean,
        onFinish: (success: Boolean) -> Unit,
    ): FrameSubscription {
        var canceled = false
        var finished = false
        val start = animNowMs()

        // 自引用 lambda：未结束时持续请求下一帧
        lateinit var tick: (Long) -> Unit
        tick = { nowMs ->
            if (!canceled && !finished) {
                val diff = (nowMs - start).toFloat()
                // 用户回调异常不得产生僵尸动画：视为打断，
                // 帧链终止且 onFinish(false) 必达，Deferred/onAnimation 状态正常收尾
                var ok = true
                val stop = if (diff <= 0f) false else {
                    try {
                        callback(diff)
                    } catch (err: Throwable) {
                        ok = false
                        engineLogError("animation frame error", err)
                        true
                    }
                }
                if (stop) {
                    finished = true
                    onFinish(ok)
                } else {
                    scheduleAnimFrame(tick)
                }
            }
        }
        scheduleAnimFrame(tick)

        return object : FrameSubscription {
            override fun cancel() {
                if (!canceled && !finished) {
                    canceled = true
                    onFinish(false)
                }
            }
        }
    }
}
