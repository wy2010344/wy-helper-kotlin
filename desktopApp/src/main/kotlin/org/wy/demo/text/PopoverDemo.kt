package org.wy.demo.text

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.helper.PopoverManager
import org.wy.engine.helper.PopoverStyle
import org.wy.engine.helper.popoverManagerContext
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.lib.EmptyFun
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
            provide(popoverManagerContext, PopoverManager())
            popoverDemo()
            popoverOverlay()
        }

        private fun StateHolderWithNode<Node, List<Node>>.popoverOverlay() {
            val pm = consume(popoverManagerContext)!!
            object : Node(this) {
                override fun draw(canvas: PlatformCanvas) {
                    val popovers = pm.popovers
                    popovers.forEach { req ->
                        val pos = pm.getPosition(req.id) ?: return@forEach
                        val node = pm.getNode(req.id, context) ?: return@forEach
                        canvas.save()
                        canvas.translate(pos.x, pos.y)
                        node.draw(canvas)
                        canvas.restore()
                    }
                }
            }
        }
    }
}

fun StateHolder<Node, List<Node>>.popoverDemo() {
    object : WrappedTextNode(this) {
        override val autoWidth: Boolean get() = true
        override val text: String get() = "Popover Demo — Select text to show dictionary popover"
        override val fontSize: Float get() = 18f
        override val fontWeight: Int get() = 700
        override val color: ColorInt get() = rgba(40, 40, 60)
    }

    object : WrappedTextNode(this) {
        override val autoWidth: Boolean get() = true
        override val text: String get() = "Select any word below to see its definition in a popover."
        override val fontSize: Float get() = 12f
        override val color: ColorInt get() = rgba(120, 120, 140)
    }

    dictionaryTextArea()
}

fun StateHolder<Node, List<Node>>.dictionaryTextArea() {
    val g = consume(engineGlobalContext)!!

    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val argWidth: LayoutSize get() = LayoutSize(600f, false)
        override val argHeight: LayoutSize get() = LayoutSize(200f, false)
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch

        private var currentDismiss: EmptyFun? = null

        override fun draw(canvas: PlatformCanvas) {
            fillOuterRoundRect(canvas, 12f, rgba(245, 245, 250))
            super.draw(canvas)
        }

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            val textNode = object : EditableTextNode(this) {
                override var text by createSignal(
                    "The quick brown fox jumps over the lazy dog. " +
                    "Select any word to see its definition in a popover popup, just like macOS Dictionary app."
                )
                override val fontSize: Float get() = 16f
                override val singleLine: Boolean get() = false
            }

            val popoverManager = consume(popoverManagerContext)!!

            g.registerMouseUp {
                if (textNode.hasSelection) {
                    val selectedText = textNode.selectedText
                    if (selectedText.isNotEmpty()) {
                        val anchorRect = textNode.selectionRect ?: return@registerMouseUp
                        currentDismiss?.invoke()
                        currentDismiss = popoverManager.show(
                            content = { holder ->
                                buildDictionaryPopover(holder, selectedText, popoverManager)
                            },
                            anchorRect = anchorRect,
                            position = PopoverManager.defaultPosition(),
                            style = PopoverStyle(
                                backgroundColor = rgba(255, 255, 255),
                                borderColor = rgba(200, 200, 220),
                                cornerRadius = 10f,
                                shadowColor = rgba(0, 0, 0, 30),
                                shadowOffsetY = 4f,
                                shadowBlur = 12f,
                                padding = 16f,
                                defaultWidth = 280f,
                                defaultHeight = 180f
                            )
                        )
                    }
                } else {
                    currentDismiss?.invoke()
                    currentDismiss = null
                }
            }

            g.registerMouseDown {
                if (currentDismiss != null) {
                    currentDismiss?.invoke()
                    currentDismiss = null
                }
            }
        }
    }
}

fun buildDictionaryPopover(
    holder: StateHolderWithNode<Node, List<Node>>,
    word: String,
    popoverManager: PopoverManager
) {
    with(holder) {
        object : WrappedTextNode(holder) {
            override val autoWidth: Boolean get() = true
            override val text: String get() = word
            override val fontSize: Float get() = 20f
            override val fontWeight: Int get() = 700
            override val color: ColorInt get() = rgba(30, 30, 60)
        }

        object : WrappedTextNode(holder) {
            override val autoWidth: Boolean get() = true
            override val text: String get() = "noun  /ˈdɪkʃəneri/"
            override val fontSize: Float get() = 12f
            override val color: ColorInt get() = rgba(150, 100, 50)
        }

        object : RectNode(holder) {
            override val argWidth: LayoutSize get() = LayoutSize(248f, false)
            override val argHeight: LayoutSize get() = LayoutSize(1f, false)
            override fun draw(canvas: PlatformCanvas) {
                fillOuterRect(canvas, rgba(220, 220, 230))
                super.draw(canvas)
            }
        }

        object : WrappedTextNode(holder) {
            override val autoWidth: Boolean get() = true
            override val text: String get() = "1. A book or electronic resource that lists words and gives their meanings."
            override val fontSize: Float get() = 13f
            override val color: ColorInt get() = rgba(60, 60, 80)
        }

        object : WrappedTextNode(holder) {
            override val autoWidth: Boolean get() = true
            override val text: String get() = "\"She looked up the word in the dictionary.\""
            override val fontSize: Float get() = 12f
            override val color: ColorInt get() = rgba(100, 100, 120)
            override val italic: Boolean get() = true
        }

        object : RectNode(holder), FlexParam {
            override val argWidth: LayoutSize get() = LayoutSize(80f, false)
            override val argHeight: LayoutSize get() = LayoutSize(28f, false)
            override val alignFix: Boolean get() = true
            override fun draw(canvas: PlatformCanvas) {
                fillOuterRoundRect(canvas, 6f, rgba(230, 230, 240))
                super.draw(canvas)
            }
            override fun mouseClick(e: MouseEvent) {
                popoverManager.dismissAll()
            }
            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                object : WrappedTextNode(this) {
                    override val autoWidth: Boolean get() = true
                    override val text: String get() = "Close"
                    override val fontSize: Float get() = 13f
                    override val color: ColorInt get() = rgba(60, 60, 80)
                }
            }
        }
    }
}