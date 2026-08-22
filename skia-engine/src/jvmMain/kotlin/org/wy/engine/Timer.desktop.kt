package org.wy.engine

import org.wy.lib.EmptyFun
import javax.swing.Timer

actual fun postDelayed(delayMs: Long, action: () -> Unit): EmptyFun {
    // Swing Timer 回调在 EDT 执行，与输入事件 / 渲染同线程，无需额外同步
    val timer = Timer(delayMs.toInt(), null)
    timer.isRepeats = false
    timer.addActionListener { action() }
    timer.start()
    return { timer.stop() }
}
