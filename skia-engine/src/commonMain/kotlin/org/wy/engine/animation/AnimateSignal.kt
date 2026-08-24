package org.wy.engine.animation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.wy.lib.QuoteValue
import org.wy.signal.OneSetStoreRef
import org.wy.signal.createLateSignal
import org.wy.signal.createSignal

/** 动画帧回调：diffTime 为自配置创建起的毫秒数，返回 true 表示自然结束 */
typealias AnimateFrame = (diffTimeMs: Float) -> Boolean

/**
 * 位移协调器（移植自 TS SilentDiff）：动画期间以 [initValue] 为基准输出位移；
 * 外部输入（如滚动中手指继续移动）通过 [silentDiff]/[silentChangeTo] 同步平移
 * 基准与目标，与动画输出互不干扰、不打断动画时钟。
 */
class SilentDiff internal constructor(
    private val valueSet: QuoteValue<Float>,
    val getCurrent: () -> Float,
    private val onProcess: ((Float) -> Unit)? = null,
    /** 动画目标值；无目标型配置时为 null */
    var target: Float? = null,
) {
    private var initValue: Float = getCurrent()

    /** 当前动画位移量（相对基准） */
    fun getDisplacement(): Float = getCurrent() - initValue

    /** 输出动画位移 */
    fun setDisplacement(n: Float) {
        val nv = initValue + n
        valueSet(nv)
        onProcess?.invoke(nv)
    }

    /** 外部增量：基准与目标同步平移，当前值外推，不改动画时钟 */
    fun silentDiff(n: Float) {
        initValue += n
        valueSet(getCurrent() + n)
        target?.let { target = it + n }
    }

    /** 改写目标但不重启动画 */
    fun silentChangeTo(n: Float) {
        val t = requireNotNull(target) { "not a target function" }
        silentDiff(n - t)
    }
}

/**
 * 动画配置工厂（对应 TS AnimateSignalConfig）：
 * [create] 返回帧回调；返回 null 表示无事可做、动画立即完成。
 * create 执行期间信号处于锁定状态，禁止修改。
 */
fun interface AnimateSignalConfig {
    fun create(out: SilentDiff): AnimateFrame?
}

/**
 * 位移式配置构造器（对应 TS createAnimationTime）：
 * 直接输出位移量，回调返回 true 结束动画。
 */
inline fun animationTime(
    crossinline frame: (diffTimeMs: Float, setDisplacement: (Float) -> Unit) -> Boolean,
): AnimateSignalConfig = AnimateSignalConfig { out ->
    { diffTime -> frame(diffTime) { n -> out.setDisplacement(n) } }
}

/** deltaX 配置工厂：tween(durationMs)/spring(...) 的产物类型 */
typealias DeltaXAnimateConfig = (deltaX: Float) -> AnimateSignalConfig

/**
 * 动画信号（移植自 TS AnimateSignal）：把"值随时间平滑变化"表达为响应式信号。
 *
 * 线程约束：非线程安全。必须在单一 UI 线程创建与操作（与引擎渲染/事件同线程），
 * lock/lastCancel 等状态均无同步保护。
 *
 * - 渲染/memo 中读取 [value] 即建立依赖，每帧写值自动触发重绘；
 * - 函数式动画：每帧由 diffTime 纯计算位移，可随时打断、无状态漂移；
 * - [animateTo] 返回 Deferred：true=自然完成，false=被打断（含帧回调异常）。
 */
class AnimateSignal private constructor(
    ref: OneSetStoreRef<Float>,
    private val frames: FrameSource,
) {
    /** 以初值创建，使用平台默认帧源 */
    constructor(initValue: Float, frames: FrameSource = DefaultFrameSource) : this(createLateSignal(initValue), frames)

    /** 绑定已有的一次性写信号 */
    constructor(ref: OneSetStoreRef<Float>) : this(ref, DefaultFrameSource)

    private val valueSet: QuoteValue<Float> = ref.getOnlySet()
    private val getFun: () -> Float = { ref.get() }

    /** 当前值。在渲染/memo 中读取即建立响应式依赖 */
    val value: Float get() = getFun()

    private val onAnimationStore = createSignal(false)

    /** 是否有动画进行中（响应式信号） */
    val onAnimation: Boolean get() = onAnimationStore.get()

    private class Running(
        val out: SilentDiff,
        val deferred: CompletableDeferred<Boolean>,
    ) {
        var subscription: FrameSubscription? = null
    }

    private var lastCancel: Running? = null

    /** 锁定标志：帧回调与配置构造期间禁止修改，防止重入 */
    private var lock = false

    /**
     * 结束当前动画。先清引用再取消订阅：
     * cancel 触发的 onFinish(false) 找不到活跃动画，天然幂等。
     */
    private fun finish(success: Boolean) {
        val o = lastCancel ?: return
        lastCancel = null
        o.subscription?.cancel()
        o.deferred.complete(success)
        onAnimationStore.set(false)
    }

    /** 目标值：动画中为配置目标（含 silentDiff 推进），否则当前值 */
    fun getTarget(): Float {
        lastCancel?.out?.target?.let { return it }
        return value
    }

    /** 直接写值并打断进行中的动画。帧回调内禁止调用（lock 抛异常） */
    fun set(n: Float): Float {
        check(!lock) { "禁止在此时修改" }
        finish(false)
        valueSet(n)
        return n
    }

    /** 冻结在当前值（打断动画但保持值不变） */
    fun stop() {
        set(value)
    }

    /** 增量写值并打断动画 */
    fun changeDiff(n: Float) {
        set(value + n)
    }

    /** 外部增量：动画中平移基准与目标（不打断时钟），空闲时直接加值 */
    fun silentDiff(n: Float) {
        val o = lastCancel
        if (o != null) o.out.silentDiff(n) else valueSet(value + n)
    }

    /** 改写目标不重启动画；空闲时直接写值 */
    fun silentChangeTo(n: Float) {
        val o = lastCancel
        if (o != null) o.out.silentChangeTo(n) else valueSet(n)
    }

    /**
     * 启动自定义动画。
     * @param config 配置工厂（构造期间持锁）
     * @param onProcess 每帧额外回调（拿到写入后的绝对值）
     * @param target 目标值登记用（供 getTarget/silentChangeTo）
     * @return true=自然完成或无事可做，false=被打断
     */
    fun change(
        config: AnimateSignalConfig,
        onProcess: ((Float) -> Unit)? = null,
        target: Float? = null,
    ): Deferred<Boolean> {
        check(!lock) { "禁止在此时修改" }
        finish(false)
        lock = true
        val out = SilentDiff(valueSet, getFun, onProcess, target)
        val frame: AnimateFrame?
        try {
            frame = config.create(out)
        } finally {
            lock = false
        }
        if (frame == null) return CompletableDeferred(true)

        val deferred = CompletableDeferred<Boolean>()
        val running = Running(out, deferred)
        lastCancel = running
        onAnimationStore.set(true)
        // 先登记再订阅：契约保证 subscribe 调用栈内不会同步触发回调
        running.subscription = frames.subscribe({ diff ->
            lock = true
            val stop = try {
                frame(diff)
            } finally {
                lock = false
            }
            stop
        }, { success -> finish(success) })
        return deferred
    }

    /** 动画到目标值（默认弹簧）。零位移时立即完成 */
    fun animateTo(
        n: Float,
        config: DeltaXAnimateConfig = defaultSpringAnimationConfig,
        onProcess: ((Float) -> Unit)? = null,
    ): Deferred<Boolean> {
        // 先检查锁再打断旧动画：锁内调用抛异常时，进行中的动画不受副作用影响
        check(!lock) { "禁止在此时修改" }
        finish(false)
        val diff = n - value
        if (diff != 0f) return change(config(diff), onProcess, n)
        return CompletableDeferred(true)
    }

    /** 有配置则动画过去，否则直接写值 */
    fun changeTo(
        n: Float,
        config: DeltaXAnimateConfig?,
        onProcess: ((Float) -> Unit)? = null,
    ): Deferred<Boolean> =
        if (config != null) animateTo(n, config, onProcess)
        else {
            set(n)
            CompletableDeferred(true)
        }
}

/** 默认弹簧动画（临界阻尼 ω0=20） */
val defaultSpringAnimationConfig: DeltaXAnimateConfig = spring()
