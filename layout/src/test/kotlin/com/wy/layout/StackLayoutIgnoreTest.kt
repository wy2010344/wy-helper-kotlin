package com.wy.layout

import kotlin.test.Test
import kotlin.test.assertEquals


/** 模拟布局子节点 */
private data class MockStackChild(
    val size: Float,
    val ignored: Boolean = false,
)

/** 同时充当 StackObject / LayoutInsideObject / StackChildConvert 的模拟对象 */
private class MockStack(
    override val children: List<MockStackChild>,
    override val innerSize: Float = 100f,
    override val alignItem: AlignItem = AlignItem.center,
    override val alignFix: Boolean = false,
) : StackObject<MockStackChild>, LayoutInsideObject<MockStackChild>, StackChildConvert<MockStackChild> {
    override fun align(n: MockStackChild): Align? = null
    override fun outerSize(n: MockStackChild) = n.size
    override fun ignore(n: MockStackChild) = n.ignored
    override fun invoke(o: LayoutInsideObject<MockStackChild>): Layout = StackLayout(this, o, this)
}

private fun stack(
    vararg children: MockStackChild,
    innerSize: Float = 100f,
    alignItem: AlignItem = AlignItem.center,
    alignFix: Boolean = false,
): StackLayout<MockStackChild> {
    val mock = MockStack(children.toList(), innerSize, alignItem, alignFix)
    return StackLayout(mock, mock, mock)
}

class StackLayoutIgnoreTest {

    @Test
    fun ignoredChildDoesNotStretchContainerSize() {
        // alignFix=false 时容器尺寸 = 非 ignore 子节点的最大尺寸
        val layout = stack(
            MockStackChild(40f),
            MockStackChild(200f, ignored = true),
            alignItem = AlignItem.stretch,
        )
        assertEquals(40f, layout.sizeFromChildren)
        // ignore 子节点保留自身尺寸，不被 stretch 拉伸
        assertEquals(40f, layout.childSize(0))
        assertEquals(200f, layout.childSize(1))
    }

    @Test
    fun ignoredChildPositionIsZero() {
        val layout = stack(
            MockStackChild(40f),
            MockStackChild(200f, ignored = true),
            alignItem = AlignItem.stretch,
        )
        assertEquals(0f, layout.childPosition(0))
        // ignore 子节点位置由自身提供，不参与 align 定位
        assertEquals(0f, layout.childPosition(1))
    }

    @Test
    fun ignoredChildInCenterContainer() {
        val layout = stack(
            MockStackChild(40f),
            MockStackChild(200f, ignored = true),
            alignItem = AlignItem.center,
        )
        // 容器尺寸只由非 ignore 子节点决定，center 定位基于该尺寸
        assertEquals(0f, layout.childPosition(0))
        assertEquals(0f, layout.childPosition(1))
    }

    @Test
    fun ignoredChildWithFixedContainerSize() {
        // alignFix=true 时容器尺寸 = innerSize，与子节点无关
        val layout = stack(
            MockStackChild(40f),
            MockStackChild(200f, ignored = true),
            innerSize = 300f,
            alignItem = AlignItem.center,
            alignFix = true,
        )
        assertEquals(300f, layout.sizeFromChildren)
        assertEquals((300f - 40f) / 2f, layout.childPosition(0))
        // ignore 子节点仍保留自身尺寸、位置自决
        assertEquals(200f, layout.childSize(1))
        assertEquals(0f, layout.childPosition(1))
    }

    @Test
    fun allChildrenIgnoredSizeIsZero() {
        val layout = stack(
            MockStackChild(50f, ignored = true),
            MockStackChild(80f, ignored = true),
        )
        assertEquals(0f, layout.sizeFromChildren)
        assertEquals(50f, layout.childSize(0))
        assertEquals(80f, layout.childSize(1))
        assertEquals(0f, layout.childPosition(0))
        assertEquals(0f, layout.childPosition(1))
    }

    @Test
    fun noIgnoreBehavesAsClassicStack() {
        val layout = stack(MockStackChild(40f), MockStackChild(80f), alignItem = AlignItem.stretch)
        assertEquals(80f, layout.sizeFromChildren)
        assertEquals(0f, layout.childPosition(0))
        assertEquals(0f, layout.childPosition(1))
        assertEquals(80f, layout.childSize(0))
        assertEquals(80f, layout.childSize(1))
    }
}
