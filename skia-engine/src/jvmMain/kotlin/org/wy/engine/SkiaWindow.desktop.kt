package org.wy.engine

import com.wy.layout.Layout
import com.wy.mve.StateHolder
import org.jetbrains.skia.Canvas
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.lib.EmptyFun
import org.wy.lib.GetValue
import org.wy.signal.TrackSignal
import org.wy.signal.createSignal
import org.wy.signal.memo
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
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

/** 无边框窗口边缘缩放命中区宽（逻辑像素）。 */
private const val FRAME_EDGE = 6
/** 无边框窗口最小尺寸（逻辑像素）。 */
private const val MIN_FRAME_W = 640
private const val MIN_FRAME_H = 400

@OptIn(ExperimentalAtomicApi::class)
open class SkiaApp(width: Int = 800, height: Int = 600, context: StateHolder<*,*>? = null) :
    Renderer(context) {
    open var title = "Skia Engine"

    /**
     * 无边框模式：创建窗口时去掉系统标题栏，由应用在 tab 条自行实现
     * 拖拽移动 / 四边缩放 / 最小化 / 最大化 / 关闭（完整 Chrome 式）。
     * 子类 override 本 getter 建立关联（窗口参数不走构造函数）。
     */
    open val frameless: Boolean get() = false

    private val w = createSignal(width)
    private val h = createSignal(height)

    // ── 无边框窗口控制状态（tab 条回调与 AWT 监听共同读写）──
    /** JFrame 引用：三键 / 拖拽 / 缩放的落点，init 的 invokeLater 内创建。 */
    private var windowRef: JFrame? = null
    /** tab 条拖拽起点：按下时的屏幕坐标与窗口位置（屏幕坐标免疫 HiDPI 缩放）。 */
    private var dragStart: Point? = null
    private var dragOrigin: Point? = null
    /** 边缘缩放：激活区位标志（1=N 2=S 4=W 8=E）与按下时快照。 */
    private var resizeZone = 0
    private var resizeStart: Point? = null
    private var resizeBounds: Rectangle? = null
    /** 「最大化」前的 bounds：非 null 即最大化态（取工作区矩形，不盖任务栏）。 */
    private var restoreBounds: Rectangle? = null
    /** 上一拍指针是否停在边缘区（离开时把光标还原为默认）。 */
    private var hoverEdge = false

    /**
     * 当前窗口宽度（响应式信号读取）：窄窗口断点等场景使用。
     * 动态断点请在 layout/size getter、draw 或 memo（renderForEach 等）内读取以注册依赖；
     * argChildren / contentChildren 构建期读取只取快照（子树只构建一次，不会随窗口变化重建）。
     */
    val windowWidth: Float get() = w.value.toFloat()

    /**
     * 当前窗口高度（响应式信号读取）：金融看板等按窗口高度做行数预算的场景使用。
     * 动态断点请在 layout/size getter、draw 或 memo（renderForEach 等）内读取以注册依赖；
     * argChildren / contentChildren 构建期读取只取快照（子树只构建一次，不会随窗口变化重建）。
     */
    val windowHeight: Float get() = h.value.toFloat()

    final override val argWidth: LayoutSize
        get() = LayoutSize(w.value.toFloat(), false)
    final override val argHeight: LayoutSize
        get() = LayoutSize(h.value.toFloat(), false)
    private val skiaLayer = SkiaLayer()
    private fun cursorOf(v: CursorType): Cursor = when (v) {
        CursorType.POINTER -> Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        CursorType.TEXT -> Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)
        CursorType.CROSSHAIR -> Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
        CursorType.MOVE -> Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
        CursorType.RESIZE_NS -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)
        CursorType.RESIZE_EW -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
        CursorType.RESIZE_NWSE -> Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR)
        CursorType.RESIZE_NESW -> Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR)
        CursorType.WAIT -> Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
        // AWT 没有预定义的 HELP / NOT_ALLOWED，回退默认箭头
        CursorType.HELP, CursorType.NOT_ALLOWED, CursorType.DEFAULT -> Cursor.getDefaultCursor()
    }

    override fun setCursor(v: CursorType) {
        skiaLayer?.cursor = cursorOf(v)
    }

    /** 声明式 IME：EditableTextNode 活跃时由 Renderer 自动调用，把输入框移动到光标位置。 */
    override fun setInputOverlay(data: InputOverlayData) {
        hiddenField?.let {
            SwingUtilities.invokeLater {
                it.setText("")
                it.setBounds(data.x.toInt(), data.y.toInt(), 1, 1)
                it.font = it.font.deriveFont(data.fontSize)
                if (!it.requestFocusInWindow()) {
                    it.requestFocus()
                }
            }
        }
    }

    override fun hideInputOverlay() {
        hiddenField?.let {
            SwingUtilities.invokeLater {
                it.setText("")
                it.setBounds(0, 0, 1, 1)
                skiaLayer.requestFocusInWindow()
            }
        }
    }

    // ── 无边框窗口控制（tab 条空白拖拽 / 右上三键的引擎侧入口）──

    /**
     * 开始移动窗口：以屏幕坐标为基准，tab 条空白按下时调用。
     * 最大化中按下先还原 bounds，并让指针落在新窗口 tab 条中段（窗口不跳离指针）。
     */
    fun beginWindowDrag() {
        val win = windowRef ?: return
        val p = MouseInfo.getPointerInfo()?.location ?: return
        val r = restoreBounds
        if (r != null) {
            restoreBounds = null
            win.bounds = r
            win.location = Point(p.x - r.width / 2, p.y - 18)
        }
        dragStart = p
        dragOrigin = win.location
    }

    /** 关闭窗口：dispose 触发 windowClosed → destroy，生命周期照常收尾。 */
    fun closeWindow() {
        windowRef?.dispose()
    }

    /** 最小化到任务栏。 */
    fun minimizeWindow() {
        windowRef?.let { it.extendedState = it.extendedState or Frame.ICONIFIED }
    }

    /** 最大化 / 还原切换：最大化取工作区矩形（不盖任务栏），还原回进入前 bounds。 */
    fun toggleMaximizeWindow() {
        val win = windowRef ?: return
        val r = restoreBounds
        if (r != null) {
            restoreBounds = null
            win.bounds = r
        } else {
            restoreBounds = win.bounds
            win.bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        }
    }

    /** 边缘区检测：位标志 1=N 2=S 4=W 8=E（角同时命中两条边），坐标为 skiaLayer 逻辑像素。 */
    private fun edgeZoneAt(x: Int, y: Int): Int {
        var z = 0
        if (y < FRAME_EDGE) z = z or 1
        else if (y >= skiaLayer.height - FRAME_EDGE) z = z or 2
        if (x < FRAME_EDGE) z = z or 4
        else if (x >= skiaLayer.width - FRAME_EDGE) z = z or 8
        return z
    }

    /** 按边缘区选光标：角=斜向、边=正向（5=NW 10=SE 6=SW 9=NE）。 */
    private fun cursorOfZone(z: Int): Cursor = when (z) {
        5, 10 -> Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR)
        6, 9 -> Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR)
        1, 2 -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)
        4, 8 -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
        else -> Cursor.getDefaultCursor()
    }

    /** 拖拽位移：按下时窗口位置叠加「当前屏幕点 - 按下屏幕点」。 */
    private fun applyWindowMove() {
        val s = dragStart ?: return
        val o = dragOrigin ?: return
        val p = MouseInfo.getPointerInfo()?.location ?: return
        windowRef?.setLocation(o.x + p.x - s.x, o.y + p.y - s.y)
    }

    /** 边缘缩放：屏幕位移叠加到按下时 bounds；压到最小尺寸锚住（左/上缘钉住另一端不回弹）。 */
    private fun applyResize() {
        val s = resizeStart ?: return
        val b = resizeBounds ?: return
        val p = MouseInfo.getPointerInfo()?.location ?: return
        val dx = p.x - s.x
        val dy = p.y - s.y
        var x = b.x
        var y = b.y
        var bw = b.width
        var bh = b.height
        val z = resizeZone
        if (z and 8 != 0) bw = b.width + dx
        if (z and 2 != 0) bh = b.height + dy
        if (z and 4 != 0) bw = b.width - dx
        if (z and 1 != 0) bh = b.height - dy
        bw = bw.coerceAtLeast(MIN_FRAME_W)
        bh = bh.coerceAtLeast(MIN_FRAME_H)
        if (z and 4 != 0) x = b.x + b.width - bw
        if (z and 1 != 0) y = b.y + b.height - bh
        windowRef?.setBounds(x, y, bw, bh)
    }

    private var hiddenField: JTextField? = null
    override fun frameCallback() {
        SwingUtilities.invokeLater {
            skiaLayer?.needRender(true)
        }
    }

    init {
        SwingUtilities.invokeLater {
            val window = JFrame(title).apply {
                defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
                // 无边框：去系统标题栏，由 tab 条承担移动 / 缩放 / 三键
                if (frameless) isUndecorated = true
                preferredSize = Dimension(width, height)
            }
            this@SkiaApp.windowRef = window
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

                override fun windowDeactivated(e: WindowEvent?) {
                    // 窗口失焦：清空修饰键，防止按键残留
                    this@SkiaApp.clearModifiers()
                }
            })
            skiaLayer.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    // 点击由 mouseUp 内部处理，此处不需要额外操作
                }

                override fun mousePressed(e: MouseEvent?) {
                    if (e == null) return
                    // 无边框：边缘 6px 是缩放区，按下先于引擎吃掉（不触发内部控件）
                    if (this@SkiaApp.frameless) {
                        val zone = this@SkiaApp.edgeZoneAt(e.x, e.y)
                        if (zone != 0) {
                            this@SkiaApp.resizeZone = zone
                            this@SkiaApp.resizeStart = MouseInfo.getPointerInfo()?.location
                            this@SkiaApp.resizeBounds = windowRef?.bounds
                            return
                        }
                    }
                    // AWT 按键编号（1=左 2=中 3=右）与引擎 PointerButton 编号一致，直接透传；
                    // 右键按下由引擎派发 ContextMenu 而非 Down
                    this@SkiaApp.mouseDown(e.x.toFloat(), e.y.toFloat(), button = e.button)
                }

                override fun mouseReleased(e: MouseEvent?) {
                    if (e == null) return
                    // 窗口拖拽结束复位（缩放按下被吃掉时，释放同样不进引擎）
                    this@SkiaApp.dragStart = null
                    this@SkiaApp.dragOrigin = null
                    if (this@SkiaApp.resizeZone != 0) {
                        this@SkiaApp.resizeZone = 0
                        this@SkiaApp.resizeStart = null
                        this@SkiaApp.resizeBounds = null
                        return
                    }
                    this@SkiaApp.mouseUp(e.x.toFloat(), e.y.toFloat(), button = e.button)
                }

                override fun mouseExited(e: MouseEvent?) {
                    this@SkiaApp.mouseExit()
                }
            })
            skiaLayer.addMouseMotionListener(object : MouseMotionAdapter() {
                override fun mouseMoved(e: MouseEvent?) {
                    if (e == null) return

                    // 无边框边缘：光标提示缩放方向，此拍不进引擎（内部控件 hover 不受干扰）
                    if (this@SkiaApp.frameless && this@SkiaApp.resizeZone == 0) {
                        val zone = this@SkiaApp.edgeZoneAt(e.x, e.y)
                        if (zone != 0) {
                            this@SkiaApp.hoverEdge = true
                            skiaLayer.cursor = this@SkiaApp.cursorOfZone(zone)
                            return
                        }
                        if (this@SkiaApp.hoverEdge) {
                            this@SkiaApp.hoverEdge = false
                            skiaLayer.cursor = Cursor.getDefaultCursor()
                        }
                    }
                    this@SkiaApp.mouseMove(e.x.toFloat(), e.y.toFloat())
                }

                override fun mouseDragged(e: MouseEvent?) {
                    //拖拽是这里生效,这里是鼠标按下
                    if (e == null) return
                    // 缩放优先；窗口拖拽期间不进引擎（指针悬停态保持静止）
                    if (this@SkiaApp.resizeZone != 0) {
                        this@SkiaApp.applyResize()
                        return
                    }
                    if (this@SkiaApp.dragStart != null) {
                        this@SkiaApp.applyWindowMove()
                        return
                    }
                    this@SkiaApp.mouseMove(e.x.toFloat(), e.y.toFloat())
                }

            })
            skiaLayer.addMouseWheelListener(object : MouseWheelListener {
                override fun mouseWheelMoved(e: MouseWheelEvent?) {
                    if (e == null) return
                    // AWT 只暴露纵向精确滚轮量，没有横向分量。
                    // 约定：按住 Shift 时把纵向滚轮映射成横向滚动——这是桌面端
                    // 用普通鼠标驱动「横向滚动容器」的唯一入口（否则横向容器
                    // 永远收不到 wheelDeltaX）。无 Shift 时走纵向，交给滚动容器
                    // 按自身 scrollDirection 决定消费与否。
                    val rot = e.preciseWheelRotation.toFloat() * 40f
                    if (e.isShiftDown) {
                        this@SkiaApp.mouseWheel(
                            e.x.toFloat(), e.y.toFloat(),
                            deltaX = rot
                        )
                    } else {
                        this@SkiaApp.mouseWheel(
                            e.x.toFloat(), e.y.toFloat(),
                            deltaY = rot
                        )
                    }
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
                    // 每次键盘事件上报权威修饰键状态（含纯修饰键本身，如 Ctrl/Shift 单独按下）
                    this@SkiaApp.updateModifiers(e.isControlDown, e.isShiftDown, e.isAltDown, e.isMetaDown)
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

                override fun keyReleased(e: AwtKeyEvent?) {
                    if (e == null) return
                    this@SkiaApp.updateModifiers(e.isControlDown, e.isShiftDown, e.isAltDown, e.isMetaDown)
                }
            }

            // Hidden JTextField for native text input (IME positioning + character filtering)
            hiddenField = JTextField().apply {
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
                            // 先结束组合态（移除预编辑窗口），再写入已确认文本——
                            // 顺序颠倒会让最终文本落进即将被移除的窗口内
                            this@SkiaApp.composingText("", 0)
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
                        } else {
                            this@SkiaApp.composingText(composingText, composingText.length)
                        }
                    }

                    override fun caretPositionChanged(e: java.awt.event.InputMethodEvent) {}
                })
            }

            hiddenField?.let { window.rootPane.layeredPane.add(it, JLayeredPane.POPUP_LAYER) }

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