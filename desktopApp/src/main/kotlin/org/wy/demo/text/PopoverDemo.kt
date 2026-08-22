package org.wy.demo.text

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.helper.Theme
import org.wy.demo.helper.hint
import org.wy.demo.helper.page
import org.wy.engine.helper.popover
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
            page(gap = 16f) {
                sectionTitle("Popover 词典浮层")
                hint("选中下方文本区域中的任意单词，即可弹出词典释义浮层；点击浮层外部或 Esc 可关闭。")

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

        /** 当前打开的 popover 句柄，用于切换选区 / 取消选中时关闭旧浮层。 */
        private var currentPop: Pop? = null

        private fun closeCurrent() {
            currentPop?.let { engineGlobal.removePop(it) }
            currentPop = null
        }

        override fun draw(canvas: PlatformCanvas) {
            fillOuterRoundRect(canvas, r.card, c.surface)
            strokeOuterRoundRect(canvas, r.card, c.border, 1f)
            super.draw(canvas)
        }

        override fun onPointerDown(e: PointerEvent) {
            super.onPointerDown(e)
            closeCurrent()
        }

        override fun onPointerUp(e: PointerEvent) {
            super.onPointerUp(e)
            if (textNode.hasSelection) {
                val selectedText = textNode.selectedText
                if (selectedText.isNotEmpty()) {
                    val anchorRect = textNode.selectionRect ?: return
                    closeCurrent()
                    currentPop = engineGlobal.appendPop { pop ->
                        popover(anchorRect, onClose = {
                            engineGlobal.removePop(pop)
                            currentPop = null
                        }) {
                            buildPopoverContent(selectedText)
                        }
                    }
                }
            } else {
                closeCurrent()
            }
        }

        lateinit var textNode: EditableTextNode

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
