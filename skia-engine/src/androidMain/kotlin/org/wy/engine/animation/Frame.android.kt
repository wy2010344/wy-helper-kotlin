package org.wy.engine.animation

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import org.wy.engine.engineLogError

internal actual fun animNowMs(): Long = android.os.SystemClock.uptimeMillis()

private val mainHandler = Handler(Looper.getMainLooper())

/**
 * Android 端用 Choreographer 走 vsync：每帧精确对齐刷新信号。
 * Choreographer 须在带 Looper 的线程获取，故经主线程 Handler 中转。
 */
internal actual fun scheduleAnimFrame(callback: (nowMs: Long) -> Unit) {
    mainHandler.post {
        try {
            Choreographer.getInstance().postFrameCallback {
                try {
                    callback(animNowMs())
                } catch (err: Throwable) {
                    engineLogError("frame callback error", err)
                }
            }
        } catch (err: Throwable) {
            engineLogError("schedule frame error", err)
        }
    }
}

actual val DefaultFrameSource: FrameSource = loopFrameSource()
