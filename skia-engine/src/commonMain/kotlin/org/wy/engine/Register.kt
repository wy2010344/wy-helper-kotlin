package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.lib.EmptyFun
import org.wy.lib.contact
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue


internal class Register(context: StateHolder<*, *>?) : EngineGlobal {
    var popList by createSignal(emptyList<Pop>())
        private set

    var toastList by createSignal(emptyList<Toast>())
        private set

    override fun appendPop(callback: StateHolder<Node, List<Node>>.(Pop) -> Unit): Pop {
        val pop = object : Pop {
            override fun render(holder: StateHolder<Node, List<Node>>) {
                holder.callback(this)
            }
        }
        popList = popList.contact(pop)
        return pop
    }

    override fun removePop(pop: Pop): Boolean {
        val index = popList.indexOf(pop)
        if (index < 0) {
            return false
        }
        popList = popList.toMutableList().also {
            it.removeAt(index)
        }
        return true
    }

    override fun appendToast(callback: StateHolder<Node, List<Node>>.(Toast) -> Unit): Toast {
        val toast = object : Toast {
            override fun render(holder: StateHolder<Node, List<Node>>) {
                holder.callback(this)
            }
        }
        toastList = toastList.contact(toast)
        return toast
    }

    override fun removeToast(toast: Toast): Boolean {
        val index = toastList.indexOf(toast)
        if (index < 0) {
            return false
        }
        toastList = toastList.toMutableList().also {
            it.removeAt(index)
        }
        return true
    }

    override val selectionManager = SelectionManager(this)

    /** 渲染树根（由 [Renderer] 构建完成后注入），供全树遍历派生使用。 */
    override var rootNode: Node? = null
        internal set

    init {
        if (context != null) provide(context)
    }

    fun destroy() {
        captures.values.forEach { it.release() }
        captures.clear()
        keyPressList.clear(); composingList.clear()
    }

    override var pointerSelect by createSignal<PointerSelect?>(null)
    override var moveHitTest by createSignal<HitestResult?>(null)
    override var ctrl by createSignal(false)
    override var shift by createSignal(false)
    override var alt by createSignal(false)
    override var meta by createSignal(false)

    /**
     * 活跃编辑器 = 聚焦的 EditableTextNode（派生，无独立存储）。
     * 焦点是原始事实，不随节点销毁命令式清理；活性在消费端校验：
     * 焦点指向已销毁编辑器时视为无活跃编辑器，杜绝死节点几何/信号访问。
     */
    override val activeEditor: EditableTextNode?
        get() = (focused as? EditableTextNode)?.takeIf { !it.destroyed }

    /** 全局焦点信号。选区不随焦点切换隐式清除——指针会话由 pointerSelect/moveHitTest 推导。 */
    override var focused: Node? by createSignal<Node?>(null)

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

    // ---------- 渲染后效果 ----------

    private val postRenderEffects = mutableListOf<EmptyFun>()

    override fun addPostRenderEffect(effect: EmptyFun) {
        postRenderEffects.add(effect)
    }

    /** 消费并执行所有渲染后效果（由 [Renderer.render] 每帧调用）。 */
    internal fun drainPostRenderEffects() {
        val batch = postRenderEffects.toList()
        postRenderEffects.clear()
        batch.forEach { it() }
    }

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

    override fun registerKeyPress(callback: KeyPressCallback): EmptyFun =
        register(keyPressList, callback)

    override fun registerComposingText(callback: ComposingTextCallback): EmptyFun =
        register(composingList, callback)

    fun provide(context: StateHolder<*, *>) {
        context.provide(selectionManagerContext, selectionManager)
        context.provide(engineGlobalContext, this)
    }

    fun dispatchKeyPress(e: KeyEvent) {
        keyPressList.forEach { it.key(e) }
    }

    fun dispatchComposingText(text: String, cursorPosition: Int) {
        composingList.forEach { it.key(text, cursorPosition) }
    }
}


private fun <K> register(map: MutableMap<K, EmptyFun>, key: K): EmptyFun {
    val destroy: EmptyFun = { map.remove(key) }
    map[key] = destroy
    return destroy
}
