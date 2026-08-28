package org.wy.demo.image

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import org.wy.demo.helper.hint
import org.wy.demo.helper.page
import org.wy.demo.helper.row
import org.wy.demo.helper.sectionTitle
import org.wy.engine.*
import org.wy.engine.helper.Button
import org.wy.engine.helper.ButtonVariant
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.lib.StoreRef
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue
import java.awt.EventQueue
import java.util.concurrent.atomic.AtomicInteger

// 演示：ImageLoader 图片异步加载薄层
// 1) 三张卡片分别演示：异步加载 / 并发去重（同 URL 只 fetch 一次）/ 失败重试
// 2) 加载成功后再次点击同 URL → 命中缓存，不重新取字节

private sealed interface CardState {
    data object Loading : CardState
    data object Success : CardState
    data object Failed : CardState
}

private fun main() {
    // 全 demo 共享的 loader：卡片 B 与卡片 C 使用同一 URL 演示去重/缓存
    val loader = ImageLoader()
    val totalFetches = AtomicInteger(0)
    loader.fetcher = { url, onBytes ->
        totalFetches.incrementAndGet()
        val color = when {
            url.contains("red") -> rgba(220, 60, 60)
            url.contains("blue") -> rgba(60, 110, 220)
            else -> rgba(80, 180, 100)
        }
        // 模拟网络：后台线程延迟取字节后，把结果投递回主线程（引擎渲染线程）再回调
        Thread {
            Thread.sleep(400)   // 模拟网络延迟
            val bytes = if (url.contains("bad")) null else renderPng(color)
            EventQueue.invokeLater { onBytes(bytes) }
        }.start()
    }

    object : SkiaApp(860, 640), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val directionJustify: DirectionJustify get() = DirectionJustify.center
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 8f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            page {
                sectionTitle("图片异步加载 ImageLoader")
                hint("点击卡片按钮异步加载图片。B 与 C 共用同一 URL，可观察：并发去重（同 URL 只 fetch 一次）、缓存命中（成功后不再请求）、失败重试")
                asyncCard("A · 异步加载", "img://red/avatar.png", loader, totalFetches)
                asyncCard("B · 并发去重", "img://blue/avatar.png", loader, totalFetches)
                asyncCard("C · 同一 URL(与 B)", "img://blue/avatar.png", loader, totalFetches)
                asyncCard("D · 失败重试", "img://bad/x.png", loader, totalFetches)
            }
        }
    }
}

private fun StateHolder<Node, List<Node>>.asyncCard(
    title: String,
    url: String,
    loader: ImageLoader,
    totalFetches: AtomicInteger,
) {
    val imageStore: StoreRef<PlatformImage?> = createSignal(null)
    val state: StoreRef<CardState> = createSignal<CardState>(CardState.Loading)

    object : RectNode(this), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val direction: Direction get() = Direction.x
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignItem: AlignItem get() = AlignItem.center
        override val alignFix: Boolean get() = true
        override val gap: Float get() = 12f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            // 左侧：图片区域（加载成功前显示占位）
            object : RectNode(this), FlexParam {
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.y
                override val directionJustify: DirectionJustify get() = DirectionJustify.center
                override val alignItem: AlignItem get() = AlignItem.center
                override val alignFix: Boolean get() = true
                override val argWidth: LayoutSize get() = LayoutSize(96f, false)
                override val argHeight: LayoutSize get() = LayoutSize(96f, false)

                override fun draw(canvas: PlatformCanvas) {
                    fillOuterRoundRect(canvas, 12f, rgba(235, 236, 242))
                    super.draw(canvas)
                }

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    val img = imageStore.get()
                    if (img != null) {
                        object : ImageNode(this) {
                            override val image: PlatformImage? get() = img
                            override val size: LayoutSizeDirection get() = LayoutSizeDirection(Direction.x, 96f, true)
                            override val radius: Float get() = 8f
                        }
                    } else {
                        val label = when (state.get()) {
                            CardState.Loading -> "加载中…"
                            CardState.Success -> "已加载"   // 理论上不会显示（有图则走上图分支）
                            CardState.Failed -> "加载失败，可重试"
                        }
                        object : WrappedTextNode(this) {
                            override val autoWidth: Boolean get() = true
                            override val text: String get() = label
                            override val fontSize: Float get() = 12f
                            override val color: ColorInt get() =
                                if (state.get() == CardState.Failed) rgba(200, 70, 70) else rgba(140, 142, 156)
                        }
                    }
                }
            }

            // 右侧：描述 + 操作按钮
            object : RectNode(this), FlexParam {
                override val layout: LayoutDirection = FlexObject(this)
                override val direction: Direction get() = Direction.y
                override val alignFix: Boolean get() = true
                override val alignItem: AlignItem get() = AlignItem.stretch
                override val gap: Float get() = 6f

                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    object : WrappedTextNode(this) {
                        override val autoWidth: Boolean get() = true
                        override val text: String get() = "$title  ($url)"
                        override val fontSize: Float get() = 13f
                        override val fontWeight: Int get() = 600
                        override val color: ColorInt get() = rgba(40, 44, 60)
                    }

                    object : WrappedTextNode(this) {
                        override val autoWidth: Boolean get() = true
                        override val text: String get() =
                            "已请求次数：${totalFetches.get()}  ·  当前状态：${
                                when (state.get()) {
                                    CardState.Loading -> "加载中"
                                    CardState.Success -> "成功"
                                    CardState.Failed -> "失败"
                                }
                            }"
                        override val fontSize: Float get() = 11f
                        override val color: ColorInt get() = rgba(130, 132, 148)
                    }

                    row {
                        object : Button(this@row) {
                            override val label: String get() =
                                when (state.get()) {
                                    CardState.Loading -> "加载中…"
                                    CardState.Success -> "重新加载（命中缓存）"
                                    CardState.Failed -> "重试"
                                }
                            override val variant: ButtonVariant get() = ButtonVariant.Secondary
                            override fun onClick() {
                                if (state.get() == CardState.Loading) return  // 进行中忽略重复点击
                                state.set(CardState.Loading)
                                imageStore.set(null)  // 清掉旧图回占位
                                loader.load(url) { img ->
                                    imageStore.set(img)
                                    state.set(if (img != null) CardState.Success else CardState.Failed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 运行时用 Skia 生成一张纯色测试位图并返回 PNG 字节，模拟网络图片内容。 */
private fun renderPng(color: Int): ByteArray {
    val surface = Surface.makeRasterN32Premul(96, 96)
    val c = surface.canvas
    c.drawRect(0f, 0f, 96f, 96f, org.jetbrains.skia.Paint().apply { this.color = color })
    c.drawCircle(48f, 48f, 30f, org.jetbrains.skia.Paint().apply { this.color = 0xFFFFFFFF.toInt() })
    return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
}