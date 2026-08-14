package org.wy.engine.helper

import com.wy.mve.StateHolder
import org.wy.engine.GlobalMouseEvent
import org.wy.engine.Node
import org.wy.engine.engineGlobalContext


fun StateHolder<*,*>.drag(change: (e: GlobalMouseEvent) -> Unit) {
    val g = consume(engineGlobalContext)!!
    var destroyed = false
    val d1 = g.registerMouseMove {
        if (!destroyed) change(it)
    }
    val d2 = g.registerMouseUp {
        if (!destroyed) {
            try {
                change(it)
            } finally {
                destroyed = true
                d1()
                d2()
                it.destroy()
            }
        }
    }
    addDestroy {
        if (!destroyed) {
            destroyed = true
            d1()
            d2()
        }
    }
}