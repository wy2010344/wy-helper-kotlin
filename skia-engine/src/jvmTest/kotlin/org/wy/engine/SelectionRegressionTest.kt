package org.wy.engine

import com.wy.mve.StateHolderWithNode
import org.wy.signal.createSignal
import org.wy.signal.batchSignalEnd
import org.wy.signal.getValue
import org.wy.signal.setValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 文本编辑器选区关键行为回归测试（真 Renderer 渲染树，与生产结构一致）。
 *
 * 保护两个严重回归：
 *   Bug 1：编辑器上拖选 → 释放 → 选区立即消失
 *          根因：cursorSelPair 即使塌缩（anchor==focus）也为非 null，在
 *          SelectionManager.currentPair 旧 4 级优先级中压制了"已定格指针选区"，
 *          导致释放瞬间高亮消失。修复后为 5 级优先级（塌缩光标降到定格指针之后）。
 *
 *   Bug 2：双击 / 三击写好的词选区被覆盖
 *          根因：Renderer 双击路径先 select 写入本地词选区、再 dispatchPointer(DOWN)，
 *          EditableTextNode.onPointerDownCapture 的 setCursor 把刚写好的选区塌缩。
 *          修复后 capture 入口检测到非塌缩分配即跳过 setCursor。
 *
 * 额外回归保护：
 *   - select 的编辑器分流路由跟随最新焦点（activeEditor 即时派生）；
 *   - 编辑器本地键盘非塌缩扩选（Shift+→）仍压制定格指针选区。
 */
class SelectionRegressionTest {

    @org.junit.After
    fun drainSignalBatch() {
        Thread.sleep(50)
        batchSignalEnd()
    }

    /**
     * 可编程换算坐标的 MockText（不依赖 paragraph、纯 Selectable 实现，不会触发 Layout
     * 要求），挂 Renderer 真实 argChildren 树下：文档序正确、activeEditor 派生链生效，
     * 同时也不会触发 "未找到父节点" 的 sizeFromParent LayoutError。
     */
    private class MockText(
        state: StateHolderWithNode<Node, List<Node>>,
        var text: String = ""
    ) : Node(state), Selectable {
        var posResolver: (Float, Float) -> Int = { x, _ -> x.toInt() }

        var hidden_ by createSignal(false)
        fun setHidden(v: Boolean) { hidden_ = v }
        override val hide: Boolean get() = hidden_

        override val textLength: Int get() = text.length
        override fun positionForPoint(globalX: Float, globalY: Float): Int =
            posResolver(globalX, globalY).coerceIn(0, text.length)
        override fun textInRange(start: Int, end: Int): String =
            if (end > start) text.substring(start, end.coerceAtMost(text.length)) else ""
        override fun selectionRect(): RectF? = null
        override fun wordRangeAt(offset: Int): Pair<Int, Int>? {
            if (offset < 0 || offset > text.length) return null
            var s = offset
            while (s > 0 && text[s - 1] != ' ') s--
            var e = offset
            while (e < text.length && text[e] != ' ') e++
            return if (s < e) s to e else null
        }
    }

    private class Env {
        lateinit var renderer: Renderer
        lateinit var plain: MockText      // 普通可选文本（不会走到 paragraph/Layout）
        lateinit var editor: EditableTextNode  // 活跃编辑器（可 focus 触发 activeEditor）

        val g: EngineGlobal get() = renderer.engineGlobal
        val m: SelectionManager get() = renderer.engineGlobal.selectionManager

        fun build(editorText: String = "Hello World", plainText: String = "Foo bar baz qux") {
            renderer = object : Renderer(null) {
                override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                    plain = MockText(this, plainText)
                    plain.posResolver = { x, _ -> x.toInt() }
                    editor = object : EditableTextNode(this) {
                        init { text = editorText }
                    }
                }
            }
            renderer.children
        }

        fun hit(node: Node, x: Float = 0f): HitestResult =
            HitestResult(listOf(NodeWithPosition(node, x, 0f)), 0L, x, 0f)

        fun blankHit(): HitestResult = HitestResult(emptyList(), 0L, 0f, 0f)
    }

    // ===== Bug 1：编辑器已聚焦 + 拖选释放后选区丢失 =====
    //
    // 流程：focused=editor（→activeEditor!=null） → editor 光标塌缩（cursorSelPair非null）
    //       → 在 plain 上拖拽 [0..3] → 释放
    // 期望：释放后定格选区（pointerSelect.release != null）仍为 [0..3]，selectedText 不变
    // Bug：cursorSelPair（塌缩，anchor==focus）优先级 2 高于定格指针优先级 3，压制全部选区
    //       → selectedText 变空
    @Test
    fun editorFocusedDoesNotSuppressFrozenPointerSelection() {
        val env = Env().also { it.build() }
        env.g.focused = env.editor         // activeEditor = editor（非null）
        env.editor.moveCursorTo(0)         // 光标塌缩：cursorSelPair = SelPair(editor@0, editor@0)

        // 在 plain 上拖拽 plain@0 → plain@3：选中 "Foo " 前 3 字符 "Foo"
        env.g.pointerSelect = PointerSelect(env.hit(env.plain, 0f), null)
        env.g.moveHitTest = env.hit(env.plain, 3f)
        assertEquals(
            env.plain.text.substring(0, 3),
            env.m.selectedText,
            "拖拽中（按住）plain 选区应生效（#1 指针按住优先级）"
        )

        // ===== Release =====
        env.g.pointerSelect = PointerSelect(env.hit(env.plain, 0f), env.hit(env.plain, 3f))

        // 期望：定格选区（priority #3）仍为 [0,3)，不能被 editor 的塌缩 cursorSelPair 压制
        val range = env.m.rangeOf(env.plain)
        assertNotNull(range, "释放后 plain 选区不应被编辑器的塌缩光标压制为 null")
        assertEquals(0 to 3, range, "释放后选区范围不变")
        assertEquals(env.plain.text.substring(0, 3), env.m.selectedText, "释放后 selectedText 仍为拖拽结果")
    }

    // ===== select 路由跟随焦点 =====
    //
    // 场景：edA 聚焦（activeEditor=edA），随后焦点切到 edB（双击/点击路径都会先
    // setFocused(leaf) 再 select）。activeEditor 是 focused 的即时派生，select 的
    // 编辑器分流必须路由到**新** activeEditor（edB），而不是残留的旧编辑器。
    @Test
    fun selectRoutesToNewlyFocusedEditor() {
        val env = object {
            lateinit var renderer: Renderer
            lateinit var edA: EditableTextNode
            lateinit var edB: EditableTextNode
            lateinit var plain: MockText
            val g get() = renderer.engineGlobal
            val m get() = renderer.engineGlobal.selectionManager
        }
        env.renderer = object : Renderer(null) {
            override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
                env.edA = object : EditableTextNode(this) {
                    init { text = "Alpha editor" }
                }
                env.edB = object : EditableTextNode(this) {
                    init { text = "Beta editor text" }
                }
                env.plain = MockText(this, "plain text")
            }
        }
        env.renderer.children

        // 聚焦 edA → activeEditor=edA
        env.g.focused = env.edA
        assertTrue(env.g.activeEditor === env.edA)

        // 焦点切到 edB（等价于双击 edB 时引擎先 setFocused(leaf)），
        // 随后 select(edB, 0, 4) 应分流给 edB 本地信号（headless 不走词解析）
        env.g.focused = env.edB
        val ok = env.m.select(env.edB, 0, env.edB, 4)

        assertTrue(ok, "焦点切换后，select 目标等于新 activeEditor 应能成功")
        assertEquals("Beta", env.m.selectedText)

        // 目标不是聚焦编辑器：全局唯一选区会话无第二语境，拒绝
        assertFalse(env.m.select(env.plain, 0, 5))
        assertEquals("Beta", env.m.selectedText, "拒绝后原选区不变")
    }

    // ===== 双击编辑器：selectRange 写入词选区后不应再被 onPointerDownCapture 的 setCursor 覆盖 =====
    //
    // Renderer.mouseDown (clickCount==2) 流程：
    //   1. setFocused(leaf=editor)
    //   2. selectionManager.select(editor, word.first, editor, word.second)  → ed.selectRange
    //      → 本地 anchor!=focus，非塌缩选区（词选中）
    //   3. dispatchPointer(DOWN) → leaf.onPointerDownCapture(EditableTextNode) → setCursor(pos)
    //
    // Bug 2（双击覆盖）：步骤 3 的 setCursor(collapsed) 把步骤 2 写好的词选区再次塌缩。
    // 修复后：onPointerDownCapture 开头检查 assignedRange 是否已非塌缩分配给本编辑器，
    //          是则 early return 跳过 setCursor，保留选词/选段结果。
    //
    // 注意：必须真实调用 onPointerDownCapture 才能保护修复点——
    // 仅断言 rangeOf 无法区分"有守卫"与"无守卫"（后者会塌缩成 null / 单点）。
    @Test
    fun doubleClickOnEditorWordNotOverwrittenByPointerDownCapture() {
        val env = Env().also { it.build(editorText = "Hello World") }
        env.g.focused = env.editor
        env.editor.moveCursorTo(0)

        // 双击路径步骤 2：select(editor, 0, editor, 5) → 本地词选区 [0,5)
        val ok = env.m.select(env.editor, 0, env.editor, 5)
        assertTrue(ok)
        assertEquals("Hello", env.m.selectedText, "select 后应有词选区")
        assertEquals(0 to 5, env.m.rangeOf(env.editor))

        // 步骤 3：dispatchPointer(DOWN) 会投递 onPointerDownCapture；
        // 无守卫时 setCursor(e.x 换算位) 会把词选区塌缩 → rangeOf 变空
        env.editor.onPointerDownCapture(
            PointerEvent(type = PointerType.Down, x = 1f, y = 1f)
        )

        // 修复后：early return，选区原样保留
        assertEquals(0 to 5, env.m.rangeOf(env.editor), "down-capture 不应覆盖双击写好的词选区")
        assertEquals("Hello", env.m.selectedText)
    }

    // ===== 回归：外部按下塌缩编辑器旧选区，拖选 / 选词不被遮蔽 =====
    //
    // 根因：currentPair 的 #2（活跃编辑器非塌缩光标）高于 #3（定格指针会话）。
    // 若编辑器持有扩展选区后指针在别处完成一次拖选，旧派生链会让编辑器旧选区
    // 遮蔽新拖选结果（rangeOf(plain) 为 null）。修复：mouseDown 在建立会话前，
    // 按下落点不在活跃编辑器内时先调用 collapseExternalSelection() 塌缩其选区
    // （平台惯例"外部按下即让位"）。下面按生产等价步骤驱动。
    @Test
    fun externalPressCollapsesEditorSelectionSoFrozenDragWins() {
        val env = Env().also { it.build() }
        env.g.focused = env.editor
        env.editor.selectAll() // 编辑器内非塌缩选区
        assertEquals(env.editor.text, env.m.selectedText)

        // 拖选 plain [0,3)：按下瞬间 mouseDown 先塌缩外部编辑器选区
        env.g.pointerSelect = PointerSelect(env.hit(env.plain, 0f), null)
        env.editor.collapseExternalSelection()
        env.g.moveHitTest = env.hit(env.plain, 3f)
        assertEquals("Foo", env.m.selectedText, "拖选中应即时生效")

        // 定格释放后不被任何残留选区压制
        env.g.pointerSelect = PointerSelect(env.hit(env.plain, 0f), env.hit(env.plain, 3f))
        assertEquals(0 to 3, env.m.rangeOf(env.plain), "定格拖选应胜过旧编辑器扩展选区")
        assertEquals("Foo", env.m.selectedText)
    }

    // ===== 回归：双击普通文本选词同样不被旧编辑器选区遮蔽 =====
    //
    // 双击路径不开 pointerSelect 会话；若编辑器旧的非塌缩选区未被塌缩，
    // #2 会压制刚物化的词选区。mouseDown 现在会在双击分支前统一塌缩。
    @Test
    fun externalPressCollapsesEditorSelectionBeforeDoubleClickWordPick() {
        val env = Env().also { it.build() }
        env.g.focused = env.editor
        env.editor.selectAll()

        // 双击 plain 中 "bar"（生产等价：先塌缩，再 setFocused(leaf)，最后物化词选区）
        env.editor.collapseExternalSelection()
        env.g.focused = env.plain
        assertTrue(env.m.select(env.plain, 4, env.plain, 7))

        assertEquals("bar", env.m.selectedText, "词选区不应被旧编辑器选区遮蔽")
    }

    // ===== 回归：编辑器本地键盘非塌缩扩选仍优先 =====
    @Test
    fun keyboardExtendedSelectionStillTakesPrecedence() {
        val env = Env().also { it.build() }
        env.g.focused = env.editor
        // 定格指针选区：用 plain（不会触发 paragraph layout）[0..3] = "Foo"
        env.g.pointerSelect = PointerSelect(env.hit(env.plain, 0f), env.hit(env.plain, 3f))
        assertEquals("Foo", env.m.selectedText)

        // 调键盘扩选：Shift+Right × 2
        env.editor.moveCursorTo(0)
        repeat(2) {
            env.editor.handleKey(KeyEvent('\u0000', KeyCode.Right, ctrl = false, shift = true, alt = false, meta = false))
        }
        // editor 本地 [0,2) "He"，应压制定格选区（plain 的 "Foo"）
        val sel = assertNotNull(env.m.selectedText, "本地有非塌缩选区时 selectedText 非空")
        assertEquals("He", sel, "键盘非塌缩扩选应压制定格指针选区")
    }
}
