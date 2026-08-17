package org.wy.demo.text

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.helper.PopoverManager
import org.wy.engine.helper.PopoverStyle
import org.wy.engine.helper.Theme
import org.wy.demo.helper.hint
import org.wy.demo.helper.page
import org.wy.engine.helper.popoverManagerContext
import org.wy.demo.helper.sectionTitle
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue

fun main() {
    object : SkiaApp(900, 700), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 0f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            provide(popoverManagerContext, PopoverManager(engineGlobal))
            page(gap = 16f) {
                sectionTitle("Popover 词典浮层")
                hint("选中下方文本区域中的任意单词，即可弹出词典释义浮层；点击浮层外部可关闭。")

                dictionaryTextArea()
            }
        }
    }
}

fun StateHolder<Node, List<Node>>.dictionaryTextArea() {
    val c = Theme.current.colors
    val r = Theme.current.radius

    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val argWidth: LayoutSize get() = LayoutSize(640f, false)
        override val argHeight: LayoutSize get() = LayoutSize(220f, false)
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 0f

        private var currentDismiss: org.wy.lib.EmptyFun? = null

        override fun draw(canvas: PlatformCanvas) {
            fillOuterRoundRect(canvas, r.card, c.surface)
            strokeOuterRoundRect(canvas, r.card, c.border, 1f)
            super.draw(canvas)
        }

        override fun onPointerDown(e: PointerEvent) {
            super.onPointerDown(e)
            currentDismiss?.invoke()
            currentDismiss = null
        }

        override fun onPointerUp(e: PointerEvent) {
            super.onPointerUp(e)
            if (textNode.hasSelection) {
                val selectedText = textNode.selectedText
                if (selectedText.isNotEmpty()) {
                    val anchorRect = textNode.selectionRect ?: return
                    currentDismiss?.invoke()
                    currentDismiss = popoverManager.show(
                        content = { buildPopoverContent(selectedText) },
                        anchorRect = anchorRect,
                        position = PopoverManager.defaultPosition(),
                        style = PopoverStyle(
                            backgroundColor = c.surface,
                            borderColor = c.border,
                            borderWidth = 1f,
                            cornerRadius = r.card,
                            shadowColor = rgba(0, 0, 0, 25),
                            shadowOffsetY = 6f,
                            shadowBlur = 16f,
                            padding = 18f,
                            defaultWidth = 280f,
                            defaultHeight = 200f
                        )
                    )
                }
            } else {
                currentDismiss?.invoke()
                currentDismiss = null
            }
        }

        lateinit var textNode: EditableTextNode
        lateinit var popoverManager: PopoverManager

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            textNode = object : EditableTextNode(this) {
                override var text by createSignal(
                    "The quick brown fox jumps over the lazy dog. " +
                    "Select any word to see its definition in a popover popup, " +
                    "just like macOS Dictionary app."
                )
                override val fontSize: Float get() = 15f
                override val singleLine: Boolean get() = false
            }

            popoverManager = consume(popoverManagerContext)!!
        }
    }
}

/** 词典浮层内容：单词 + 音标 + 分隔线 + 释义 + 例句。 */
private fun StateHolder<Node, List<Node>>.buildPopoverContent(word: String) {
    val c = Theme.current.colors

    // 单词
    object : WrappedTextNode(this) {
        override val autoWidth: Boolean get() = true
        override val text: String get() = word
        override val fontSize: Float get() = 18f
        override val fontWeight: Int get() = 700
        override val color: ColorInt get() = c.text
    }

    // 词性 + 音标
    object : WrappedTextNode(this) {
        override val autoWidth: Boolean get() = true
        override val text: String get() = "noun  /ˈdɪkʃəneri/"
        override val fontSize: Float get() = 12f
        override val color: ColorInt get() = rgba(180, 130, 60)
    }

    // 分隔线
    object : RectNode(this) {
        override val argWidth: LayoutSize get() = LayoutSize(244f, false)
        override val argHeight: LayoutSize get() = LayoutSize(1f, false)
        override fun draw(canvas: PlatformCanvas) {
            fillOuterRect(canvas, c.border)
            super.draw(canvas)
        }
    }

    // 释义
    object : WrappedTextNode(this) {
        override val autoWidth: Boolean get() = true
        override val text: String get() = "1. A book or electronic resource that lists words and gives their meanings."
        override val fontSize: Float get() = 13f
        override val color: ColorInt get() = c.text
    }

    // 例句
    object : WrappedTextNode(this) {
        override val autoWidth: Boolean get() = true
        override val text: String get() = "\"She looked up the word in the dictionary.\""
        override val fontSize: Float get() = 12f
        override val color: ColorInt get() = c.textSecondary
        override val italic: Boolean get() = true
    }
}
