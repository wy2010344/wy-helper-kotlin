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
    context: StateHolder<Node>
) : LayoutNode(context){


    override fun argPosition(direction: Direction): Float {
        val lp = layoutParent
        if (lp != null) {
            try {
                return lp.layoutValue(direction).childPosition(layoutIndex)
            } catch (err: LayoutError) {

            }
        }
        return 0f
    }

    override fun argSize(direction: Direction): LayoutSize {
        val x = layoutValue(direction)
        if (x.allowSizeFromChildren) {
            return LayoutSize(x.sizeFromChildren, true)
        }
        return sizeFromParent(direction)
    }

}