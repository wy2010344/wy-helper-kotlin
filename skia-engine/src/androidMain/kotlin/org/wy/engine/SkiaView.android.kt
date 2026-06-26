package org.wy.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Android View that renders directly through the Skia pipeline.
 *
 * Uses SurfaceView for efficient rendering. Renders to an offscreen
 * Android Bitmap, then draws to the SurfaceView's Canvas.
 */
class SkiaView(
    context: Context,
    private val root: RenderNode
) : SurfaceView(context), SurfaceHolder.Callback {

    private val renderer = Renderer()
    private var renderThread: Thread? = null
    private var running = false

    init {
        holder.addCallback(this)
        renderer.onFrameRequested { postInvalidate() }
        root.resolveLayout()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        renderThread = Thread {
            while (running) {
                val canvas = holder.lockCanvas() ?: break
                try {
                    drawFrame(canvas)
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
                // Throttle: wait for next frame request
                synchronized(this) {
                    try { wait(16) } catch (_: InterruptedException) { break }
                }
            }
        }.also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        root.markDirty()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
    }

    private fun drawFrame(canvas: Canvas) {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)

        // Create offscreen Android Bitmap
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val offscreen = android.graphics.Canvas(bitmap)
        val pc = PlatformCanvas(offscreen)

        // Unified layout + draw to Skia
        renderer.render(root, pc, w.toFloat(), h.toFloat())

        // Blit to SurfaceView Canvas
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        bitmap.recycle()
    }
}
