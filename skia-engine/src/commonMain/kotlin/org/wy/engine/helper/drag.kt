package org.wy.engine.helper

import com.wy.mve.StateHolder
import org.wy.engine.GlobalMouseEvent
import org.wy.engine.Node
import org.wy.engine.engineGlobalContext


fun StateHolder<Node>.drag(
    change: (e: GlobalMouseEvent) -> Unit
) {
    val g = consume(engineGlobalContext)!!
    val d1 = g.registerMouseMove(change)
    g.registerMouseUp {
        change(it)
        d1()
        it.destroy()
    }
}