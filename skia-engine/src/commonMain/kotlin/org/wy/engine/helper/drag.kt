package org.wy.engine.helper

import com.wy.mve.StateHolder
import org.wy.engine.GlobalMouseEvent
import org.wy.engine.GestureRecognizer
import org.wy.engine.engineGlobalContext
import org.wy.lib.EmptyFun

/**
 * 基于全局鼠标回调的拖拽辅助函数。
 * 注册 mouseDown 回调，鼠标按下后自动注册 mouseMove / mouseUp，
 * 鼠标松开时自动清理，形成完整的拖拽生命周期。
 */
fun StateHolder<*, *>.drag(change: (e: GlobalMouseEvent) -> Unit) {
    val g = consume(engineGlobalContext)!!
    var destroyed = false
    var d0: EmptyFun = {}
    var d1: EmptyFun = {}
    var d2: EmptyFun = {}

    d0 = g.registerMouseDown {
        if (destroyed) return@registerMouseDown
        d1()
        d2()
        d1 = g.registerMouseMove { me ->
            if (!destroyed) change(me)
        }
        d2 = g.registerMouseUp {
            if (!destroyed) {
                try {
                    change(it)
                } finally {
                    destroyed = true
                    d1()
                    d2()
                }
            }
        }
    }
    addDestroy {
        if (!destroyed) {
            destroyed = true
            d0()
            d1()
            d2()
        }
    }
}

/**
 * 基于 GestureArena 的手势识别辅助函数。
 * 注册的 [recognizers] 会在每次鼠标按下时自动加入手势竞技场，
 * 用于竞争手势的识别（tap / drag / scroll 等）。
 * 当节点销毁时自动注销。
 */
fun StateHolder<*, *>.gesture(vararg recognizers: GestureRecognizer) {
    val g = consume(engineGlobalContext)!!
    recognizers.forEach { g.registerGestureRecognizer(it) }
    addDestroy {
        recognizers.forEach { g.unregisterGestureRecognizer(it) }
    }
}