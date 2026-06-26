package com.wy.mve

import org.wy.lib.EmptyFun
import org.wy.lib.GetValue
import org.wy.lib.SetValue
import org.wy.signal.Memo


internal open class StateHolderI<Node> : StateHolder<Node> {
    open val parent: StateHolderI<Node>? = null

    open val parentContextIndex: Int = parent?.contexts?.size ?: 0
    internal val nodes: MutableList<ValueOrGetList<Node>> = mutableListOf()

    init {
        this.parent?.children?.add(this)
    }

    internal fun addNode(n: Node) {
        if (destroyed) {
            throw Error("已经结构构建，无法再继续添加")
        }
        nodes.add(Value(n))
    }

    private var endBuild = false

    internal fun create() {
        if (endBuild) {
            throw Error("已经初始化过了")
        }
        buildChildren()
        endBuild = true
    }

    internal open fun buildChildren() {}
    private val children = mutableSetOf<StateHolderI<Node>>()


    private val destroyList = mutableListOf<EmptyFun>()
    override fun addDestroy(destroy: EmptyFun) {
        if (destroyed) {
            throw Error("can not add to a destroyed holder")
        }
        destroyList.add(destroy)
    }

    final override var destroyed = false
        private set

    fun destroy() {
        if (destroyed) {
            println("duplicate destroy ${this}")
            return
        }
        destroyed = true
        children.forEach(::destroyHolder)
        destroyList.forEach(::run)
    }

    @Suppress("NewApi")
    override fun <T, K, O> renderForEach(
        forEach: (callback: (K, T) -> GetValue<O>) -> Unit,
        creater: Creater<Node, T, K, O>,
        duplicateInfo: DuplicateInfo
    ): Memo<*> {
        if(endBuild){
            throw Error("已经初始化过了")
        }
        val contextIndex = contexts.size
        val forEachSignal = object : Memo<ForEachModal<Node, T, K, O>>() {
            override fun get(old: ForEachModal<Node, T, K, O>?, inited: Boolean): ForEachModal<Node, T, K, O> {
                val cacheMap=old?.newMap?:mutableMapOf()
                val newMap = mutableMapOf<K, MutableList<EachValue<Node, T, O>>>()
                val thisTimeAdd = mutableListOf<EachValue<Node, T, O>>()
                val thisChildren = mutableListOf<EachValue<Node, T, O>>()
                var index = 0
                val getSignal = this
                forEach { key, value ->
                    val holders = cacheMap[key]
                    val ev: EachValue<Node, T, O>
                    if (holders.isNullOrEmpty()) {
                        ev = object : EachValue<Node, T, O>(getSignal) {
                            override val parent: StateHolderI<Node> = this@StateHolderI
                            override val parentContextIndex: Int = contextIndex
                            override fun buildChildren() {
                                creater(key, this)
                            }
                        }
                        thisTimeAdd.add(ev)
                    } else {
                        ev = holders.removeFirst()
                    }
                    ev.value = value
                    ev.index = index++
                    val envs = newMap.getOrPut(key) { mutableListOf() }
                    if (envs.isNotEmpty()) {
                        when (duplicateInfo) {
                            DuplicateInfo.WARN -> println("WARN: duplicate key $key (${envs.size + 1}x)")
                            DuplicateInfo.THROW -> throw IllegalStateException("Duplicate key: $key")
                            DuplicateInfo.IGNORE -> {}
                        }
                    }
                    envs.add(ev)
                    newMap[key] = envs
                    thisChildren.add(ev)
                    ev::invoke
                }

                return ForEachModal(cacheMap,newMap,thisTimeAdd,thisChildren)
            }
        }
        forEachSignal.afters.add { mit ->
            mit.cacheMap.forEach { (_, values) -> values.forEach { it.destroy() } }
            mit.thisTimeAdd.forEach {
                it.create()
            }
        }
        nodes.add(object : GetList<Node>() {
            override fun getList(): List<ValueOrGetList<Node>> {
               val out= forEachSignal()
                return out.thisChildren.flatMap(::getNodes)
            }
        })
        return forEachSignal
    }

    private companion object All {

        private fun <Node> getNodes(item: StateHolderI<Node>): List<ValueOrGetList<Node>> {
            return item.nodes
        }


        private fun <T, Node> findProvider(
            who: StateHolderI<Node>,
            context: Context<T>
        ): Pair<Context<T>, T>? {
            var holder: StateHolderI<Node>? = who
            var begin = holder?.contexts?.size ?: 0
            while (holder != null) {
                var i = begin - 1
                while (i > -1) {
                    val provder = holder.contexts[i]
                    if (provder.first == context) {
                        return provder as Pair<Context<T>, T>?
                    }
                    i--
                }
                begin = holder.parentContextIndex
                holder = holder.parent
            }
            return null
        }
    }


    private val contexts = mutableListOf<Pair<Context<*>, *>>()

    override fun <T> provide(context: Context<T>, value: T) {
        contexts.add(Pair(context, value))
    }

    override fun <T> consume(context: Context<T>): T {
        val holder = findProvider(this, context)
        if (holder != null) {
            return holder.second
        }
        return context.value
    }

    override fun renderNode(
        node: Node,
        after: SetValue<List<Node>>?,
        callback: StateHolder<Node>.() -> Unit
    ): GetValue<List<Node>> {
        addNode(node)
        return object : TargetStateHolder<Node>(node, after, callback) {
            override val parent = this@StateHolderI
        }.target
    }

    override fun getParent(): Node? {
        return consume(parentContext) as Node?
    }
}

private fun <Node> destroyHolder(stateHolder: StateHolderI<Node>) {
    stateHolder.destroy()
}

private data class ForEachModal<Node, T, K, O>(
    val cacheMap: MutableMap<K, MutableList<EachValue<Node, T, O>>>,
    val newMap: MutableMap<K, MutableList<EachValue<Node, T, O>>>,
    val thisTimeAdd: MutableList<EachValue<Node, T, O>>,
    val thisChildren: MutableList<EachValue<Node, T, O>>
)