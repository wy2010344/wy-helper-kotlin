package org.wy.engine

import android.os.Handler
import android.os.Looper
import org.wy.lib.EmptyFun

actual fun postDelayed(delayMs: Long, action: () -> Unit): EmptyFun {
    val handler = Handler(Looper.getMainLooper())
    val r = Runnable { action() }
    handler.postDelayed(r, delayMs)
    return { handler.removeCallbacks(r) }
}
