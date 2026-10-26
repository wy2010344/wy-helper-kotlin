package org.wy.engine.helper

/**
 * 浮层相对锚点的放置方向（8 方向）。
 *
 * 命名 = 主轴侧 + 对齐端：[BottomStart] 表示"锚点下方、起始边对齐"，
 * [RightEnd] 表示"锚点右侧、结束边对齐"。默认 [BottomStart] 即 Dropdown/Popover 的传统行为。
 */
enum class Placement {
    BottomStart, BottomEnd,
    TopStart, TopEnd,
    LeftStart, LeftEnd,
    RightStart, RightEnd,
}

/**
 * 锚矩形 → 面板坐标（均为浮层根节点内相对坐标），带空间不足自动回退。
 *
 * @param p 放置方向
 * @param ax/ay/aw/ah 锚矩形（左上角 + 宽高，浮层根内相对坐标）
 * @param pw/ph 面板尺寸
 * @param vw/vh 视口尺寸（浮层根节点 outer 尺寸）；**<= 0 视为未测量**
 *              （测试 / 离屏环境），此时只出首选位：不翻转、不 clamp，
 *              保持旧实现的静态定位行为
 * @param gap 面板与锚点在主轴上的间距
 * @return 面板 (x, y)
 *
 * 回退规则（判据只读锚/面板/视口几何，不含 x/y 自身迭代量，两次调用幂等）：
 * 1. 首选侧放不下（含 gap）且对侧放得下 → 翻转到对侧；
 * 2. 双侧都放不下 → 保持首选侧，再贴视口边 clamp；
 * 3. 对齐轴越界（align 端超出视口）→ 同样 clamp 贴边。
 */
fun resolvePlacement(
    p: Placement,
    ax: Float, ay: Float, aw: Float, ah: Float,
    pw: Float, ph: Float,
    vw: Float, vh: Float,
    gap: Float,
): Pair<Float, Float> {
    // 首选位（不依赖视口）
    var x: Float
    var y: Float
    val vertical = when (p) {   // 主轴是否为纵向（上下）
        Placement.BottomStart, Placement.BottomEnd,
        Placement.TopStart, Placement.TopEnd -> true
        else -> false
    }
    val preferredFirst = when (p) { // 首选主轴侧是否为"正方向"（下 / 右）
        Placement.BottomStart, Placement.BottomEnd,
        Placement.RightStart, Placement.RightEnd -> true
        else -> false
    }
    // 对齐端：Start 对齐锚点起始边（左 / 上），End 对齐结束边（右 / 下）
    val alignEnd = when (p) {
        Placement.BottomEnd, Placement.TopEnd,
        Placement.LeftEnd, Placement.RightEnd -> true
        else -> false
    }
    x = if (vertical) (if (alignEnd) ax + aw - pw else ax) else (if (preferredFirst) ax + aw + gap else ax - pw - gap)
    y = if (vertical) (if (preferredFirst) ay + ah + gap else ay - ph - gap) else (if (alignEnd) ay + ah - ph else ay)

    // 视口无效（未测量）：直接返回首选位
    if (vw <= 0f || vh <= 0f) return x to y

    if (vertical) {
        val fitsBelow = ay + ah + gap + ph <= vh
        val fitsAbove = ay - gap - ph >= 0f
        // 首选侧放得下 → 首选；放不下且对侧放得下 → 翻转；双侧不足 → 保持首选（随后 clamp）
        val placeBelow = when {
            preferredFirst && fitsBelow -> true
            preferredFirst && fitsAbove -> false
            !preferredFirst && !fitsAbove && fitsBelow -> true
            else -> preferredFirst
        }
        y = if (placeBelow) ay + ah + gap else ay - ph - gap
    } else {
        val fitsRight = ax + aw + gap + pw <= vw
        val fitsLeft = ax - gap - pw >= 0f
        val placeRight = when {
            preferredFirst && fitsRight -> true
            preferredFirst && fitsLeft -> false
            !preferredFirst && !fitsLeft && fitsRight -> true
            else -> preferredFirst
        }
        x = if (placeRight) ax + aw + gap else ax - pw - gap
    }

    // 贴边 clamp（含对齐轴越界）
    x = x.coerceIn(0f, (vw - pw).coerceAtLeast(0f))
    y = y.coerceIn(0f, (vh - ph).coerceAtLeast(0f))
    return x to y
}
