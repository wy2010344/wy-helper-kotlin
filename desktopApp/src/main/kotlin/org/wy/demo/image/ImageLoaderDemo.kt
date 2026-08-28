package org.wy.demo.image

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.demo.helper.hint
import org.wy.demo.helper.page
import org.wy.demo.helper.row
import org.wy.demo.helper.sectionTitle
import org.wy.engine.*
import org.wy.engine.helper.Button
import org.wy.engine.helper.ButtonVariant
import org.wy.engine.helper.text
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue
import java.awt.EventQueue
import java.net.HttpURLConnection
import java.net.URL

// 演示：ImageLoader 图片异步加载薄层
// 1) 四张卡片分别演示：异步加载 / 并发去重（同 URL 只 fetch 一次）/ 缓存命中 / 失败重试
// 2) B 与 C 共用同一 URL，观察去重与缓存
// 3) fetcher 走真实 web 链接（JVM HttpURLConnection 下载字节），需要能访问 picsum.photos

private sealed interface CardState {
    data object Loading : CardState
    data object Success : CardState
    data object Failed : CardState
}

private fun main() {
    val loader = ImageLoader()
    var totalFetches by createSignal(0)
    loader.fetcher = { url, onBytes ->
        totalFetches += 1   // 请求计数（信号，UI 自动刷新）
        // 后台线程真实网络下载，完成后投递回主线程再回调
        Thread {
            val bytes = try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.instanceFollowRedirects = true
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    conn.inputStream.use { it.readBytes() }
                } else null
            } catch (e: Throwable) {
                null
            }
            EventQueue.invokeLater { onBytes(bytes) }
        }.start()
    }

    // D 卡用无法连接的 URL 演示失败重试
    val badUrl = "http://127.0.0.1:9/nonexistent.png"

    object : SkiaApp(860, 640), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 8f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            page {
                sectionTitle("图片异步加载 ImageLoader")
                hint("点击卡片按钮从 web 异步下载图片。需直连 picsum.photos。B 与 C 共用同一 URL，可观察：并发去重（同 URL 只 fetch 一次）、缓存命中（成功后不再请求）、失败重试")
                asyncCard("A · 异步加载", "https://picsum.photos/seed/wyhelper-red/600/400", loader, { totalFetches })
                asyncCard("B · 并发去重", "https://picsum.photos/seed/wyhelper-blue/600/400", loader, { totalFetches })
                asyncCard("C · 同一 URL(与 B)", "https://picsum.photos/seed/wyhelper-blue/600/400", loader, { totalFetches })
                asyncCard("D · 失败重试", badUrl, loader, { totalFetches })
            }
        }
    }
}

private fun StateHolder<Node, List<Node>>.asyncCard(
    title: String,
    url: String,
    loader: ImageLoader,
    totalFetches: () -> Int,
) {
    val imageStore = createSignal<PlatformImage?>(null)
    val state = createSignal<CardState>(CardState.Loading)

    row {
        // 左侧：96x96 图片区（仿 Button：固定尺寸 + alignFix，内部居中放文本/图片）
        object : RectNode(this@row), FlexParam {
            override val layout: LayoutDirection = FlexObject(this)
            override val direction: Direction get() = Direction.y
            override val directionJustify: DirectionJustify get() = DirectionJustify.center
            override val alignItem: AlignItem get() = AlignItem.center
            override val alignFix: Boolean get() = true
            override val argWidth: LayoutSize get() = LayoutSize(96f, false)
            override val argHeight: LayoutSize get() = LayoutSize(96f, false)
            override val gap: Float get() = 4f

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
                    val label: String = when (state.get()) {
                        CardState.Loading -> "加载中…"
                        CardState.Success -> "已加载"
                        CardState.Failed -> "加载失败\n可重试"
                    }
                    text({ label }, 12f, if (state.get() == CardState.Failed) rgba(200, 70, 70) else rgba(140, 142, 156), 400)
                }
            }
        }

        // 右侧：描述 + 状态 + 操作按钮（直接平铺，避免 flex 嵌套）
        text(
            { "$title\n$url" },
            13f, rgba(40, 44, 60), 600,
        )
        text(
            {
                "当前状态：${when (state.get()) {
                    CardState.Loading -> "加载中"
                    CardState.Success -> "成功"
                    CardState.Failed -> "失败"
                }}  ·  已请求总数：${totalFetches()}"
            },
            11f, rgba(130, 132, 148), 400,
        )
        object : Button(this@row) {
            override val label: String get() =
                when (state.get()) {
                    CardState.Loading -> "加载中…"
                    CardState.Success -> "重新加载（命中缓存）"
                    CardState.Failed -> "重试"
                }
            override val variant: ButtonVariant get() = ButtonVariant.Secondary
            override fun onClick() {
                if (state.get() == CardState.Loading) return
                state.set(CardState.Loading)
                imageStore.set(null)
                loader.load(url) { img ->
                    imageStore.set(img)
                    state.set(if (img != null) CardState.Success else CardState.Failed)
                }
            }
        }
    }
}