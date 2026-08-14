package org.wy.engine

import com.wy.mve.Context
import kotlin.math.abs
import kotlin.math.max

abstract class GestureRecognizer {
    open val eagerness: Int = 0
    open fun onPointerDown(e: GlobalMouseEvent) {}
    open fun onPointerMove(e: GlobalMouseEvent) {}
    open fun onPointerUp(e: GlobalMouseEvent) {}
    open fun onPointerExit() {}

    fun accept() { arena?.accept(this) }
    fun reject() { arena?.reject(this) }
    internal var arena: GestureArena? = null
}

class GestureArena {
    private val recognizers = mutableListOf<GestureRecognizer>()
    private var winner: GestureRecognizer? = null

    fun add(r: GestureRecognizer) {
        r.arena = this
        recognizers.add(r)
    }

    fun dispatchDown(e: GlobalMouseEvent) {
        recognizers.toList().forEach { it.onPointerDown(e) }
    }

    fun dispatchMove(e: GlobalMouseEvent) {
        if (winner != null) {
            winner!!.onPointerMove(e)
        } else {
            recognizers.toList().forEach { it.onPointerMove(e) }
        }
    }

    fun dispatchUp(e: GlobalMouseEvent) {
        if (winner != null) {
            winner!!.onPointerUp(e)
        } else {
            recognizers.toList().forEach { it.onPointerUp(e) }
            sweep()
        }
    }

    fun dispatchExit() {
        recognizers.toList().forEach { it.onPointerExit() }
        clear()
    }

    fun accept(r: GestureRecognizer) {
        if (winner != null) return
        winner = r
        recognizers.filter { it !== r }.toList().forEach { it.reject() }
    }

    fun reject(r: GestureRecognizer) {
        recognizers.remove(r)
        if (recognizers.size == 1 && winner == null) {
            winner = recognizers.first()
        }
    }

    private fun sweep() {
        if (winner == null && recognizers.isNotEmpty()) {
            winner = recognizers.maxByOrNull { it.eagerness }
            recognizers.filter { it !== winner }.toList().forEach { it.reject() }
        }
    }

    fun clear() {
        recognizers.clear()
        winner = null
    }
}

open class TapRecognizer(
    private val onTap: () -> Unit
) : GestureRecognizer() {
    override val eagerness = 0
    private var downX = 0f
    private var downY = 0f

    override fun onPointerDown(e: GlobalMouseEvent) {
        downX = e.x
        downY = e.y
    }

    override fun onPointerMove(e: GlobalMouseEvent) {
        if (abs(e.x - downX) > 3f || abs(e.y - downY) > 3f) reject()
    }

    override fun onPointerUp(e: GlobalMouseEvent) {
        onTap()
        accept()
    }
}

open class DragRecognizer(
    private val onStart: ((GlobalMouseEvent) -> Unit)? = null,
    private val onUpdate: ((GlobalMouseEvent) -> Unit)? = null,
    private val onEnd: (() -> Unit)? = null,
    private val axis: Axis? = null
) : GestureRecognizer() {
    override val eagerness = 10
    private var startX = 0f
    private var startY = 0f
    private var started = false

    override fun onPointerDown(e: GlobalMouseEvent) {
        startX = e.x
        startY = e.y
        started = false
    }

    override fun onPointerMove(e: GlobalMouseEvent) {
        val dx = e.x - startX
        val dy = e.y - startY
        val dist = max(abs(dx), abs(dy))
        if (!started && dist > 5f) {
            val (primary, secondary) = when (axis) {
                Axis.X -> abs(dx) to abs(dy)
                Axis.Y -> abs(dy) to abs(dx)
                null -> dist to 0f
            }
            if (axis == null || primary > secondary) {
                started = true
                onStart?.invoke(e)
                accept()
            } else {
                reject()
            }
        }
        if (started) {
            onUpdate?.invoke(e)
        }
    }

    override fun onPointerUp(e: GlobalMouseEvent) {
        if (started) onEnd?.invoke()
        accept()
    }

    override fun onPointerExit() {
        if (started) onEnd?.invoke()
        reject()
    }
}

open class ScrollRecognizer(
    private val axis: Axis,
    private val onScroll: (delta: Float) -> Float
) : GestureRecognizer() {
    override val eagerness = 50

    fun handleWheel(delta: Float): Float {
        val consumed = onScroll(delta)
        return delta - consumed
    }
}

enum class Axis { X, Y }

val gestureArenaContext = Context<GestureArena?>(null)