package org.wy.engine.animation

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 缓动函数：输入进度 0..1，输出映射进度 */
typealias EaseFn = (Float) -> Float

/**
 * 缓动函数集（参考 easings.net / react-native-reanimated / tween.js）。
 * in 系列为基础曲线；用 [easeOutFn]/[easeInOutFn]/[easeOutInFn] 包装得到其余形态。
 */
object EaseFns {
    /** 线性（无缓动） */
    val linear: EaseFn = { t -> t }

    /** N 次方缓动 */
    fun poly(n: Int): EaseFn = { t -> t.pow(n) }

    /** 二次方 */
    val quad: EaseFn = poly(2)

    /** 三次方 */
    val cubic: EaseFn = poly(3)

    /** 四次方 */
    val quart: EaseFn = poly(4)

    /** 五次方 */
    val quint: EaseFn = poly(5)

    /** 正弦曲线缓动 */
    fun sine(t: Float): Float = 1f - cos(t * PI.toFloat() / 2f)

    /** 指数曲线缓动 cubic-bezier(0.7, 0, 0.84, 0) */
    fun expo(t: Float): Float = if (t == 0f) 0f else 2f.pow(10f * (t - 1f))

    /** 圆形曲线缓动 cubic-bezier(0.55, 0, 1, 0.45) */
    fun circ(t: Float): Float = 1f - sqrt(1f - t * t)

    /**
     * Back 缓动（过冲回弹）。
     * @param s 过冲参数，常用 1.70158，越大过冲越明显
     */
    fun back(s: Float = 1.70158f): EaseFn = { t -> t * t * ((s + 1f) * t - s) }

    /** 弹跳动画（模拟小球落地） */
    fun bounceOut(t: Float): Float = when {
        t < 1f / 2.75f -> 7.5625f * t * t
        t < 2f / 2.75f -> {
            val t2 = t - 1.5f / 2.75f
            7.5625f * t2 * t2 + 0.75f
        }
        t < 2.5f / 2.75f -> {
            val t2 = t - 2.25f / 2.75f
            7.5625f * t2 * t2 + 0.9375f
        }
        else -> {
            val t2 = t - 2.625f / 2.75f
            7.5625f * t2 * t2 + 0.984375f
        }
    }

    /**
     * 果冻效果（指数衰减正弦），比 back 多抖几次。
     * @param bounciness 弹性
     */
    fun elasticOut(bounciness: Float = 1f): EaseFn {
        val p = bounciness * PI.toFloat()
        return { t ->
            1f - cos(t * PI.toFloat() / 2f).pow(3) * cos(t * p)
        }
    }

    /**
     * 可调振幅/频率的弹性缓动（简化自 tween.js）。
     * @param a 振幅
     * @param p 频率（越小周期越多）
     */
    fun elastic(a: Float = 1f, p: Float = 0.3f): EaseFn {
        val (amplitude, s) = if (a < 1f) {
            1f to p / 4f
        } else {
            a to p / (2f * PI.toFloat()) * asin(1f / a)
        }
        return { t ->
            if (t == 0f || t == 1f) t
            else {
                val tt = t - 1f
                -(amplitude * 2f.pow(10f * tt) * sin((tt - s) * 2f * PI.toFloat() / p))
            }
        }
    }
}

/** easeIn 包装为 easeOut */
fun easeOutFn(easeIn: EaseFn): EaseFn = { t -> 1f - easeIn(1f - t) }

/** easeIn 包装为 easeInOut */
fun easeInOutFn(easeIn: EaseFn): EaseFn = { t ->
    if (t < 0.5f) easeIn(t * 2f) / 2f
    else 1f - easeIn((1f - t) * 2f) / 2f
}

/** easeIn 包装为 easeOutIn（如 bounceOut 的 inOut 形态） */
fun easeOutInFn(easeIn: EaseFn): EaseFn = { t ->
    if (t < 0.5f) (1f - easeIn(1f - 2f * t)) / 2f
    else (1f + easeIn(2f * t - 1f)) / 2f
}

/**
 * 时长缓动动画工厂：tween(durationMs)(deltaX) 得到动画配置。
 * 进度 pc>=1 时精确落在目标值并自然结束。
 * @param fn 缓动曲线，默认线性
 */
fun tween(durationMs: Float, fn: EaseFn = EaseFns.linear): DeltaXAnimateConfig = { deltaX ->
    animationTime { diffTime, setDisplacement ->
        val pc = diffTime / durationMs
        if (pc < 1f) {
            setDisplacement(deltaX * fn(pc))
            false
        } else {
            setDisplacement(deltaX)
            true
        }
    }
}
