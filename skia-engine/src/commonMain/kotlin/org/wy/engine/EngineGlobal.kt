package org.wy.engine

import com.wy.mve.Context
import org.wy.lib.EmptyFun

data class GlobalMouseEvent(
    val x: Float,
    val y: Float,
    val destroy: EmptyFun
)

typealias MouseCallback=(e:GlobalMouseEvent)-> Unit
data class GlobalWheelEvent(
    val x: Float,
    val y: Float,
    val delta: Float,
    val destroy: EmptyFun
)
typealias WheelCallback=(e:GlobalWheelEvent)-> Unit

interface EngineGlobal {
    fun registerMouseMove(callback:MouseCallback): EmptyFun
    fun registerMouseUp(callback: MouseCallback): EmptyFun
    fun registerMouseWheel(callback: WheelCallback): EmptyFun
}

val engineGlobalContext= Context<EngineGlobal?>(null)