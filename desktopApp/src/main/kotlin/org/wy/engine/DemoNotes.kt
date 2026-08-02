package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.GrowChild
import org.wy.engine.layout.LayoutDirection
import org.wy.lib.StoreRef
import org.wy.signal.getValue
import org.wy.signal.setValue

// ════════════════════════════════════════════════════
// 笔记页
// ════════════════════════════════════════════════════
internal fun StateHolder<Node,List<Node>>.dNotesPage(active: StoreRef<DemoTab>, notes: StoreRef<String>): RectNode =
    object : RectNode(this), FlexParam, GrowChild {
        override val hide: Boolean get() = active.value != DemoTab.NOTES

        override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f

        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.y
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val alignFix: Boolean get() = true
        override val gap: Float get() = 12f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            object : RectNode(this), FlexParam {
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.x
                override val directionJustify: DirectionJustify get() = DirectionJustify.between
                override val alignItem: AlignItem get() = AlignItem.center
                override val alignFix: Boolean get() = true
                override val argHeight: LayoutSize get() = LayoutSize(30f, false)

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    dLabel({ "笔记编辑器" }, 14f, TEXT, 600)
                    dButton("清空", { notes.value = "" }, primary = false, width = 72f, height = 28f)
                }
            }

            object : RectNode(this), FlexParam, GrowChild {
                override fun argGrow(direction: Direction): Float = if (direction == Direction.y) 1f else 0f
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.x
                override val directionJustify: DirectionJustify get() = DirectionJustify.start
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val alignFix: Boolean get() = true
                override val gap: Float get() = 12f

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    object : RectNode(this), FlexParam, GrowChild {
                        override fun argGrow(direction: Direction): Float = if (direction == Direction.x) 1f else 0f
                        override val layout: LayoutDirection = FlexObject(this)
                        override val direction: Direction get() = Direction.y
                        override val directionJustify: DirectionJustify get() = DirectionJustify.start
                        override val alignItem: AlignItem get() = AlignItem.stretch
                        override val alignFix: Boolean get() = true
                        override val gap: Float get() = 8f

                        override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                            12f

                        override fun draw(canvas: PlatformCanvas) {
                            fillOuterRoundRect(canvas, 10f, CARD)
                            strokeOuterRoundRect(canvas, 10f, BORDER, 1f)
                            super.draw(canvas)
                        }

                        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                            dLabel({ "Editor" }, 12f, TEXT2, 600)
                            object : EditableTextNode(this), GrowChild {
                                override fun argGrow(direction: Direction): Float =
                                    if (direction == Direction.y) 1f else 0f

                                override val argHeight: LayoutSize
                                    get() = sizeFromParent(Direction.y)

                                override var text: String
                                    get() = notes.value
                                    set(v) {
                                        notes.value = v
                                    }

                                override val fontSize: Float = 14f

                                override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                                    if (direction == Direction.x) 10f else 8f

                                override fun draw(canvas: PlatformCanvas) {
                                    canvas.save()
                                    canvas.clipRect(0f, 0f, outerWidth, outerHeight)
                                    super.draw(canvas)
                                    canvas.restore()
                                }
                            }
                        }
                    }

                    object : RectNode(this), FlexParam, GrowChild {
                        override fun argGrow(direction: Direction): Float = if (direction == Direction.x) 1f else 0f
                        override val layout: LayoutDirection = FlexObject(this)
                        override val direction: Direction get() = Direction.y
                        override val directionJustify: DirectionJustify get() = DirectionJustify.start
                        override val alignItem: AlignItem get() = AlignItem.stretch
                        override val alignFix: Boolean get() = true
                        override val gap: Float get() = 8f

                        override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                            12f

                        override fun draw(canvas: PlatformCanvas) {
                            fillOuterRoundRect(canvas, 10f, rgba(248, 250, 255))
                            strokeOuterRoundRect(canvas, 10f, BORDER, 1f)
                            super.draw(canvas)
                        }

                        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                            dLabel({ "Preview" }, 12f, TEXT2, 600)
                            object : RichTextNode(this) {
                                override val spans: List<RichTextSpan> get() = previewSpans(notes.value)

                                override fun argPadding(direction: Direction, startEnd: StartEnd): Float =
                                    if (direction == Direction.x) 10f else 8f

                                override fun draw(canvas: PlatformCanvas) {
                                    canvas.save()
                                    canvas.clipRect(0f, 0f, outerWidth, outerHeight)
                                    super.draw(canvas)
                                    canvas.restore()
                                }
                            }
                            dLabel({ "字数 ${notes.value.length}" }, 11f, TEXT2)
                        }
                    }
                }
            }
        }
    }
