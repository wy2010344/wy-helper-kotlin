package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.helper.slider
import org.wy.engine.helper.switch
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.lib.StoreRef
import org.wy.signal.getValue

// ════════════════════════════════════════════════════
// 交互控件（demo 布局包装，交互与样式由 helper 组件提供）
// ════════════════════════════════════════════════════
internal fun StateHolder<Node,List<Node>>.dToggleRow(
    label: String,
    checked: StoreRef<Boolean>,
    focusOrder: Int? = null
): RectNode = object : RectNode(this), FlexParam {
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.x
    override val directionJustify: DirectionJustify get() = DirectionJustify.between
    override val alignItem: AlignItem get() = AlignItem.center
    override val alignFix: Boolean get() = true
    override val argHeight: LayoutSize get() = LayoutSize(22f, false)

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        dLabel({ label }, 13f, TEXT, 500)
        switch({ checked.value }, { checked.value = it }, focusOrder = focusOrder)
    }
}

internal fun StateHolder<Node,List<Node>>.dSlider(
    label: String,
    value: StoreRef<Float>,
    focusOrder: Int? = null,
    width: Float = 220f
): RectNode = object : RectNode(this), FlexParam {
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.x
    override val alignItem: AlignItem get() = AlignItem.center
    override val alignFix: Boolean get() = true
    override val argHeight: LayoutSize get() = LayoutSize(20f, false)
    override val gap: Float get() = 12f

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        dLabel({ label }, 13f, TEXT, 500, width = 90f)
        slider({ value.value }, { value.value = it }, focusOrder = focusOrder, width = width)
        dLabel({ "${(value.value * 100).toInt()}" }, 12f, TEXT2)
    }
}
