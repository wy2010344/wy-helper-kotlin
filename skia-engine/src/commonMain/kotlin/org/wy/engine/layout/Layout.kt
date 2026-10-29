package org.wy.engine.layout

import com.wy.layout.Align
import com.wy.layout.AlignItem
import com.wy.layout.DirectionFixBetweenWhenOne
import com.wy.layout.DirectionJustify
import com.wy.layout.FlexObject
import com.wy.layout.LayoutFun
import com.wy.layout.StackObject
import com.wy.layout.absoluteLayout
import org.wy.engine.Direction
import org.wy.engine.LayoutDep
import org.wy.engine.LayoutNode
import org.wy.engine.opposite
import org.wy.engine.outerSize


interface FlexParam{
    val direction: Direction
        get() = Direction.y
    val directionJustify: DirectionJustify
        get() = DirectionJustify.grow
    val directionFixBetweenWhenOne: DirectionFixBetweenWhenOne
        get() = DirectionFixBetweenWhenOne.center
    val alignFix: Boolean
        get() = false
    val alignItem: AlignItem
        get() = AlignItem.center
    val gap: Float
        get() = 0f
    val reverse: Boolean
        get() = false
}

interface LayoutDirection {
    val layoutX: LayoutFun<LayoutNode>
    val layoutY: LayoutFun<LayoutNode>
}

class FlexObject(val arg: FlexParam):Flex(){
    /**
     * 所属节点：[arg] 通常就是实现了 [FlexParam] 的那个节点本身。
     * 审计时用它把「父级读取子节点尺寸」记到正确的节点上。
     */
    override val ownerNode: LayoutNode?
        get() = arg as? LayoutNode
    override val gap: Float
        get() = arg.gap
    override val reverse: Boolean
        get() = arg.reverse
    override val alignItem: AlignItem
        get() = arg.alignItem
    override val alignFix: Boolean
        get() = arg.alignFix
    override val direction: Direction
        get() = arg.direction
    override val directionFixBetweenWhenOne: DirectionFixBetweenWhenOne
        get() = arg.directionFixBetweenWhenOne
    override val directionJustify: DirectionJustify
        get() = arg.directionJustify
}

val absoluteLayoutDirection:LayoutDirection = object : LayoutDirection{
    override val layoutX: LayoutFun<LayoutNode>
        get() = absoluteLayout()
    override val layoutY: LayoutFun<LayoutNode>
        get() = absoluteLayout()

}



interface GrowChild{
    fun argGrow(direction: Direction): Float=0f
    val growX
        get() = argGrow(Direction.x)
    val growY
        get() = argGrow(Direction.y)
}

interface IgnoreFlex{
    val ignore : Boolean
}
interface AlignChild{
    fun argAlign(direction: Direction): Align?
    val alignX
        get() = argAlign(Direction.x)
    val alignY
        get() = argAlign(Direction.y)
}
abstract class Flex : FlexObject<LayoutNode> ,LayoutDirection{
    open val direction: Direction = Direction.x

    /**
     * 本布局所属的节点（用于审计时把"父级读取子节点尺寸"记到正确的节点上）。
     *
     * 默认 null：纯布局对象（如绝对定位布局）没有对应节点。
     */
    open val ownerNode: LayoutNode? get() = null

    final override fun index(n: LayoutNode): Int {
        return n.layoutIndex
    }

    final override fun grow(n: LayoutNode): Float {
        if(n is GrowChild){
            return when(direction){
                Direction.x -> n.growX
                Direction.y -> n.growY
            }
        }
        return 0f
    }

    final override fun outerSize(n: LayoutNode): Float {
        // 审计观测点：父级在此读取子节点尺寸。
        // 记录 READ 边，与子节点的 ASK 边配对即可判定成环——无需 auditor 复述引擎规则。
        val owner = ownerNode
        if (owner != null && LayoutDep.probeMode) {
            LayoutDep.probeRead(owner, n, direction)
        }
        return n.outerSize(direction)
    }

    override fun ignore(n: LayoutNode): Boolean {
        return n is IgnoreFlex && n.ignore
    }
    override val layoutX: LayoutFun<LayoutNode>
        get() = layout(Direction.x)
    override val layoutY: LayoutFun<LayoutNode>
        get() = layout(Direction.y)

    fun layout(direction: Direction) = when (this.direction == direction) {
        true -> this
        false -> cross
    }
    open val alignItem: AlignItem = AlignItem.center

    open val alignFix: Boolean=false
    private val cross = object : StackObject<LayoutNode> {
        override val alignItem: AlignItem
            get() = this@Flex.alignItem

        override val alignFix: Boolean
            get() = this@Flex.alignFix

        override fun align(n: LayoutNode): Align? {
            // 注意：这里必须 return，否则 when 只是语句、值被丢弃 → AlignChild 自对齐永远失效
            if (n is AlignChild) {
                return when (direction) {
                    Direction.x -> n.alignY
                    Direction.y -> n.alignX
                }
            }
            return null
        }

        override fun outerSize(n: LayoutNode): Float {
            // 审计观测点：交叉轴上父级读取子节点尺寸（方向取交叉轴）。
            val owner = this@Flex.ownerNode
            if (owner != null && LayoutDep.probeMode) {
                LayoutDep.probeRead(owner, n, direction.opposite)
            }
            return n.outerSize(direction.opposite)
        }

        override fun ignore(n: LayoutNode): Boolean {
            return n is IgnoreFlex && n.ignore
        }
    }
}
