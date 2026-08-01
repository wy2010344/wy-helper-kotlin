package org.wy.engine

import com.wy.layout.Layout
import com.wy.mve.StateHolder
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.lib.EmptyFun
import org.wy.lib.GetValue
import org.wy.signal.TrackSignal
import org.wy.signal.createSignal
import java.awt.Color
import java.awt.Cursor
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
import javax.swing.JLayeredPane
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Desktop window that renders directly through the Skia pipeline.
 * No Compose Canvas layer involved.
 */

@OptIn(ExperimentalAtomicApi::class)
open class SkiaApp(width: Int = 800, height: Int = 600, context: StateHolder<Node>? = null) :
    Renderer(context) {
    open var title = "Skia Engine"
    private val w = createSignal(width)
    private val h = createSignal(height)

    final override val argWidth: LayoutSize
        get() = LayoutSize(w.value.toFloat(), false)
    final override val argHeight: LayoutSize
        get() = LayoutSize(h.value.toFloat(), false)
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

                override fun mouseExited(e: MouseEvent?) {
                    this@SkiaApp.mouseExit()
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

            // Shared KeyListener: forwards keys to the engine from wherever the
            // AWT focus currently sits (main canvas or the hidden input overlay).
            val keyListener = object : KeyListener {
                override fun keyTyped(e: AwtKeyEvent?) {
                    if (e == null) return
                    if (e.isControlDown || e.isAltDown || e.isMetaDown) return
                    val ch = e.keyChar
                    if (ch.code < 0x20 || ch.code == 0x7F || ch == Char(0xFFFF)) return
                    e.consume()
                    this@SkiaApp.keyPress(ch, KeyCode.Unknown, false, false, false)
                }

                override fun keyPressed(e: AwtKeyEvent?) {
                    if (e == null) return
                    val code = KeyCode.fromAwt(e.keyCode)
                    val isModifier = e.isControlDown || e.isAltDown || e.isMetaDown
                    if (code == KeyCode.Unknown && !isModifier) return
                    e.consume()
                    val ch = if (isModifier && e.keyCode in 65..90) {
                        (e.keyCode + 32).toChar()
                    } else {
                        e.keyChar
                    }
                    if (ch == Char(0xFFFF) && code == KeyCode.Unknown) return
                    this@SkiaApp.keyPress(
                        ch, code,
                        e.isControlDown,
                        e.isShiftDown,
                        e.isAltDown,
                        e.isMetaDown
                    )
                }

                override fun keyReleased(e: AwtKeyEvent?) {}
            }

            // Hidden JTextField for native text input (IME positioning + character filtering)
            val hiddenField = JTextField().apply {
                isVisible = true
                background = Color(0, 0, 0, 0)
                foreground = Color(0, 0, 0, 0)
                caretColor = Color(0, 0, 0, 0)
                border = null
                isOpaque = false
                setBounds(0, 0, 1, 1)
                enableInputMethods(true)
                focusTraversalKeysEnabled = false
                addKeyListener(keyListener)

                addInputMethodListener(object : java.awt.event.InputMethodListener {
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

                    override fun caretPositionChanged(e: java.awt.event.InputMethodEvent) {}
                })
            }

            window.rootPane.layeredPane.add(hiddenField, JLayeredPane.POPUP_LAYER)

            this@SkiaApp.setInputOverlayHandler(
                show = { x, y, w, h, fontSize ->
                    SwingUtilities.invokeLater {
                        hiddenField.setText("")
                        hiddenField.setBounds(x.toInt(), y.toInt(), 1, 1)
                        hiddenField.font = hiddenField.font.deriveFont(fontSize)
                        if (!hiddenField.requestFocusInWindow()) {
                            hiddenField.requestFocus()
                        }
                    }
                },
                hide = {
                    SwingUtilities.invokeLater {
                        hiddenField.setText("")
                        hiddenField.setBounds(0, 0, 1, 1)
                        skiaLayer.requestFocusInWindow()
                    }
                }
            )

            this@SkiaApp.setCursorHandler { type ->
                skiaLayer.cursor = when (type) {
                    CursorType.POINTER -> Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    CursorType.TEXT -> Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)
                    CursorType.DEFAULT -> Cursor.getDefaultCursor()
                }
            }

            skiaLayer.isFocusable = true
            skiaLayer.focusTraversalKeysEnabled = false
            skiaLayer.addKeyListener(keyListener)
            // When SkiaLayer gains focus, we can optionally re-focus hiddenField
            // if an EditableTextNode is still active.

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
            window.pack()
            window.isVisible = true
            // Request focus only after the window is actually shown, otherwise the
            // request is dropped and the keyboard has nowhere to land.
            skiaLayer.requestFocusInWindow()
            window.addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) {
                    this@SkiaApp.w.value = skiaLayer.width
                    this@SkiaApp.h.value = skiaLayer.height
                }
            })
        }
    }
}