package org.wy.engine.animation

import org.wy.engine.engineLogError
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

internal actual fun animNowMs(): Long = System.nanoTime() / 1_000_000L

private val frameExecutor = Executors.newSingleThreadScheduledExecutor { r ->
    Thread(r, "skia-engine-frame").apply { isDaemon = true }
}

/**
 * 桌面端无全局 vsync 入口：16ms 定时节拍产生帧，
 * 经 invokeLater 切回 EDT（UI 线程）执行，与事件/渲染同线程。
 */
internal actual fun scheduleAnimFrame(callback: (nowMs: Long) -> Unit) {
    frameExecutor.schedule({
        SwingUtilities.invokeLater {
            try {
                callback(animNowMs())
            } catch (err: Throwable) {
                engineLogError("frame callback error", err)
            }
        }
    }, 16, TimeUnit.MILLISECONDS)
}

actual val DefaultFrameSource: FrameSource = loopFrameSource()
