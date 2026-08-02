package org.wy.engine

import com.wy.mve.StateHolder
import org.wy.lib.getValue
import org.wy.signal.memo

/**
 * 显示一张位图。图片始终绘制在 **padding 之内的内容区**。
 *
 * 尺寸模型：
 * - 已知原图宽高 [originalWidth] / [originalHeight]（宽高比固定）。
 * - 通过 [size] 只给**一个方向**的尺寸（宽或高），另一个方向按原图比例自动算出。
 *   [size] 的 `LayoutSizeDirection.fromInside` 表示该数值是**内容区**尺寸还是**外包**尺寸：
 *   - `fromInside = true`  → value 是内容区尺寸（图片实际绘制区）
 *   - `fromInside = false` → value 是外包尺寸（value - padding = 内容区尺寸）
 * - [size] 为 null 时内容区 = 原图尺寸。
 * - [image] 为 null 时节点无尺寸、不绘制（可用于延迟解码 / 信号驱动的图片）。
 *
 * 示例：
 * ```kotlin
 * object : ImageNode(this) {
 *     override val image: PlatformImage get() = loadedImage
 *     override val size: LayoutSizeDirection get() = LayoutSizeDirection(Direction.x, 96f, true)
 *     override val radius: Float get() = 12f
 * }
 * // 或给出外包宽 120，四周 padding 12：
 * object : ImageNode(this) {
 *     override val image: PlatformImage get() = loadedImage
 *     override val size: LayoutSizeDirection get() = LayoutSizeDirection(Direction.x, 120f, false)
 *     override fun argPadding(direction: Direction, startEnd: StartEnd) = 12f
 * }
 * ```
 */
open class ImageNode(
    context: StateHolder<Node,List<Node>>
) : RectNode(context) {

    /** 图片（动态属性，可重载，如信号驱动或延迟解码）。null 时节点无尺寸、不绘制 */
    open val image: PlatformImage? = null

    /** 原图宽度，可覆写为其它"固有尺寸" */
    open val originalWidth: Float get() = image?.width?.toFloat() ?: 0f

    /** 原图高度，可覆写为其它"固有尺寸" */
    open val originalHeight: Float get() = image?.height?.toFloat() ?: 0f

    /** 画圆角用的半径，0 表示不裁剪（裁剪区域为内容区） */
    open val radius: Float = 0f

    /** 单个尺寸提示：指定宽或高（[LayoutSizeDirection.direction]），另一方向按原图比例推算；null 表示按原图尺寸 */
    open val size: LayoutSizeDirection? = null

    /** 内容区（图片实际绘制区）尺寸：只给一个方向时，另一个方向按原图比例补齐 */
    protected val contentSize: Pair<Float, Float> by memo {
        val img = image ?: return@memo 0f to 0f
        val padX = padding(Direction.x, StartEnd.start) + padding(Direction.x, StartEnd.end)
        val padY = padding(Direction.y, StartEnd.start) + padding(Direction.y, StartEnd.end)
        when (val s = size) {
            null -> originalWidth to originalHeight
            else -> when (s.direction) {
                Direction.x -> {
                    val w = if (s.fromInside) s.value else (s.value - padX).coerceAtLeast(0f)
                    w to (w * originalHeight / originalWidth.coerceAtLeast(1f))
                }

                Direction.y -> {
                    val h = if (s.fromInside) s.value else (s.value - padY).coerceAtLeast(0f)
                    (h * originalWidth / originalHeight.coerceAtLeast(1f)) to h
                }
            }
        }
    }

    override val argWidth: LayoutSize
        get() = LayoutSize(contentSize.first, true)

    override val argHeight: LayoutSize
        get() = LayoutSize(contentSize.second, true)

    override fun draw(canvas: PlatformCanvas) {
        val img = image ?: return
        val (w, h) = contentSize
        if (w <= 0f || h <= 0f) {
            super.draw(canvas)
            return
        }
        val ox = paddingInlineStart
        val oy = paddingBlockStart
        val r = radius
        if (r > 0f) {
            canvas.save()
            canvas.clipRRect(ox, oy, w, h, r)
            canvas.drawImage(img, ox, oy, w, h)
            canvas.restore()
        } else {
            canvas.drawImage(img, ox, oy, w, h)
        }
        super.draw(canvas)
    }
}
