package com.wy.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


/** 模拟布局子节点 */
private data class MockChild(
    val size: Float,
    val grow: Float = 0f,
    val ignored: Boolean = false,
)

/** 同时充当 FlexObject / LayoutInsideObject / FlexChildConvert 的模拟对象 */
private class MockFlex(
    override val children: List<MockChild>,
    override val innerSize: Float = 200f,
    override val gap: Float = 0f,
    override val directionJustify: DirectionJustify = DirectionJustify.grow,
    override val reverse: Boolean = false,
    override val directionFixBetweenWhenOne: DirectionFixBetweenWhenOne = DirectionFixBetweenWhenOne.center,
) : FlexObject<MockChild>, LayoutInsideObject<MockChild>, FlexChildConvert<MockChild> {
    override fun index(n: MockChild) = children.indexOfFirst { it === n }
    override fun grow(n: MockChild) = n.grow
    override fun outerSize(n: MockChild) = n.size
    override fun ignore(n: MockChild) = n.ignored
    override fun invoke(o: LayoutInsideObject<MockChild>): Layout = throw NotImplementedError()
}

private fun flex(
    vararg children: MockChild,
    innerSize: Float = 200f,
    gap: Float = 0f,
    justify: DirectionJustify = DirectionJustify.grow,
    reverse: Boolean = false,
): FlexLayout<MockChild> {
    val mock = MockFlex(children.toList(), innerSize, gap, justify, reverse)
    return FlexLayout(mock, mock, mock)
}

class FlexLayoutIgnoreTest {

    @Test
    fun ignoredChildNotCountedInGrowContainer() {
        val layout = flex(MockChild(10f, ignored = true), MockChild(30f), MockChild(40f))
        assertEquals(70f, layout.sizeFromChildren)
        // ignored child: position/size 不可用 → 抛 LayoutError
        assertFailsWith<LayoutError> { layout.childPosition(0) }
        assertFailsWith<LayoutError> { layout.childSize(0) }
        // 非 ignored 子节点正常
        assertEquals(0f, layout.childPosition(1))
        assertEquals(30f, layout.childPosition(2))
        assertEquals(30f, layout.childSize(1))
        assertEquals(40f, layout.childSize(2))
    }

    @Test
    fun ignoredChildSlotIsCurrentLengthInStartContainer() {
        val layout = flex(
            MockChild(10f, ignored = true), MockChild(30f), MockChild(40f),
            justify = DirectionJustify.start,
        )
        assertFailsWith<LayoutError> { layout.childPosition(0) }
        assertFailsWith<LayoutError> { layout.childSize(0) }
        assertEquals(0f, layout.childPosition(1))
        assertEquals(30f, layout.childPosition(2))
    }

    @Test
    fun ignoredGrowChildDoesNotConsumeRemaining() {
        val layout = flex(
            MockChild(0f, grow = 1f),
            MockChild(50f, grow = 1f, ignored = true),
            MockChild(0f, grow = 1f),
            justify = DirectionJustify.start,
        )
        // 容器高度 200，只有两个有效子节点平分
        assertEquals(100f, layout.childSize(0))
        assertEquals(100f, layout.childSize(2))
        // 被 ignore 的子节点：position/size 不可用
        assertFailsWith<LayoutError> { layout.childPosition(1) }
        assertFailsWith<LayoutError> { layout.childSize(1) }
        assertEquals(0f, layout.childPosition(0))
        assertEquals(100f, layout.childPosition(2))
    }

    @Test
    fun ignoredChildDoesNotConsumeGap() {
        val layout = flex(
            MockChild(30f), MockChild(20f, ignored = true), MockChild(40f),
            justify = DirectionJustify.start,
            gap = 10f,
        )
        // 只有两个有效子节点参与 gap：A 在 0，C 在 30+10=40
        assertEquals(0f, layout.childPosition(0))
        assertEquals(40f, layout.childPosition(2))
        // ignored child: 不可用
        assertFailsWith<LayoutError> { layout.childPosition(1) }
        assertFailsWith<LayoutError> { layout.childSize(1) }
    }

    @Test
    fun ignoredChildInCenterContainer() {
        val layout = flex(
            MockChild(20f, ignored = true), MockChild(30f),
            justify = DirectionJustify.center,
        )
        // 只有 B 参与居中：(200-30)/2 = 85
        assertEquals(85f, layout.childPosition(1))
        assertFailsWith<LayoutError> { layout.childSize(0) }
    }

    @Test
    fun noIgnoreBehavesAsClassicFlex() {
        val layout = flex(MockChild(30f), MockChild(40f))
        assertEquals(70f, layout.sizeFromChildren)
        assertEquals(0f, layout.childPosition(0))
        assertEquals(30f, layout.childPosition(1))
        assertEquals(30f, layout.childSize(0))
        assertEquals(40f, layout.childSize(1))
    }

    @Test
    fun allChildrenIgnoredInGrowContainer() {
        val layout = flex(MockChild(10f, ignored = true), MockChild(20f, ignored = true))
        assertEquals(0f, layout.sizeFromChildren)
        assertFailsWith<LayoutError> { layout.childPosition(0) }
        assertFailsWith<LayoutError> { layout.childPosition(1) }
        assertFailsWith<LayoutError> { layout.childSize(0) }
        assertFailsWith<LayoutError> { layout.childSize(1) }
    }
}
