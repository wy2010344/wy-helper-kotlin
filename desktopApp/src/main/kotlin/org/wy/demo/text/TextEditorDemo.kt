package org.wy.demo.text

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue

fun main() {
    object : SkiaApp(900, 700), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val directionJustify: DirectionJustify get() = DirectionJustify.center
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 8f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            sectionTitle("文本编辑 Demo")
            basicEditor()
            separator()
            multiLineEditor()
            separator()
            singleLineField()
            separator()
            editorGroup()
            separator()
            readOnlyRichText()
            separator()
            sectionTitle("嵌套滚动 Demo")
            nestedScrollDemo()
        }
    }
}

// ---- 公共辅助 ----

fun StateHolder<Node, List<Node>>.sectionTitle(text: String) {
    object : WrappedTextNode(this) {
        override val autoWidth: Boolean get() = true
        override val text: String get() = text
        override val fontSize: Float get() = 18f
        override val fontWeight: Int get() = 700
        override val color: ColorInt get() = rgba(40, 40, 60)
    }
}

fun StateHolder<Node, List<Node>>.separator() {
    object : RectNode(this), FlexParam {
        override val argHeight: LayoutSize get() = LayoutSize(1f, false)
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override fun draw(canvas: PlatformCanvas) {
            canvas.fillRect(0f, 0f, outerWidth, outerHeight, rgba(220, 220, 230))
            super.draw(canvas)
        }
    }
}

fun StateHolder<Node, List<Node>>.label(text: String, w: Float = 100f) {
    object : WrappedTextNode(this) {
        override val text: String get() = text
        override val fontSize: Float get() = 12f
        override val color: ColorInt get() = rgba(100, 100, 120)
    }
}

fun StateHolder<Node, List<Node>>.hint(text: String) {
    object : WrappedTextNode(this) {
        override val autoWidth: Boolean get() = true
        override val text: String get() = text
        override val fontSize: Float get() = 11f
        override val color: ColorInt get() = rgba(150, 150, 170)
    }
}

// ---- 1. 基础编辑器 ----

fun StateHolder<Node, List<Node>>.basicEditor() {
    var content by createSignal("Hello, World!")
    var cursorPos by createSignal(0)

    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val argWidth: LayoutSize get() = LayoutSize(400f, true)
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 4f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            hint("点击定位光标 · 拖拽选择 · Ctrl+Z 撤销 · Ctrl+Shift+Z 重做")

            val editor = object : EditableTextNode(this) {
                override var text by createSignal("Hello, World!\n这是一个多行编辑器。\n试试拖拽选择文本。")
                override val fontSize: Float get() = 15f
                override val color: ColorInt get() = rgba(30, 30, 30)
                override val cursorColor: ColorInt get() = rgba(0, 80, 200)
                override val selectionColor: ColorInt get() = rgba(0, 100, 220, 70)
            }

            object : WrappedTextNode(this) {
                override val autoWidth: Boolean get() = true
                override val text: String get() = "Ctrl+A 全选 · Ctrl+C/V/X 复制粘贴剪切 · Shift+方向键扩展选区"
                override val fontSize: Float get() = 11f
                override val color: ColorInt get() = rgba(130, 130, 150)
            }
        }
    }
}

// ---- 2. 多行编辑器 ----

fun StateHolder<Node, List<Node>>.multiLineEditor() {
    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val argWidth: LayoutSize get() = LayoutSize(400f, true)
        override val argHeight: LayoutSize get() = LayoutSize(120f, false)
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            object : EditableTextNode(this) {
                override var text by createSignal("第一行内容\n第二行内容\n第三行内容\n鼠标点击任意位置定位\n拖拽选择多行文本")
                override val fontSize: Float get() = 14f
                override val color: ColorInt get() = rgba(40, 40, 40)
                override val cursorColor: ColorInt get() = rgba(200, 50, 50)
                override val selectionColor: ColorInt get() = rgba(200, 80, 80, 70)
            }
        }
    }
}

// ---- 3. 单行输入框 ----

fun StateHolder<Node, List<Node>>.singleLineField() {
    object : RectNode(this), FlexParam {
        override val direction: Direction = Direction.x
        override val layout: LayoutDirection = FlexObject(this)
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 8f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            label("姓名:")

            object : EditableTextNode(this) {
                override var text by createSignal("")
                override val singleLine: Boolean get() = true
                override val fontSize: Float get() = 14f
                override val color: ColorInt get() = rgba(30, 30, 30)
                override val cursorColor: ColorInt get() = rgba(0, 120, 0)
                override val selectionColor: ColorInt get() = rgba(0, 120, 0, 70)
            }
        }
    }

    object : RectNode(this), FlexParam {
        override val direction: Direction = Direction.x
        override val layout: LayoutDirection = FlexObject(this)
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 8f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            label("邮箱:")

            object : EditableTextNode(this) {
                override var text by createSignal("")
                override val singleLine: Boolean get() = true
                override val fontSize: Float get() = 14f
                override val color: ColorInt get() = rgba(30, 30, 30)
                override val cursorColor: ColorInt get() = rgba(0, 120, 0)
                override val selectionColor: ColorInt get() = rgba(0, 120, 0, 70)
            }
        }
    }

    hint("单行模式: Enter 不换行, Tab 不插入制表符")
}

// ---- 4. 编辑器组 (Tab 切换) ----

fun StateHolder<Node, List<Node>>.editorGroup() {
    object : RectNode(this), FlexParam {
        override val direction: Direction = Direction.y
        override val layout: LayoutDirection = FlexObject(this)
        override val argWidth: LayoutSize get() = LayoutSize(400f, true)
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 4f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            label("字段 1")
            object : EditableTextNode(this) {
                override var text by createSignal("字段一")
                override val singleLine: Boolean get() = true
                override val fontSize: Float get() = 14f
            }

            label("字段 2")
            object : EditableTextNode(this) {
                override var text by createSignal("字段二")
                override val singleLine: Boolean get() = true
                override val fontSize: Float get() = 14f
            }

            label("字段 3 (多行)")
            object : EditableTextNode(this) {
                override var text by createSignal("多行内容\n可以换行")
                override val fontSize: Float get() = 14f
            }

            hint("点击任意字段获取焦点, Tab 键在字段间切换")
        }
    }
}

// ---- 5. 只读富文本展示 ----

fun StateHolder<Node, List<Node>>.readOnlyRichText() {
    object : RichTextNode(this) {
        override val spans: List<RichTextSpan> get() = listOf(
            RichTextSpan("富文本展示: ", RichTextStyle(fontSize = 14f, fontWeight = 700, color = rgba(60, 60, 80))),
            RichTextSpan("这是只读文本, 不可编辑。EditableTextNode 支持: ", RichTextStyle(fontSize = 13f, color = rgba(80, 80, 100))),
            RichTextSpan("鼠标点击定位、拖拽选择、键盘导航、撤销重做、复制粘贴", RichTextStyle(fontSize = 13f, fontWeight = 600, color = rgba(0, 80, 180)))
        )
        override val autoWidth: Boolean get() = true
    }
}