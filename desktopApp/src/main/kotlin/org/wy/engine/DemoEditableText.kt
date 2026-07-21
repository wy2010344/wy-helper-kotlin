package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.mve.StateHolder
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue

/**
 * 演示 EditableTextNode 可编辑文本组件。
 *
 * 支持功能：
 * - 键盘输入文字
 * - Backspace / Delete 删除
 * - 鼠标点击定位光标
 * - 鼠标拖拽选择文本
 * - Ctrl+Z 撤销
 * - Ctrl+Y 重做
 * - Shift+方向键扩展选区
 * - Home / End 跳转行首/行尾
 * - Ctrl+A 全选
 */
fun demoEditableText(context: StateHolder<Node>) {
    object :RectNode(context), FlexParam{
        override val layout: LayoutDirection = FlexObject(this)
        override val argWidth: LayoutSize
            get() = LayoutSize(400f,true)
        override val alignFix: Boolean
            get() = true
        override val alignItem: AlignItem
            get() = AlignItem.stretch
        override fun StateHolder<Node>.argChildren() {

            var infoText by createSignal("Type something below. Ctrl+Z=Undo, Ctrl+Y=Redo")
            object : TextNode(this) {
                override val text: String get() = infoText
                override val fontSize: Float get() = 11f
                override val color: ColorInt get() = rgba(120, 120, 120)
            }

            val editor = object : EditableTextNode(this) {
                override val fontSize: Float get() = 16f
                override val color: ColorInt get() = rgba(0, 0, 0)
                override val cursorColor: ColorInt get() = rgba(0, 0, 0)
            }
        }
    }
}

/**
 * 带状态标签的可编辑文本组件。
 * 实时显示当前文本长度和光标位置。
 */
fun demoEditableTextWithStatus(context: StateHolder<Node>) {
    object :RectNode(context), FlexParam{
        override val layout: LayoutDirection = FlexObject(this)
        override val argWidth: LayoutSize
            get() = LayoutSize(400f, true)
        override val alignFix: Boolean
            get() = true
        override val alignItem: AlignItem
            get() = AlignItem.stretch

        override fun StateHolder<Node>.argChildren() {

            var statusText by createSignal("Chars: 0 | Cursor: 0")
            object : TextNode(this) {
                override val text: String get() = statusText
                override val fontSize: Float get() = 11f
                override val color: ColorInt get() = rgba(100, 100, 200)
            }

            val editor = object : EditableTextNode(this, maxHistorySize = 50) {
                override val fontSize: Float get() = 14f
                override val color: ColorInt get() = rgba(0, 0, 0)
                override val cursorColor: ColorInt get() = rgba(0, 0, 200)
            }
        }
    }
}

/**
 * 演示多个可编辑文本框共存。
 */
fun demoMultipleEditableText(context: StateHolder<Node>) {

    object :RectNode(context), FlexParam{
        override val layout: LayoutDirection = FlexObject(this)
        override val argWidth: LayoutSize
            get() = LayoutSize(400f, true)
        override val alignFix: Boolean
            get() = true
        override val alignItem: AlignItem
            get() = AlignItem.stretch

        override fun StateHolder<Node>.argChildren() {


            object : TextNode(this) {
                override val text: String get() = "Field #1:"
                override val fontSize: Float get() = 12f
                override val color: ColorInt get() = rgba(80, 80, 80)
            }

            object : EditableTextNode(this) {
                override val fontSize: Float get() = 14f
                override val color: ColorInt get() = rgba(0, 0, 0)
                override val cursorColor: ColorInt get() = rgba(200, 0, 0)
            }


            object : TextNode(this) {
                override val text: String get() = "Field #2:"
                override val fontSize: Float get() = 12f
                override val color: ColorInt get() = rgba(80, 80, 80)
            }

            object : EditableTextNode(this, maxHistorySize = 200) {
                override val fontSize: Float get() = 14f
                override val color: ColorInt get() = rgba(0, 0, 0)
                override val cursorColor: ColorInt get() = rgba(0, 150, 0)
            }
        }
    }
}
