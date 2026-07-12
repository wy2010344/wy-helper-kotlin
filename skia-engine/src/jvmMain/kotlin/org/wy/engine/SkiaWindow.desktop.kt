package org.wy.engine

import com.wy.layout.LayoutFun
import com.wy.layout.absoluteLayout
import com.wy.mve.StateHolder
import kotlinx.coroutines.launch
import org.jetbrains.skia.*
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate
import org.wy.engine.layout.LayoutNode
import org.wy.lib.EmptyFun
import org.wy.signal.TrackSignal
import org.wy.signal.batchScope
import org.wy.signal.batchSignalEnd
import org.wy.signal.createSignal
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.util.Date
import java.util.concurrent.locks.ReentrantLock
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Desktop window that renders directly through the Skia pipeline.
 * No Compose Canvas layer involved.
 */

@OptIn(ExperimentalAtomicApi::class)
open class SkiaApp(width: Int = 800, height: Int = 600) : Renderer() {
    open var title = "Skia Engine"
    private val w = createSignal(width)
    private val h = createSignal(height)
    final override val width: Float
        get() = w.value.toFloat()

    final override val height: Float
        get() = h.value.toFloat()

    private val skiaLayer = SkiaLayer()

    override fun frameCallback() {
        SwingUtilities.invokeLater {
            skiaLayer.needRender(true)
        }
    }

    init {
        SwingUtilities.invokeLater {
            val window = JFrame(title).apply {
                defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
                preferredSize = Dimension(width, height)
            }
            val de = object : TrackSignal<String>() {
                override fun get(old: String?, inited: Boolean): String {
                    return title
                }

                override fun set(v: String, oldV: String?, inited: Boolean): EmptyFun? {
                    window.title = v
                    return null
                }
            }
            window.addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent?) {
                    this@SkiaApp.destroy()
                    de.dispose()
                }
            })
            skiaLayer.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    if (e == null) return
                    this@SkiaApp.mouseClick(e.x.toFloat(), e.y.toFloat())
                }

                override fun mousePressed(e: MouseEvent?) {
                    if (e == null) return
                    this@SkiaApp.mouseDown(e.x.toFloat(), e.y.toFloat())
                }

                override fun mouseReleased(e: MouseEvent?) {
                    if (e == null) return
                    this@SkiaApp.mouseUp(e.x.toFloat(), e.y.toFloat())
                }
            })
            skiaLayer.addMouseMotionListener(object : MouseMotionAdapter() {
                override fun mouseMoved(e: MouseEvent?) {
                    if (e == null) return

                    this@SkiaApp.mouseMove(e.x.toFloat(), e.y.toFloat())
                }

                override fun mouseDragged(e: MouseEvent?) {
                    //拖拽是这里生效,这里是鼠标按下
                    if (e == null) return
                    this@SkiaApp.mouseMove(e.x.toFloat(), e.y.toFloat())
                }
            })
            skiaLayer.addMouseWheelListener(object : MouseWheelListener {
                override fun mouseWheelMoved(e: MouseWheelEvent?) {
                    if (e == null) return
                    this@SkiaApp.mouseWheel(
                        e.x.toFloat(), e.y.toFloat(), e.preciseWheelRotation.toFloat() * 40f
                    )
                }
            })

            skiaLayer.renderDelegate = SkikoRenderDelegate { canvas, _, _, _ ->
                if (this@SkiaApp.scheduled) {
                    return@SkikoRenderDelegate
                }
                val scale = skiaLayer.contentScale
                canvas.scale(scale, scale)
                this@SkiaApp.render(PlatformCanvas(canvas))
            }
            skiaLayer.attachTo(window.contentPane)
            skiaLayer.needRender()
            window.addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) {
                    this@SkiaApp.w.value = skiaLayer.width
                    this@SkiaApp.h.value = skiaLayer.height
                }
            })
            window.pack()
            window.isVisible = true
        }
    }
}