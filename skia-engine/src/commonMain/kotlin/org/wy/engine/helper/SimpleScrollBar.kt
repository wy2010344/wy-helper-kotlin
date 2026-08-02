package org.wy.engine.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.Direction
import org.wy.engine.LayoutSize
import org.wy.engine.MouseEvent
import org.wy.engine.Node
import org.wy.engine.PlatformCanvas
import org.wy.engine.RectNode
import org.wy.engine.Scroll
import org.wy.engine.engineGlobalContext
import org.wy.engine.fillInnerRect
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.engine.scrollBarSize
import org.wy.engine.strokeInnerRect

/**
 * 方向由 [direction] 指定：Direction.y 为纵向（默认），Direction.x 为横向。
 * 纵向时轨道宽度固定 10f，滑块沿 y 移动；横向时轨道高度固定 10f，滑块沿 x 移动。
 */
abstract class SimpleScrollBar(
    context: StateHolder<Node,List<Node>>,
    val direction: Direction = Direction.y
) {
    abstract val scroll: Scroll

    init {
        object : RectNode(context), FlexParam {

            override val alignFix: Boolean = true
            override val alignItem: AlignItem = AlignItem.stretch
            override val directionJustify: DirectionJustify =
                DirectionJustify.start
            override val layout: LayoutDirection = FlexObject(this)
            override val argWidth: LayoutSize
                get() = if (direction == Direction.y) LayoutSize(10f, false) else super.argWidth
            override val argHeight: LayoutSize
                get() = if (direction == Direction.x) LayoutSize(10f, false) else super.argHeight

            override fun draw(canvas: PlatformCanvas) {
                strokeInnerRect(canvas)
                super.draw(canvas)
            }

            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                renderForEach({
                    val o = scroll.scrollBarSize(direction)
                    it(o != null, o)
                }) { key, et ->
                    if (key) {
                        object : RectNode(this) {
                            override val argWidth: LayoutSize
                                get() = if (direction == Direction.y) super.argWidth
                                else LayoutSize(et.value?.size ?: 0f, false)
                            override val argHeight: LayoutSize
                                get() = if (direction == Direction.y) LayoutSize(et.value?.size ?: 0f, false)
                                else super.argHeight
                            override val x: Float
                                get() = if (direction == Direction.x) et.value?.offset ?: 0f else super.x
                            override val y: Float
                                get() = if (direction == Direction.y) et.value?.offset ?: 0f else super.y

                            override fun mouseDown(e: MouseEvent) {
                                startDrag(e)
                            }

                            override fun draw(canvas: PlatformCanvas) {
                                fillInnerRect(canvas)
                                super.draw(canvas)
                            }

                            private fun startDrag(e: MouseEvent) {
                                val g = context!!.consume(engineGlobalContext)!!
                                val calc = et.value ?: return
                                val startValue = scroll.value
                                val startPointer = if (direction == Direction.y) e.globalY else e.globalX
                                val move = g.registerMouseMove { me ->
                                    val pointer = if (direction == Direction.y) me.y else me.x
                                    scroll.value = startValue + calc.moveToScroll(pointer - startPointer)
                                }
                                g.registerMouseUp {
                                    move()
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}
