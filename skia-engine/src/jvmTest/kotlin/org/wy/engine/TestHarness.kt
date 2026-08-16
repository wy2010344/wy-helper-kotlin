package org.wy.engine

import com.wy.mve.Context
import com.wy.mve.DuplicateInfo
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import com.wy.mve.Creater
import org.wy.lib.EmptyFun
import org.wy.lib.GetValue
import org.wy.signal.Memo
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue

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

    @Suppress("UNCHECKED_CAST")
    override fun renderNode(
        node: N,
        callback: StateHolderWithNode<N, T>.() -> Unit
    ): GetValue<T> {
        return { emptyList<Any>() as T }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <NN, TT> renderNode(
        node: NN,
        config: com.wy.mve.ShareConfig<NN, TT>,
        callback: StateHolderWithNode<NN, TT>.() -> Unit
    ): GetValue<TT> {
        return { emptyList<Any>() as TT }
    }

    override fun getParent(): Any? {
        parentNode?.let { return it }
        // 模拟真实渲染树：root context 的 parent 是其根节点，否则 Node 构造会抛"需要找到parent"
        if (rootParent == null) {
            val g = consume(engineGlobalContext)
            rootParent = if (g != null) Node(null, g) else null
        }
        return rootParent
    }

    var parentNode: Any? = null
    private var rootParent: Node? = null

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
    private var capturedId: Int? = null
    private var capturedMove: ((PointerEvent) -> Unit)? = null
    private var capturedUp: ((PointerEvent) -> Unit)? = null

    private val keyCallbacks = mutableListOf<Pair<KeyPressCallback, () -> Unit>>()
    private val composingCallbacks = mutableListOf<Pair<ComposingTextCallback, () -> Unit>>()

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

    override var pressed: HitestResult? by createSignal(null)
    override var moveHitTest: HitestResult? by createSignal(null)
    override var lastPointerDevice: PointerDevice by createSignal(PointerDevice.Mouse)
    override var ctrl: Boolean = false
    override var shift: Boolean = false
    override var alt: Boolean = false
    override var meta: Boolean = false
    override var focused: Node? = null

    /** 活跃编辑器 = 聚焦的 EditableTextNode（派生，与 Register 语义一致）。 */
    override val activeEditor: EditableTextNode?
        get() = focused as? EditableTextNode

    private val selectionManagerInstance = SelectionManager()
    override val selectionManager: SelectionManager get() = selectionManagerInstance

    override fun capturePointer(
        id: Int,
        onMove: (PointerEvent) -> Unit,
        onUp: (PointerEvent) -> Unit
    ): PointerCapture {
        capturedId = id
        capturedMove = onMove
        capturedUp = onUp
        return object : PointerCapture {
            override fun release() {
                if (capturedId == id) {
                    capturedId = null
                    capturedMove = null
                    capturedUp = null
                }
            }
        }
    }

    fun simulatePointerMove(x: Float, y: Float) {
        capturedMove?.invoke(
            PointerEvent(type = PointerType.Move, x = x, y = y, rootX = x, rootY = y)
        )
    }

    fun simulatePointerUp(x: Float, y: Float) {
        capturedUp?.invoke(
            PointerEvent(type = PointerType.Up, x = x, y = y, rootX = x, rootY = y)
        )
        capturedId = null
        capturedMove = null
        capturedUp = null
    }

    fun simulateKeyPress(key: Char, code: KeyCode = KeyCode.Unknown, ctrl: Boolean = false, shift: Boolean = false, alt: Boolean = false) {
        val event = KeyEvent(key, code, ctrl, shift, alt, false)
        keyCallbacks.toList().forEach { (cb, _) -> cb(event) }
    }
}
