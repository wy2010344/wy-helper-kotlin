package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.lib.EmptyFun
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue

internal class Register(context: StateHolder<*, *>?): EngineGlobal {
    override val selectionManager = SelectionManager()

    init {
        if (context != null) provide(context)
    }

    fun destroy() {
        captures.values.forEach { it.release() }
        captures.clear()
        keyPressList.clear(); composingList.clear()
    }

    override var pressed by createSignal<HitestResult?>(null)
    override var moveHitTest by createSignal<HitestResult?>(null)
    override var ctrl by createSignal(false)
    override var shift by createSignal(false)
    override var alt by createSignal(false)
    override var meta by createSignal(false)
    override var activeEditor by createSignal<EditableTextNode?>(null)
    private var focusedSignal by createSignal<Node?>(null)
    override var focused: Node?
        get() = focusedSignal
        set(value) {
            focusedSignal = value
            // 焦点即选中登记：SelectionManager 与焦点单一通道同步
            syncSelection(value)
        }

    /** 沿父链查找可被选中的节点，保证命中最深层叶子不是 Selectable 时也能登记 */
    private fun findSelectable(node: Node?): Selectable? {
        var cur = node
        while (cur != null) {
            if (cur is Selectable) return cur
            cur = cur.parent
        }
        return null
    }

    private fun syncSelection(node: Node?) {
        selectionManager.select(findSelectable(node))
    }

    // ---------- 指针捕获 ----------

    internal inner class Capture(
        val id: Int,
        val onMove: (PointerEvent) -> Unit,
        val onUp: (PointerEvent) -> Unit
    ) : PointerCapture {
        var released = false
            private set

        override fun release() {
            if (released) return
            released = true
            if (captures[id] === this) {
                captures.remove(id)
            }
        }
    }

    private val captures = mutableMapOf<Int, Capture>()

    override fun capturePointer(
        id: Int,
        onMove: (PointerEvent) -> Unit,
        onUp: (PointerEvent) -> Unit
    ): PointerCapture {
        captures[id]?.release()
        val capture = Capture(id, onMove, onUp)
        captures[id] = capture
        return capture
    }

    internal fun captured(id: Int): Capture? = captures[id]

    // ---------- 修饰键 ----------

    /** 由键盘按下 / 释放事件刷新四个修饰键信号 */
    internal fun applyModifiers(ctrl: Boolean, shift: Boolean, alt: Boolean, meta: Boolean) {
        this.ctrl = ctrl; this.shift = shift; this.alt = alt; this.meta = meta
    }

    /** 清空修饰键（窗口失焦 / 需要重置键盘状态时调用） */
    internal fun clearModifiers() {
        ctrl = false; shift = false; alt = false; meta = false
    }

    // ---------- 键盘 / IME ----------

    private val keyPressList = mutableMapOf<KeyPressCallback, EmptyFun>()
    private val composingList = mutableMapOf<ComposingTextCallback, EmptyFun>()

    override fun registerKeyPress(callback: KeyPressCallback): EmptyFun = register(keyPressList, callback)
    override fun registerComposingText(callback: ComposingTextCallback): EmptyFun = register(composingList, callback)

    fun provide(context: StateHolder<*, *>) {
        context.provide(selectionManagerContext, selectionManager)
        context.provide(engineGlobalContext, this)
    }

    fun dispatchKeyPress(e: KeyEvent) { keyPressList.forEach { it.key(e) } }
    fun dispatchComposingText(text: String, cursorPosition: Int) { composingList.forEach { it.key(text, cursorPosition) } }
}


private fun <K> register(map: MutableMap<K, EmptyFun>, key: K): EmptyFun {
    val destroy: EmptyFun = { map.remove(key) }
    map[key] = destroy
    return destroy
}
