package org.wy.engine

import com.wy.mve.Context
import com.wy.mve.DuplicateInfo
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import com.wy.mve.Creater
import org.wy.lib.EmptyFun
import org.wy.lib.GetValue
import org.wy.signal.Memo

/**
 * 测试用的 Mock StateHolder，提供必要的 context 实现。
 * 不依赖完整的 mve 渲染树，可独立测试节点行为。
 */
class TestStateHolder<N, T> : StateHolder<N, T> {
    private val contextStore = mutableMapOf<Context<*>, Any?>()
    private val nodeList = mutableListOf<N>()
    private val destroyList = mutableListOf<EmptyFun>()
    private var isDestroyed = false
    private val childrenHolders = mutableSetOf<StateHolder<*, *>>()

    override fun <TT> provide(context: Context<TT>, value: TT) {
        contextStore[context] = value
    }

    override fun addNode(n: N) {
        nodeList.add(n)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <TT> consume(context: Context<TT>): TT {
        contextStore[context]?.let { return it as TT }
        return context.value
    }

    override fun addDestroy(destroy: EmptyFun) {
        destroyList.add(destroy)
    }

    override val destroyed: Boolean get() = isDestroyed

    @Suppress("OVERRIDE_RESOLVES_TO_DIFFERENT_CLASSIFIER")
    override fun <Item, Key, Output> renderForEach(
        forEach: (callback: (Key, Item) -> GetValue<Output>) -> Unit,
        duplicateInfo: DuplicateInfo,
        creater: Creater<N, T, Item, Key, Output>
    ): Memo<*> = error("Not supported in test")

    override fun renderNode(
        node: N,
        callback: StateHolderWithNode<N, T>.() -> Unit
    ): GetValue<T> = error("Not supported in test")

    override fun <NN, TT> renderNode(
        node: NN,
        config: com.wy.mve.ShareConfig<NN, TT>,
        callback: StateHolderWithNode<NN, TT>.() -> Unit
    ): GetValue<TT> = error("Not supported in test")

    override fun getParent(): Any? = parentNode

    var parentNode: Any? = null

    fun destroy() {
        if (isDestroyed) return
        isDestroyed = true
        childrenHolders.forEach { (it as? TestStateHolder<*, *>)?.destroy() }
        destroyList.forEach { it() }
    }

    fun getNodes(): List<N> = nodeList.toList()
}

/**
 * 测试用的 Mock EngineGlobal 实现。
 */
class TestEngineGlobal : EngineGlobal {
    private val downCallbacks = mutableListOf<Pair<MouseCallback, () -> Unit>>()
    private val moveCallbacks = mutableListOf<Pair<MouseCallback, () -> Unit>>()
    private val upCallbacks = mutableListOf<Pair<MouseCallback, () -> Unit>>()
    private val wheelCallbacks = mutableListOf<Pair<WheelCallback, () -> Unit>>()
    private val keyCallbacks = mutableListOf<Pair<KeyPressCallback, () -> Unit>>()
    private val composingCallbacks = mutableListOf<Pair<ComposingTextCallback, () -> Unit>>()

    private val gestureRecognizers = mutableListOf<GestureRecognizer>()

    override fun registerMouseDown(callback: MouseCallback): EmptyFun {
        val destroy: () -> Unit = { downCallbacks.removeAll { it.first === callback } }
        downCallbacks.add(callback to destroy)
        return destroy
    }

    override fun registerMouseMove(callback: MouseCallback): EmptyFun {
        val destroy: () -> Unit = { moveCallbacks.removeAll { it.first === callback } }
        moveCallbacks.add(callback to destroy)
        return destroy
    }

    override fun registerMouseUp(callback: MouseCallback): EmptyFun {
        val destroy: () -> Unit = { upCallbacks.removeAll { it.first === callback } }
        upCallbacks.add(callback to destroy)
        return destroy
    }

    override fun registerMouseWheel(callback: WheelCallback): EmptyFun {
        val destroy: () -> Unit = { wheelCallbacks.removeAll { it.first === callback } }
        wheelCallbacks.add(callback to destroy)
        return destroy
    }

    override fun registerKeyPress(callback: KeyPressCallback): EmptyFun {
        val destroy: () -> Unit = { keyCallbacks.removeAll { it.first === callback } }
        keyCallbacks.add(callback to destroy)
        return destroy
    }

    override fun registerComposingText(callback: ComposingTextCallback): EmptyFun {
        val destroy: () -> Unit = { composingCallbacks.removeAll { it.first === callback } }
        composingCallbacks.add(callback to destroy)
        return destroy
    }

    override fun registerGestureRecognizer(r: GestureRecognizer) {
        gestureRecognizers.add(r)
    }

    override fun unregisterGestureRecognizer(r: GestureRecognizer) {
        gestureRecognizers.remove(r)
    }

    override val pressed: Boolean get() = false
    override val moveHitest: NodeWithPosition? get() = null
    override var focused: Node? = null

    override val gestureArena: GestureArena get() = GestureArena()
    override val selectionManager: SelectionManager get() = SelectionManager()

    override fun requestInputOverlay(x: Float, y: Float, w: Float, h: Float, fontSize: Float) {}
    override fun hideInputOverlay() {}
    override fun requestCursor(type: CursorType) {}

    fun simulateMouseDown(x: Float, y: Float) {
        downCallbacks.toList().forEach { (cb, _) -> cb(GlobalMouseEvent(x, y) {}) }
    }

    fun simulateMouseMove(x: Float, y: Float) {
        moveCallbacks.toList().forEach { (cb, _) -> cb(GlobalMouseEvent(x, y) {}) }
    }

    fun simulateMouseUp(x: Float, y: Float) {
        upCallbacks.toList().forEach { (cb, _) -> cb(GlobalMouseEvent(x, y) {}) }
    }

    fun simulateKeyPress(key: Char, code: KeyCode = KeyCode.Unknown, ctrl: Boolean = false, shift: Boolean = false, alt: Boolean = false) {
        val event = KeyEvent(key, code, ctrl, shift, alt, false)
        keyCallbacks.toList().forEach { (cb, _) -> cb(event) }
    }
}