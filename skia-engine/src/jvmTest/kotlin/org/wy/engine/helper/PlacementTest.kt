package org.wy.engine.helper

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 浮层 8 方向定位纯函数测试：
 * - 充足空间下 8 方向各就各位（默认行为与旧实现一致）；
 * - 首选侧空间不足翻转对侧；
 * - 双侧均不足贴边 clamp；
 * - align 越界 clamp；
 * - 视口无效（测试/未测量环境视口为 0）时只出首选位、不翻转不 clamp；
 * - 同输入重复调用结果一致（幂等，防布局抖动）。
 */
class PlacementTest {

    // ── 基准几何：视口 1000×800，锚点 (400,300) 100×40，面板 200×120，gap 4 ──
    private val vw = 1000f
    private val vh = 800f
    private val ax = 400f
    private val ay = 300f
    private val aw = 100f
    private val ah = 40f
    private val pw = 200f
    private val ph = 120f
    private val gap = 4f

    private fun resolve(p: Placement, ax: Float = this.ax, ay: Float = this.ay) =
        resolvePlacement(p, ax, ay, aw, ah, pw, ph, vw, vh, gap)

    @Test
    fun eightPlacementsInAmpleViewport() {
        assertEquals(ax to (ay + ah + gap), resolve(Placement.BottomStart), "下方起始对齐")
        assertEquals((ax + aw - pw) to (ay + ah + gap), resolve(Placement.BottomEnd), "下方结束对齐")
        assertEquals(ax to (ay - ph - gap), resolve(Placement.TopStart), "上方起始对齐")
        assertEquals((ax + aw - pw) to (ay - ph - gap), resolve(Placement.TopEnd), "上方结束对齐")
        assertEquals((ax - pw - gap) to ay, resolve(Placement.LeftStart), "左侧上对齐")
        assertEquals((ax - pw - gap) to (ay + ah - ph), resolve(Placement.LeftEnd), "左侧下对齐")
        assertEquals((ax + aw + gap) to ay, resolve(Placement.RightStart), "右侧上对齐")
        assertEquals((ax + aw + gap) to (ay + ah - ph), resolve(Placement.RightEnd), "右侧下对齐")
    }

    @Test
    fun flipsUpWhenNoSpaceBelow() {
        // 锚点贴视口底部（700+40+4+120=864 > 800），上方空间充足 → 下翻上
        val (x, y) = resolve(Placement.BottomStart, ay = 700f)
        assertEquals(ax, x, "翻转后 x 不变")
        assertEquals(700f - ph - gap, y, "翻到锚点上方")
    }

    @Test
    fun flipsDownWhenNoSpaceAbove() {
        // 锚点贴视口顶部（50-120-4 < 0），下方空间充足 → 上翻下
        val (x, y) = resolve(Placement.TopStart, ay = 50f)
        assertEquals(ax, x, "翻转后 x 不变")
        assertEquals(50f + ah + gap, y, "翻到锚点下方")
    }

    @Test
    fun flipsLeftWhenNoSpaceRight() {
        // 锚点贴视口右缘（900+100+4+200 > 1000），左侧空间充足 → 右翻左
        val (x, y) = resolve(Placement.RightStart, ax = 900f)
        assertEquals(900f - pw - gap, x, "翻到锚点左侧")
        assertEquals(ay, y, "翻转后 y 不变")
    }

    @Test
    fun clampsWhenBothSidesInsufficientVertically() {
        // 视口高 150、面板高 130：下方 (40+40+4+130=214) 与上方 (40-4-130) 均不足
        // → 保持首选下方 y=84，再贴底 clamp 到 vh-ph=20
        val (x, y) = resolvePlacement(
            Placement.BottomStart, 400f, 40f, 100f, 40f, 200f, 130f, 1000f, 150f, gap
        )
        assertEquals(400f, x, "x 不受影响")
        assertEquals(20f, y, "双侧不足时贴底")
    }

    @Test
    fun clampsWhenBothSidesInsufficientHorizontally() {
        // 视口宽 150、面板宽 130：右侧 (40+40+4+130=214) 与左侧 (40-4-130) 均不足
        // → 保持首选右侧 x=84，再贴右 clamp 到 vw-pw=20
        val (x, y) = resolvePlacement(
            Placement.RightStart, 40f, 400f, 40f, 40f, 130f, 120f, 150f, 800f, gap
        )
        assertEquals(20f, x, "双侧不足时贴右")
        assertEquals(400f, y, "y 不受影响")
    }

    @Test
    fun clampsAlignOverflow() {
        // BottomEnd：锚点左缘太靠左，面板右缘对齐后 x 为负 → clamp 到 0
        assertEquals(
            0f to (300f + ah + gap),
            resolvePlacement(Placement.BottomEnd, 10f, 300f, 50f, 40f, 200f, 120f, vw, vh, gap),
            "align 越界贴左"
        )
        // BottomStart：锚点太靠右，x=900 超出 vw-pw=800 → clamp
        assertEquals(
            800f to (300f + ah + gap),
            resolvePlacement(Placement.BottomStart, 900f, 300f, 100f, 40f, 200f, 120f, vw, vh, gap),
            "align 越界贴右"
        )
        // RightStart：锚点贴近底缘，y=780 超出 vh-ph=680 → clamp
        assertEquals(
            (400f + aw + gap) to 680f,
            resolvePlacement(Placement.RightStart, 400f, 780f, 100f, 40f, 200f, 120f, vw, vh, gap),
            "纵向 align 越界贴底"
        )
    }

    @Test
    fun invalidViewportKeepsPreferredPosition() {
        // 测试 / 未测量环境浮层根尺寸为 0：不翻转、不 clamp，只出首选位
        // （保持旧实现行为，PopoverTest 等定位断言不回归）
        val (x, y) = resolvePlacement(
            Placement.BottomStart, 700f, 700f, 100f, 40f, 200f, 120f, 0f, 0f, gap
        )
        assertEquals(700f, x, "无效视口 x 仍为首选位")
        assertEquals(700f + 40f + gap, y, "无效视口不翻转")
    }

    @Test
    fun resolveIsIdempotent() {
        // 同一输入两次求解必须一致：判据里不含 x/y 自身迭代量，防布局抖动
        val p = Placement.BottomStart
        assertEquals(
            resolvePlacement(p, ax, ay, aw, ah, pw, ph, vw, vh, gap),
            resolvePlacement(p, ax, ay, aw, ah, pw, ph, vw, vh, gap),
            "重复调用结果一致"
        )
    }
}
