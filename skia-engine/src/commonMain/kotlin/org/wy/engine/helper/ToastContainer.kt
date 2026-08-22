package org.wy.engine.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.Direction
import org.wy.engine.LayoutNode
import org.wy.engine.LayoutSize
import org.wy.engine.Node
import org.wy.engine.PlatformCanvas
import org.wy.engine.RectNode
import org.wy.engine.StartEnd
import org.wy.engine.Toast
import org.wy.engine.innerSize
import org.wy.engine.outerSize
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue

/**
 * Toast 容器：铺满窗口但整层不拦截命中（点击穿透到主界面），
 * 内部使用 flex 列表排列多个 toast，超出可视区域时可滚动。
 *
 * 业务可通过继承此类自定义容器样式。
 */
fun StateHolder<Node, List<Node>>.toastContainer(
    children:  StateHolderWithNode<Node, List<Node>>.() -> Unit,
){
    object : SimpleScrollNode(this){
        override fun acceptHit(x: Float, y: Float): Boolean {
            return false
        }

        override val contentAcceptHit: Boolean
            get() = false
        override val contentAlignItem: AlignItem
            get() = AlignItem.center
        override val notInLayout: Boolean
            get() = true

        override fun argSize(direction: Direction): LayoutSize {
            return LayoutSize(layoutParent?.innerSize(direction)?:0f,false)
        }

        override fun argPosition(direction: Direction): Float {
            return 0f
        }

        override fun StateHolderWithNode<Node, List<Node>>.contentChildren() {
            children()
        }

    }
}