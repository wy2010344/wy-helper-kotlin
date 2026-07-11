package org.wy.engine

import com.wy.mve.Context
import org.wy.lib.EmptyFun


typealias MouseCallback=(x: Float, y: Float)-> Unit
typealias WheelCallback=(x: Float, y: Float,deltaY: Float)-> Unit

interface EngineGlobal {
    fun registerMouseMove(callback:MouseCallback): EmptyFun
    fun registerMouseUp(callback: MouseCallback): EmptyFun
    fun registerMouseWheel(callback: WheelCallback): EmptyFun
}

val engineGlobalContext= Context<EngineGlobal?>(null)