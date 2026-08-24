package org.wy.engine.animation

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** 弹簧物理参数 */
data class SpringBaseArg(
    /** 自由振荡角频率，越大收敛越快。默认 20 */
    val omega0: Float = 20f,
    /** 阻尼比：<1 欠阻尼（会来回振荡），=1 临界阻尼，>1 过阻尼。默认 1 */
    val zeta: Float = 1f,
)

/** 弹簧某时刻状态：displacement 为距终点的剩余距离，velocity 单位 px/s */
class SpringOutValue(val displacement: Float, val velocity: Float)

/**
 * 弹簧解析解（移植自 TS springBase，三分支：欠阻尼/临界/过阻尼）。
 *
 * @param elapsedTime 已流逝毫秒（内部转为秒）
 * @param deltaX 起始位置到目标的差（剩余距离初值）
 * @param initialVelocity 初速度 px/ms（内部转为 px/s 并取反，使正向与位移一致）
 * @param velocityWhenZeta1Plus zeta>=1 时是否计算速度（停止判定需要，纯位移场景可省）
 */
fun springBase(
    elapsedTime: Float,
    deltaX: Float,
    initialVelocity: Float,
    zeta: Float = 1f,
    omega0: Float = 20f,
    velocityWhenZeta1Plus: Boolean = false,
): SpringOutValue {
    val t = elapsedTime / 1000f
    val v0 = -initialVelocity * 1000f
    if (kotlin.math.abs(zeta - 1f) < 0.001f) {
        // 临界阻尼：(A + B·t)·e^(-ωt)，最快且不振荡。
        // ζ≈1 一并归并入本分支：欠阻尼公式的分母 omegaD=ω0√(1-ζ²) 在 ζ→1⁻ 时趋零，
        // sinCoeff 数值发散，Float 精度下不稳定
        val coeffA = deltaX
        val coeffB = v0 + omega0 * deltaX
        val envelope = exp(-omega0 * t)
        val displacement = (coeffA + coeffB * t) * envelope
        val velocity = if (velocityWhenZeta1Plus) {
            envelope * ((coeffA + coeffB * t) * -omega0 + coeffB)
        } else 0f
        return SpringOutValue(displacement, velocity)
    } else if (zeta < 1f) {
        // 欠阻尼：指数包络 × 衰减振荡
        val omegaD = omega0 * sqrt(1f - zeta * zeta)
        val cosCoeff = deltaX
        val sinCoeff = (v0 + zeta * omega0 * deltaX) / omegaD
        val cos1 = cos(omegaD * t)
        val sin1 = sin(omegaD * t)
        val envelope = exp(-zeta * omega0 * t)
        val displacement = envelope * (cosCoeff * cos1 + sinCoeff * sin1)
        val velocity = displacement * -omega0 * zeta +
                envelope * omegaD * (sinCoeff * cos1 - cosCoeff * sin1)
        return SpringOutValue(displacement, velocity)
    } else {
        // 过阻尼：两个衰减指数项叠加
        val cext = omega0 * sqrt(zeta * zeta - 1f)
        val gammaPlus = -zeta * omega0 + cext
        val gammaMinus = -zeta * omega0 - cext
        val coeffB = (gammaMinus * deltaX - v0) / (gammaMinus - gammaPlus)
        val coeffA = deltaX - coeffB
        val em = exp(gammaMinus * t)
        val ep = exp(gammaPlus * t)
        val displacement = coeffA * em + coeffB * ep
        val velocity = if (velocityWhenZeta1Plus) {
            coeffA * gammaMinus * em + coeffB * gammaPlus * ep
        } else 0f
        return SpringOutValue(displacement, velocity)
    }
}

/**
 * 由刚度-阻尼-质量换算弹簧参数：
 * ω0=√(k/m)，ζ=d/(2√(km))
 */
fun getZetaAndOmega0From(stiffness: Float, damping: Float, mass: Float): SpringBaseArg =
    SpringBaseArg(
        omega0 = sqrt(stiffness / mass),
        zeta = damping / (2f * sqrt(stiffness * mass)),
    )

/**
 * 停止判定：剩余距离与速度都足够小。
 * 阈值默认参考 Framer Motion（0.5px 在像素域几乎无法察觉）
 */
fun springIsStop(
    n: SpringOutValue,
    displacementThreshold: Float = 0.5f,
    velocityThreshold: Float = 10f,
): Boolean =
    kotlin.math.abs(n.displacement) < displacementThreshold &&
            kotlin.math.abs(n.velocity) < velocityThreshold

/** 弹簧动画配置 */
data class SpringAnimationArg(
    /** 物理参数 */
    val config: SpringBaseArg = SpringBaseArg(),
    /** 初速度 px/ms（如手势抬起时的速度），默认 0 */
    val initialVelocity: Float = 0f,
    /** 停止距离阈值 px */
    val displacementThreshold: Float = 0.5f,
    /** 停止速度阈值 px/s */
    val velocityThreshold: Float = 10f,
)

/** 弹簧动画工厂：spring()(deltaX) 得到动画配置；自然结束时精确落在目标值 */
fun spring(arg: SpringAnimationArg = SpringAnimationArg()): DeltaXAnimateConfig = { deltaX ->
    animationTime { diffTime, setDisplacement ->
        val out = springBase(
            diffTime, deltaX, arg.initialVelocity,
            arg.config.zeta, arg.config.omega0, true,
        )
        val stop = springIsStop(out, arg.displacementThreshold, arg.velocityThreshold)
        if (stop) {
            setDisplacement(deltaX)
        } else {
            setDisplacement(deltaX - out.displacement)
        }
        stop
    }
}
