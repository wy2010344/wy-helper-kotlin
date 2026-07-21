package org.wy.engine

import com.wy.layout.Layout
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.lib.EmptyFun
import org.wy.lib.GetValue
import org.wy.signal.TrackSignal
import org.wy.signal.createSignal
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import java.awt.event.KeyEvent as AwtKeyEvent
import java.awt.event.KeyListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
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

    final override val argWidth: LayoutSize
        get() = LayoutSize(w.value.toFloat(),false)
    final override val argHeight: LayoutSize
        get() = LayoutSize(h.value.toFloat(),false)
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

            skiaLayer.isFocusable = true
            skiaLayer.addInputMethodListener(object : java.awt.event.InputMethodListener {
                override fun inputMethodTextChanged(e: java.awt.event.InputMethodEvent) {
                    val iter = e.text
                    val composingText = if (iter != null) {
                        val sb = StringBuilder()
                        var idx = iter.beginIndex
                        val end = iter.endIndex
                        while (idx < end) {
                            sb.append(iter.current())
                            iter.next()
                            idx++
                        }
                        sb.toString()
                    } else {
                        ""
                    }
                    val committedCount = e.committedCharacterCount
                    if (committedCount > 0) {
                        val committed = if (iter != null) {
                            val sb = StringBuilder()
                            iter.setIndex(iter.beginIndex)
                            var remaining = committedCount
                            while (remaining > 0 && iter.index < iter.endIndex) {
                                sb.append(iter.current())
                                iter.next()
                                remaining--
                            }
                            sb.toString()
                        } else ""
                        for (ch in committed) {
                            this@SkiaApp.keyPress(ch, KeyCode.Unknown, false, false, false)
                        }
                        this@SkiaApp.composingText("", 0)
                    } else {
                        this@SkiaApp.composingText(composingText, composingText.length)
                    }
                }
                override fun caretPositionChanged(e: java.awt.event.InputMethodEvent?) {}
            })
            skiaLayer.addKeyListener(object : KeyListener {
                override fun keyTyped(e: AwtKeyEvent?) {
                    if (e == null) return
                    if (e.isControlDown || e.isAltDown || e.isMetaDown) return
                    val ch = e.keyChar
                    if (ch.code < 0x20 || ch.code == 0x7F || ch == Char(0xFFFF)) return
                    val code = KeyCode.fromAwt(e.keyCode)
                    this@SkiaApp.keyPress(ch, code, false, false, false)
                }

                override fun keyPressed(e: AwtKeyEvent?) {
                    if (e == null) return
                    val code = KeyCode.fromAwt(e.keyCode)
                    val isModifier = e.isControlDown || e.isAltDown || e.isMetaDown
                    if (code == KeyCode.Unknown && !isModifier) return
                    val ch = if (isModifier && e.keyCode in 65..90) {
                        (e.keyCode + 32).toChar()
                    } else {
                        e.keyChar
                    }
                    this@SkiaApp.keyPress(ch, code, e.isControlDown, e.isShiftDown, e.isAltDown)
                }

                override fun keyReleased(e: AwtKeyEvent?) {}
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
            skiaLayer.requestFocusInWindow()
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