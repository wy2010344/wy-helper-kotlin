package org.wy.engine

import com.wy.mve.Context
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue

/**
 * Selectable：可被 SelectionManager 管理的节点接口。
 * 对标 Flutter SelectionRegistrar / Compose TextSelectionManager，
 * 但只保留最核心的"单焦点、单选中态"模型。
 *
 * 业务层只需：
 * - 实现此接口，自动享受 Cmd+A/C/X/V 等统一快捷键
 * - 通过 selectionManagerContext 读取当前选中的文本/矩形
 *
 * 引擎层负责：
 * - 选中态的互斥（同一时刻只有一个节点处于选中状态）
 * - 快捷键路由（Cmd+A 全选走这里，而不是各节点分散处理）
 * - 选中状态跨节点同步
 */
interface Selectable {
    val selectionOrder: Int get() = 0
    fun selectionText(): String?
    fun selectionRect(): RectF?
    val hasSelection: Boolean
    fun setSelected(selected: Boolean)
    fun selectAll()
}

/**
 * SelectionManager：管理全局唯一的选中节点。
 *
 * 设计原则：单焦点、单选中态。
 * - 任何时刻最多只有一个 Selectable 处于选中状态
 * - 选中逻辑委托给实现类（EditableTextNode 等）
 * - 提供统一的快捷键入口和读取接口
 */
class SelectionManager {
    private var active by createSignal<Selectable?>(null)

    /** 当前选中的节点（内部用） */
    val current: Selectable? get() = active

    /** 选中的文本内容，供 Cmd+C / 业务读取 */
    val selectedText: String? get() = active?.selectionText()

    /** 选中的矩形区域，供 UI 展示（popover 等） */
    val selectedRect: RectF? get() = active?.selectionRect()

    /** 是否存在选中 */
    val hasSelection: Boolean get() = active?.hasSelection() == true

    /**
     * 选中指定节点，自动清除上一个节点的选中态。
     * @param target 要选中的节点，传 null 则清空
     */
    fun select(target: Selectable?) {
        val old = active
        if (old === target) return
        old?.setSelected(false)
        active = target
        target?.setSelected(true)
    }

    /** 全选：委托给当前选中的节点 */
    fun selectAll() {
        active?.selectAll()
    }

    /** 清空选中 */
    fun clear() {
        select(null)
    }
}

val selectionManagerContext = Context<SelectionManager?>(null)
