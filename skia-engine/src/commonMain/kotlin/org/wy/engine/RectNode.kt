package org.wy.engine

import com.wy.layout.Layout
import com.wy.layout.LayoutError
import com.wy.layout.LayoutFun
import com.wy.mve.StateHolder
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.absoluteLayoutDirection
import org.wy.lib.GetValue
import org.wy.signal.memo

abstract class RectNode(
    context: StateHolder<Node,List<Node>>
) : LayoutNode(context){
    override fun argPosition(direction: Direction): Float {
        val lp = layoutParent ?: return 0f
        try {
            return lp.layoutValue(direction).childPosition(layoutIndex) + lp.padding(
                direction,
                StartEnd.start
            )
        } catch (err: LayoutError) {

        }
        return lp.padding(direction, StartEnd.start)
    }

    override fun argSize(direction: Direction): LayoutSize {
        val x = layoutValue(direction)
        if (x.allowSizeFromChildren) {
            return LayoutSize(x.sizeFromChildren, true)
        }
        return sizeFromParent(direction)
    }

}