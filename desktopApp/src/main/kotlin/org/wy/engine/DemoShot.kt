package org.wy.engine

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolderWithNode
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.wy.engine.dashboard.MarketData
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import java.io.File

/**
 * 离屏截图入口：把 DemoGrand 的界面渲染成 PNG，用于无人工介入地核对视觉细节。
 *
 * 用法：./gradlew :desktopApp:shot
 * 产物：desktopApp/build/shots/ 下的 PNG
 */

private class ShotRoot(
    private val w: Float,
    private val h: Float,
    private val search: String,
    private val tab: DemoTab = DemoTab.OVERVIEW
) : Renderer(null), FlexParam {
    override val layout: LayoutDirection = FlexObject(this)
    override val direction: Direction get() = Direction.y
    override val directionJustify: DirectionJustify get() = DirectionJustify.start
    override val alignItem: AlignItem get() = AlignItem.stretch
    override val alignFix: Boolean get() = true
    override val argWidth: LayoutSize get() = LayoutSize(w, false)
    override val argHeight: LayoutSize get() = LayoutSize(h, false)

    override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
        buildGrandDemo(search, tab, { w }, { h })
    }
}

private fun renderApp(
    search: String,
    w: Int = 1120,
    h: Int = 740,
    /** 浮层滚动条平时透明，截图时直接置为可见态（避免依赖动画帧循环）。 */
    revealScrollBar: Boolean = false,
    tab: DemoTab = DemoTab.OVERVIEW
): Image {
    val surface = Surface.makeRasterN32Premul(w, h)
    val root = ShotRoot(w.toFloat(), h.toFloat(), search, tab)
    if (revealScrollBar) {
        root.walk { if (it is org.wy.engine.helper.OverlayScrollBar) it.revealNow() }
    }
    root.render(PlatformCanvas(surface.canvas))
    return surface.makeImageSnapshot()
}

private fun Node.walk(f: (Node) -> Unit) {
    f(this)
    children.forEach { it.walk(f) }
}

private fun zoom(src: Image, sx: Float, sy: Float, sw: Float, sh: Float, scale: Float): Image {
    val s = Surface.makeRasterN32Premul((sw * scale).toInt(), (sh * scale).toInt())
    s.canvas.clear(rgba(255, 255, 255))
    s.canvas.drawImageRect(
        src,
        Rect(sx, sy, sx + sw, sy + sh),
        Rect(0f, 0f, sw * scale, sh * scale)
    )
    return s.makeImageSnapshot()
}

/**
 * 在 [x0,x1) × [y0,y1) 内找出与底色 [bg] 明显不同的像素包围盒，
 * 用来把"文字到底画在哪一行"变成可比较的数字，而不是靠肉眼。
 */
private fun inkBox(img: Image, x0: Int, y0: Int, x1: Int, y1: Int, bg: Int): String {
    val pix = img.peekPixels() ?: return "no-pixels"
    var minY = Int.MAX_VALUE
    var maxY = -1
    var minX = Int.MAX_VALUE
    var maxX = -1
    for (y in y0 until y1) {
        for (x in x0 until x1) {
            val c = pix.getColor(x, y)
            val d = maxOf(
                kotlin.math.abs((c ushr 16 and 0xFF) - (bg ushr 16 and 0xFF)),
                kotlin.math.abs((c ushr 8 and 0xFF) - (bg ushr 8 and 0xFF)),
                kotlin.math.abs((c and 0xFF) - (bg and 0xFF))
            )
            if (d > 20) {
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                if (x < minX) minX = x
                if (x > maxX) maxX = x
            }
        }
    }
    if (maxY < 0) return "empty"
    return "x=[$minX..$maxX] y=[$minY..$maxY] h=${maxY - minY + 1}"
}

/**
 * 统计区域内与目标色 [color] 足够接近的像素数与包围盒（按通道最大差 ≤ [tol] 判定）。
 * 金融看板用：红/绿蜡烛、盘口深度条、深度面积填充有没有真的画出来。
 */
private fun colorBox(
    img: Image, x0: Int, y0: Int, x1: Int, y1: Int, color: ColorInt, tol: Int = 26
): String {
    val pix = img.peekPixels() ?: return "no-pixels"
    val tr = color ushr 16 and 0xFF
    val tg = color ushr 8 and 0xFF
    val tb = color and 0xFF
    var n = 0
    var minX = Int.MAX_VALUE; var maxX = -1
    var minY = Int.MAX_VALUE; var maxY = -1
    for (y in y0 until y1) {
        for (x in x0 until x1) {
            val c = pix.getColor(x, y)
            val d = maxOf(
                kotlin.math.abs((c ushr 16 and 0xFF) - tr),
                kotlin.math.abs((c ushr 8 and 0xFF) - tg),
                kotlin.math.abs((c and 0xFF) - tb)
            )
            if (d <= tol) {
                n++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }
    return if (n == 0) "none" else "$n px, bbox x=[$minX..$maxX] y=[$minY..$maxY]"
}

/**
 * 统计区域内"偏红 / 偏绿"的像素数与包围盒。盘口深度条与深度面积是半透明
 * 叠色（alpha 30 左右），精确比色受混合比影响，改为按通道偏向判定：
 * 红条 R 通道显著高于 G/B，绿条反之。
 */
private fun tintBox(
    img: Image, x0: Int, y0: Int, x1: Int, y1: Int, up: Boolean, gap: Int = 12
): String {
    val pix = img.peekPixels() ?: return "no-pixels"
    var n = 0
    var minX = Int.MAX_VALUE; var maxX = -1
    var minY = Int.MAX_VALUE; var maxY = -1
    for (y in y0 until y1) {
        for (x in x0 until x1) {
            val c = pix.getColor(x, y)
            val r = c ushr 16 and 0xFF
            val g = c ushr 8 and 0xFF
            val b = c and 0xFF
            val hit = if (up) {
                r - g >= gap && r - b >= gap && r >= 30
            } else {
                // D_DOWN 绿(14,203,129)蓝分量高，低 alpha 叠底后 g≈b（如 17,43,42），
                // 不能再比 g-b，只判 g 对 r 的偏向。
                g - r >= gap && g >= 40
            }
            if (hit) {
                n++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }
    return if (n == 0) "none" else "$n px, bbox x=[$minX..$maxX] y=[$minY..$maxY]"
}

/** 在 [y] 这一行上扫描与底色 [bg] 不同的连续像素段，用于量出滑块 / 边框的横向位置。 */
private fun scanRow(img: Image, y: Int, x0: Int, x1: Int, bg: Int): String {
    val pix = img.peekPixels() ?: return "no-pixels"
    val runs = mutableListOf<String>()
    var start = -1
    for (x in x0 until x1) {
        val c = pix.getColor(x, y)
        val d = maxOf(
            kotlin.math.abs((c ushr 16 and 0xFF) - (bg ushr 16 and 0xFF)),
            kotlin.math.abs((c ushr 8 and 0xFF) - (bg ushr 8 and 0xFF)),
            kotlin.math.abs((c and 0xFF) - (bg and 0xFF))
        )
        if (d > 12) {
            if (start < 0) start = x
        } else if (start >= 0) {
            if (x - start > 1) runs += "$start..${x - 1}"
            start = -1
        }
    }
    if (start >= 0) runs += "$start..${x1 - 1}"
    return if (runs.isEmpty()) "none" else runs.joinToString("  ")
}

/** 在 [x] 这一列上扫描纵向像素段，用于量出滑块的长度与上下留白。 */
private fun scanCol(img: Image, x: Int, y0: Int, y1: Int, bg: Int): String {
    val pix = img.peekPixels() ?: return "no-pixels"
    val runs = mutableListOf<String>()
    var start = -1
    for (y in y0 until y1) {
        val c = pix.getColor(x, y)
        val d = maxOf(
            kotlin.math.abs((c ushr 16 and 0xFF) - (bg ushr 16 and 0xFF)),
            kotlin.math.abs((c ushr 8 and 0xFF) - (bg ushr 8 and 0xFF)),
            kotlin.math.abs((c and 0xFF) - (bg and 0xFF))
        )
        if (d > 12) {
            if (start < 0) start = y
        } else if (start >= 0) {
            if (y - start > 1) runs += "$start..${y - 1}"
            start = -1
        }
    }
    if (start >= 0) runs += "$start..${y1 - 1}"
    return if (runs.isEmpty()) "none" else runs.joinToString("  ")
}

/** 两张图在指定区域内的差异像素数：用来判断"文字有没有溢出到框外"/"有没有发生滚动"。 */
private fun diffCount(a: Image, b: Image, x0: Int, y0: Int, x1: Int, y1: Int): Int {
    val pa = a.peekPixels()!!
    val pb = b.peekPixels()!!
    var n = 0
    for (y in y0 until y1) {
        for (x in x0 until x1) {
            val ca = pa.getColor(x, y)
            val cb = pb.getColor(x, y)
            if (ca != cb) n++
        }
    }
    return n
}

/**
 * 卡片溢出扫描：页面底色上的墨迹即"逃出卡片的组件"。
 *
 * 两阶段判定，避免把卡片内部的浅色填充 / 抗锯齿误报成底色：
 * 1. **泛洪求真正的页面底色**：从扫描窗四边种子出发，只允许"与底色
 *    (244,246,251) 差 ≤4"的像素扩散。卡片内的浅色填充（G_SUBTLE 241,245,249、
 *    G_MUTED 248,250,252）虽然是孤岛，但与页面底色不连通，不会被纳入；
 * 2. **墨迹贴底即溢出**：既不像底色也不像白色（dBg>40 且 dWhite>40）的像素，
 *    四邻域落在泛洪域里——卡片内部的控件四邻都是卡片白底，只有溢出的控件
 *    才会贴到页面底色上。窗口越窄，写死宽度的子件越容易现形。
 *
 * 扫描窗 [x0..) × [y0..)：跳过顶栏 / 侧栏 / 分段标签 / 页头标题这些
 * 合法的"底色上的深色元素"（布局常量：侧栏 208 + 内容padding 16 → x0=224；
 * 页头在滚动区之上、位置固定 → y0=190）。底部再排除视口裁剪带
 * （状态栏 30 + 内容下 padding 16 → 视口底 = h-46，多退 2px 容差）：
 * 被滚动视口裁在折叠线的卡片，其卡内文本会挨着裁剪线下的底色，属合法裁剪。
 */
private fun cardSpill(img: Image, x0: Int = 224, y0: Int = 190): String {
    val w = img.width
    val h = img.height
    val pix = img.peekPixels() ?: return "no-pixels"
    val x1 = w - 2
    val y1 = h - 48

    fun ch(c: Int, shift: Int) = c ushr shift and 0xFF
    fun isBg(c: Int): Boolean {
        return Math.abs(ch(c, 16) - 244) <= 4 && Math.abs(ch(c, 8) - 246) <= 4 &&
            Math.abs(ch(c, 0) - 251) <= 4
    }
    fun isInk(c: Int): Boolean {
        val dBg = maxOf(Math.abs(ch(c, 16) - 244), Math.abs(ch(c, 8) - 246), Math.abs(ch(c, 0) - 251))
        val dWhite = maxOf(Math.abs(ch(c, 16) - 255), Math.abs(ch(c, 8) - 255), Math.abs(ch(c, 0) - 255))
        return dBg > 40 && dWhite > 40
    }

    // 1. 从扫描窗四边种子泛洪：得到与页面底色连通的区域
    val filled = BooleanArray(w * h)
    val stack = ArrayDeque<Int>()
    fun seed(x: Int, y: Int) {
        if (x in x0..x1 && y in y0..y1 && !filled[y * w + x] && isBg(pix.getColor(x, y))) {
            filled[y * w + x] = true
            stack.addLast(y * w + x)
        }
    }
    for (x in x0..x1) { seed(x, y0); seed(x, y1) }
    for (y in y0..y1) { seed(x0, y); seed(x1, y) }
    while (stack.isNotEmpty()) {
        val i = stack.removeLast()
        val x = i % w
        val y = i / w
        seed(x - 1, y); seed(x + 1, y); seed(x, y - 1); seed(x, y + 1)
    }

    // 2. 墨汁像素四邻域命中泛洪域 → 溢出到卡片外
    var count = 0
    var minX = Int.MAX_VALUE
    var maxX = -1
    var minY = Int.MAX_VALUE
    var maxY = -1
    for (y in y0..y1) {
        for (x in x0..x1) {
            if (!isInk(pix.getColor(x, y))) continue
            if (filled[y * w + x - 1] || filled[y * w + x + 1] ||
                filled[(y - 1) * w + x] || filled[(y + 1) * w + x]
            ) {
                count++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }
    return if (count == 0) "clean" else "$count px, bbox x=[$minX..$maxX] y=[$minY..$maxY]"
}

/**
 * 调试用：把指定页整棵树的几何打印出来（节点类 / 相对位置 / 外尺寸），
 * 用于定位"卡片高度与内容不一致"这类布局问题。SHOT_DEBUG_TREE=1 时启用。
 */
private fun debugTree(w: Int, tab: DemoTab, h: Int = 1560) {
    val root = ShotRoot(w.toFloat(), h.toFloat(), "", tab)
    // 先触发一次完整布局（render 会走完测量）
    val surface = Surface.makeRasterN32Premul(w, h)
    root.render(PlatformCanvas(surface.canvas))
    fun dump(n: Node, depth: Int, ox: Float, oy: Float) {
        if (n is LayoutNode) {
            // 匿名对象显示类名恒为 `?`，优先用 debugName 才能在树里认出业务节点
            val name = n.debugName ?: n::class.simpleName ?: "?"
            val ax = ox + n.x
            val ay = oy + n.y
            if (depth <= 12) {
                println(
                    "%s%s @ (%.0f,%.0f) %.0fx%.0f".format(
                        "  ".repeat(depth), name, ax, ay, n.outerWidth, n.outerHeight
                    )
                )
            }
            n.children.forEach { dump(it, depth + 1, ax, ay) }
        } else {
            n.children.forEach { dump(it, depth + 1, ox, oy) }
        }
    }
    println("── debug tree w=$w h=$h tab=$tab ──")
    dump(root, 0, 0f, 0f)
}

private fun label(canvas: PlatformCanvas, text: String, x: Float, y: Float, color: ColorInt, size: Float = 13f) {    canvas.drawParagraph(
        buildParagraph(listOf(RichTextSpan(text, RichTextStyle(fontSize = size, color = color, fontWeight = 700))), 600f),
        x, y
    )
}

/** 把修复前 / 修复后的同一区域上下拼接，便于一眼看出差异。 */
private fun composeCompare(beforePath: String, afterPath: String, out: String, caption: String) {
    val beforeFile = File(beforePath)
    val before: Image? = if (beforeFile.exists()) Image.makeFromEncoded(beforeFile.readBytes()) else null
    val after: Image = Image.makeFromEncoded(File(afterPath).readBytes())
    val pad = 16f
    val labelH = 26f
    val w = after.width.toFloat() + pad * 2
    val panelH = after.height.toFloat()
    val bh = before?.height?.toFloat() ?: 0f
    val h = pad + (if (before != null) labelH + bh + pad else 0f) + labelH + panelH + pad
    val surface = Surface.makeRasterN32Premul(w.toInt(), h.toInt())
    val c = PlatformCanvas(surface.canvas)
    c.clear(rgba(255, 255, 255))
    label(c, caption, pad, pad, TEXT, 14f)
    var y = pad + labelH
    if (before != null) {
        label(c, "BEFORE  (修复前)", pad, y, RED, 12f)
        y += labelH
        surface.canvas.drawImage(before, pad, y)
        y += bh + pad
    }
    label(c, "AFTER  (修复后)", pad, y, GREEN, 12f)
    y += labelH
    surface.canvas.drawImage(after, pad, y)
    surface.makeImageSnapshot().save(out)
}

private fun Image.save(path: String) {
    val file = File(path)
    file.parentFile.mkdirs()
    val data = encodeToData(EncodedImageFormat.PNG)!!
    file.writeBytes(data.bytes)
    println("wrote ${file.absolutePath}")
}

/** 模拟真实交互：点击搜索框 → 逐字键入，用于观察光标与溢出滚动。 */
private fun renderTyped(vararg inputs: String): Image {
    val w = 1120
    val h = 740
    val surface = Surface.makeRasterN32Premul(w, h)
    val root = ShotRoot(w.toFloat(), h.toFloat(), "")
    root.mouseDown(944f, 28f)
    root.mouseUp(944f, 28f)
    inputs.flatMap { it.asIterable() }.forEach {
        root.keyPress(it, KeyCode.Unknown, false, false, false)
    }
    root.render(PlatformCanvas(surface.canvas))
    return surface.makeImageSnapshot()
}

/**
 * 离屏截图入口。
 *
 * 线程约束：引擎的响应式全局状态（memo 栈 / TrackSignal.currentFun / batch）约定
 * 单 UI 线程访问，而 batch 协程跑在 `Dispatchers.Main`（Swing EDT）上。
 * 若在主线程直接渲染，两条线程会同时操作 memo 栈，表现为
 * 「出入并不匹配 / 重复进入 / 禁止在非观察处读取」等连环怪错。
 * 因此整个流程搬到 EDT 上执行，与 batch 串行。
 */
fun main() {
    runBlocking {
        withContext(Dispatchers.Main) {
            shotMain()
        }
    }
}

private fun shotMain() {
    val out = "build/shots"
    // 首访免责声明弹窗会盖住看板截图：截图期间置为已读，结束后恢复用户偏好
    val noticePrefWas = prefs.get("dash.noticeSeen", "")
    prefs.put("dash.noticeSeen", "1")
    val holder = "搜索 · Ctrl+K"
    val long = "搜索一个比较长的关键词看看会不会换行"
    val empty = renderApp("")
    val same = renderApp(holder)
    val typed = renderApp(long)
    empty.save("$out/full-empty.png")
    same.save("$out/full-same.png")
    typed.save("$out/full-typed.png")
    renderTyped(long).save("$out/full-caret.png")

    // 顶栏搜索框区域放大 4 倍，便于肉眼比对 placeholder / 正文的基线
    val bar = Rect(780f, 0f, 1120f, 56f)
    zoom(empty, bar.left, bar.top, bar.width, bar.height, 4f).save("$out/zoom-empty.png")
    zoom(same, bar.left, bar.top, bar.width, bar.height, 4f).save("$out/zoom-same.png")
    zoom(typed, bar.left, bar.top, bar.width, bar.height, 4f).save("$out/zoom-typed.png")
    zoom(renderTyped(long), bar.left, bar.top, bar.width, bar.height, 4f)
        .save("$out/zoom-caret.png")

    // 浮层滚动条：把"最近动态"列表右缘放大 4 倍，看滑块粗细 / 贴边留白 / 是否压到内容
    val withBar = renderApp("", revealScrollBar = true)
    withBar.save("$out/full-scrollbar.png")
    zoom(withBar, 1040f, 240f, 72f, 320f, 4f).save("$out/zoom-scrollbar.png")
    println("[scrollbar] row@320  = " + scanRow(withBar, 320, 1050, 1112, CARD))
    println("[scrollbar] row@420  = " + scanRow(withBar, 420, 1050, 1112, CARD))
    println("[scrollbar] col@1081 = " + scanCol(withBar, 1081, 240, 560, CARD))
    // 静止（未 reveal）时同一区域应完全没有滑块
    println("[scrollbar] hidden   = " + scanRow(empty, 320, 1050, 1112, CARD))

    // ── 组件库页 ──
    // 加高画布：整页（含第 4 行弹层/标签页卡片）一次拍全，避免被滚动区裁掉
    val gallery = renderApp("", w = 1120, h = 1830, tab = DemoTab.COMPONENTS)
    gallery.save("$out/gallery.png")
    if (System.getenv("SHOT_DEBUG_TREE") != null) {
        debugTree(720, DemoTab.COMPONENTS)
        debugTree(1120, DemoTab.DASHBOARD, 800)
        debugTree(1120, DemoTab.OVERVIEW, 740)
    }
    val galleryBar = renderApp("", revealScrollBar = true, tab = DemoTab.COMPONENTS)
    galleryBar.save("$out/gallery-scrollbar.png")
    // 页头徽标区放大 3 倍：核对徽标文字有没有被截断
    zoom(gallery, 290f, 96f, 420f, 46f, 3f).save("$out/zoom-gallery-header.png")
    // 卡片行结构（包裹高度后行位随内容变化，探针随布局实测更新）：
    // 四行卡片顶别在 y=185/395/625/815，行间底色带在 y=385/615/805——
    // 底色带必须纯色（none），卡片行探针给出两张卡片的横向跨度
    println("[gallery] row1 spans @200  = " + scanRow(gallery, 200, 210, 1118, BG))
    println("[gallery] row2 spans @410  = " + scanRow(gallery, 410, 210, 1118, BG))
    println("[gallery] row3 spans @640  = " + scanRow(gallery, 640, 210, 1118, BG))
    println("[gallery] row4 spans @830  = " + scanRow(gallery, 830, 210, 1118, BG))
    println("[gallery] gap  @385/615/805 = " +
        scanRow(gallery, 385, 210, 1118, BG) + " | " +
        scanRow(gallery, 615, 210, 1118, BG) + " | " +
        scanRow(gallery, 805, 210, 1118, BG))
    println("[gallery] bar col@1098   = " + scanCol(galleryBar, 1098, 110, 660, BG))

    // ── 窄窗口回归：卡片内的固定宽子件不得溢出到卡片外 ──
    // 卡片间隙与页面右缘在任何窗口宽度下都只能是底色；
    // 一旦出现墨迹，说明有子件写了死宽 / 死了高，不随容器收缩。
    // 1120 为默认窗口基准；880 为卡片行拆分断点（<880 单列、>=880 双列），
    // 900/1000 覆盖双列窄化，800/720 覆盖单列。
    // 单列模式内容总高 ~1580，画布加到 1800 让整页放得下——否则最后一张卡片
    // 会被滚动视口裁在折叠线处，卡片内文本挨着裁剪线下的底色造成误报。
    for (nw in intArrayOf(1120, 1000, 900, 800, 720)) {
        val narrow = renderApp("", w = nw, h = 2300, tab = DemoTab.COMPONENTS)
        narrow.save("$out/gallery-w$nw.png")
        val spill = cardSpill(narrow)
        println("[gallery w=$nw] outside-card ink = $spill")
    }

    // ── 金融看板页（深色行情终端）──
    // 多宽度截图：1120 默认 / 1280 宽 / 900 与 720 触发窄窗口断点
    // （<1000 隐藏周期 tabs 与 MOCK 文案；<880 只留 2 个交易对 tabs）
    val dashSizes = linkedMapOf(1120 to 800, 1280 to 860, 900 to 740, 720 to 700)
    val dashShots = LinkedHashMap<Int, Image>()
    for ((dw, dh) in dashSizes) {
        val dash = renderApp("", w = dw, h = dh, tab = DemoTab.DASHBOARD)
        dashShots[dw] = dash
        dash.save("$out/dashboard-w$dw.png")
    }
    // 探针区随布局实测更新（先跑 SHOT_DEBUG_TREE=1 量几何，再回填坐标）：
    // 1120 窗口下中列绘图区约 x∈[340,820]、盘口约 x∈[860,1090]、
    // 底部 Depth 面积图约 x∈[340,820] y∈[560,745]。
    // 蜡烛为实色（D_UP 240,76,92 / D_DOWN 14,203,129），直接比色；
    // 深度条 / 面积是半透明叠色，走通道偏向判定。
    val dashUp = rgba(240, 76, 92)
    val dashDown = rgba(14, 203, 129)
    val d = dashShots[1120]!!
    println("[dash] candles red   = " + colorBox(d, 340, 180, 820, 520, dashUp))
    println("[dash] candles green = " + colorBox(d, 340, 180, 820, 520, dashDown))
    println("[dash] book bid bar  = " + tintBox(d, 860, 220, 1090, 700, up = true))
    println("[dash] book ask bar  = " + tintBox(d, 860, 220, 1090, 700, up = false))
    println("[dash] depth area R  = " + tintBox(d, 340, 560, 820, 745, up = true))
    println("[dash] depth area G  = " + tintBox(d, 340, 560, 820, 745, up = false))
    // 窄窗口：蜡烛仍然要画（K 线绘图区随列宽收窄但不断）
    for ((dw, dh) in dashSizes) {
        if (dw == 1120) continue
        val n = dashShots[dw]!!
        println("[dash w=$dw] candles red = " + colorBox(n, 200, 180, dw - 220, dh - 180, dashUp))
    }
    // 实时 K 线探针：rev=0 应为静态快照；rev 前进时末根在走、每 10 个心跳滚出新根
    for (r in listOf(0, 3, 10, 37)) {
        val ks = MarketData.liveKlines("BTCUSDT", "15m", r)
        val last = ks.last()
        println(
            "[dash] klines rev=$r size=${ks.size} last=" + last.time +
                " o=" + last.open + " c=" + last.close + " closed=" + last.closed
        )
    }

    // ── 深色模式验证 ──
    // 打开全局主题信号后重新渲染概览 / 组件库 / 设置 / 笔记 / 看板五页；
    // 结束后恢复运行前的偏好值，避免截图任务污染用户的主题设置。
    val darkPrefWas = prefs.get("dark", "0")
    setDarkTheme(true)
    val darkShot = renderApp("", w = 1120, h = 740)
    darkShot.save("$out/dark-overview.png")
    renderApp("", w = 1120, h = 1830, tab = DemoTab.COMPONENTS).save("$out/dark-gallery.png")
    renderApp("", w = 1120, h = 740, tab = DemoTab.SETTINGS).save("$out/dark-settings.png")
    renderApp("", w = 1120, h = 740, tab = DemoTab.NOTES).save("$out/dark-notes.png")
    // 看板昼夜双主题：夜间深色终端（与白天探针同法比蜡烛/深度色）
    renderApp("", w = 1120, h = 800, tab = DemoTab.DASHBOARD).save("$out/dark-dashboard.png")
    // 探针：页面底色应为深色 BG（15,18,26）
    println("[dark] bg pixel = " + Integer.toHexString(darkShot.peekPixels()!!.getColor(900, 40)))
    // 探针：概览图表柱（深色 BAR 96,106,152）是否真的画出来了
    println("[dark] chart bar = " + colorBox(darkShot, 240, 300, 700, 690, rgba(96, 106, 152)))
    prefs.put("dark", darkPrefWas)
    if (darkPrefWas != "1") demoDark.value = false

    // ── 概览页图表是否真的有柱子 ──
    println("[chart] col@x=300        = " + scanCol(empty, 300, 300, 690, CARD))

    val bg = empty.peekPixels()!!.getColor(900, 40)
    println("[ink] placeholder      : " + inkBox(empty, 845, 8, 1030, 48, bg))
    println("[ink] same-as-real-text: " + inkBox(same, 845, 8, 1030, 48, bg))
    println("[ink] typed-long-text  : " + inkBox(typed, 845, 8, 1030, 60, bg))
    // 溢出检测：长文本态与空态在 pill 右侧外部（x 1046..1120）应完全一致
    println("[spill] px-diff outside-pill = ${diffCount(typed, empty, 1046, 4, 1120, 52)}")
    // 光标跟随：聚焦长文本后可见字形整体左移（非 0 表示发生了水平滚动）
    val caret = renderTyped(long)
    println("[caret] px-diff vs unfocused  = ${diffCount(caret, typed, 845, 16, 1031, 40)}")
    println("[caret] ink box               = " + inkBox(caret, 845, 16, 1031, 40, bg))

    // 与 build/shots/before 下的基线快照合成对比图（没有基线时自动跳过）
    composeCompare("$out/before/zoom-empty.png", "$out/zoom-empty.png",
        "$out/compare-placeholder.png", "搜索框占位文本对齐：placeholder 与正文同段落、同 padding")
    composeCompare("$out/before/zoom-typed.png", "$out/zoom-typed.png",
        "$out/compare-nowrap.png", "长文本输入：单行不折行，溢出裁剪在内容盒内")
    composeCompare("$out/before/zoom-caret.png", "$out/zoom-caret.png",
        "$out/compare-caret.png", "光标跟随：单行溢出时水平滚动，光标始终可见")

    prefs.put("dash.noticeSeen", noticePrefWas)
}
