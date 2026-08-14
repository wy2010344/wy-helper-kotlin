package org.wy.engine

import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.memo
import org.wy.signal.setValue

class SelectionManager {
    private val registered = mutableMapOf<Int, Selectable>()
    private var nextId = 0

    fun register(s: Selectable): Int {
        val id = nextId++
        registered[id] = s
        return id
    }

    fun unregister(id: Int) {
        registered.remove(id)
    }

    private val anchor by createSignal<Pair<Int, Int>?>(null)
    private val focus by createSignal<Pair<Int, Int>?>(null)
    private val dragging by createSignal(false)

    private val ordered by memo {
        registered.values.sortedBy { it.selectionOrder }
    }

    val hasSelection: Boolean
        get() = anchor() != null && anchor() != focus()

    fun handleMouseDown(target: Selectable, localX: Float, localY: Float, shift: Boolean) {
        val offset = target.getOffsetAt(localX, localY)
        if (shift && anchor() != null) {
            focus = target.selectionOrder to offset
        } else {
            anchor = target.selectionOrder to offset
            focus = target.selectionOrder to offset
        }
        dragging = true
    }

    fun handleMouseMove(rootX: Float, rootY: Float) {
        if (!dragging) return
        val under = findSelectableAt(rootX, rootY)
        if (under != null) {
            val (lx, ly) = under.rootToLocal(rootX, rootY)
            val offset = under.getOffsetAt(lx, ly)
            focus = under.selectionOrder to offset
        }
    }

    fun handleMouseUp() {
        dragging = false
    }

    fun clearSelection() {
        anchor = null
        focus = null
    }

    fun selectAll(scope: Selectable) {
        anchor = scope.selectionOrder to 0
        focus = scope.selectionOrder to scope.textLength()
    }

    val selectedText by memo {
        val a = anchor() ?: return@memo null
        val f = focus() ?: return@memo null
        if (a == f) return@memo null
        buildString {
            val rangeStart = if (a.first <= f.first) a else f
            val rangeEnd = if (a.first <= f.first) f else a
            ordered.forEach { sel ->
                if (sel.selectionOrder < rangeStart.first || sel.selectionOrder > rangeEnd.first) return@forEach
                val s = if (sel.selectionOrder == rangeStart.first) rangeStart.second else 0
                val e = if (sel.selectionOrder == rangeEnd.first) rangeEnd.second else sel.textLength()
                if (s < e) append(sel.getText(s, e))
            }
        }
    }

    val coveredRects by memo {
        val a = anchor() ?: return@memo emptyList()
        val f = focus() ?: return@memo emptyList()
        if (a == f) return@memo emptyList()
        val rangeStart = if (a.first <= f.first) a else f
        val rangeEnd = if (a.first <= f.first) f else a
        ordered.flatMap { sel ->
            if (sel.selectionOrder < rangeStart.first || sel.selectionOrder > rangeEnd.first) return@flatMap emptyList()
            val s = if (sel.selectionOrder == rangeStart.first) rangeStart.second else 0
            val e = if (sel.selectionOrder == rangeEnd.first) rangeEnd.second else sel.textLength()
            if (s >= e) return@flatMap emptyList()
            sel.getRectsForRange(s, e).map { rect ->
                val (rx, ry) = sel.localToRoot(rect.left, rect.top)
                TextRect(rx, ry, rx + rect.width, ry + rect.height)
            }
        }
    }

    private fun findSelectableAt(rootX: Float, rootY: Float): Selectable? {
        for (sel in ordered) {
            val (lx, ly) = sel.rootToLocal(rootX, rootY)
            if (lx >= 0 && ly >= 0) {
                val maxW = sel.localToRoot(Float.MAX_VALUE, 0f).first
                val maxH = sel.localToRoot(0f, Float.MAX_VALUE).second
                if (rootX <= maxW && rootY <= maxH) return sel
            }
        }
        return null
    }
}

val selectionManagerContext = Context<SelectionManager?>(null)
