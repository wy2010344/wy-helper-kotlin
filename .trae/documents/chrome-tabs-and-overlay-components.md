# 四项新功能实现计划：8 方向浮层 / 右键菜单 / Tooltip / Chrome 动态多 tab

## Context
夜间模式批次已提交（273cab1）。本轮实现用户新提的四个需求：
1. Dropdown 支持 8 方向定位，空间不足自动回退（当前只有"锚点下方"一种）。
2. 通用右键菜单组件（引擎 ContextMenu 事件已具备，缺组件层）。
3. 通用 tooltip 组件（悬停延迟出现，当前只有图表内部自绘）。
4. Demo 改造成 Chrome 式动态多 tab（可新建/关闭/多 tab 并存），左侧导航只属于第一个 tab，看板为其中一个 tab。

已核实的关键机制：
- `OverlayBase`（helper/Overlay.kt）浮层根节点铺满窗口（`argSize = layoutParent.innerSize`）→ 可当视口；钩子 `enabled/argPosition/argSize/onDismiss/trapsFocus/hide`；Esc + 点击空白关闭。
- 布局先测量后定位 → 面板 `argPosition` 内可安全读自身 `outerWidth/outerHeight` 与浮层根尺寸。
- `Node.acceptHit`（Node.kt:167）；ToastBase 先例 `acceptHit = false` 使浮层不挡命中（Toast.kt:53，ToastTest:102 断言）。
- 右键只派发 `onPointerContextMenu` 不派发 Down/Click（PointerContextMenuDoubleClickTest 已验证）。
- `postDelayed(delayMs, action): EmptyFun` 返回取消句柄（Timer.kt）；Toast 的 postDelayed+addDestroy 取消先例。
- `EngineGlobal.appendPop/removePop`（EngineGlobal.kt:86/88）挂载/卸载浮层。
- `TrackSignal`（signal/trackSignal.kt）可做"信号变化触发副作用"。
- helper 测试基建：jvmTest/helper/ 下 DropdownTest、PopoverTest、ToastTest、ButtonTest、HelperTestSupport。
- hover 先例：`by memo { g.moveHitTest?.include(this) ?: false }`（GalleryControls）。

## W1 Dropdown/Popover 8 方向 + 自动回退

**新建** `skia-engine/src/commonMain/kotlin/org/wy/engine/helper/Placement.kt`（约 80 行）：
```kotlin
enum class Placement { BottomStart, BottomEnd, TopStart, TopEnd, LeftStart, LeftEnd, RightStart, RightEnd }
/** 锚矩形(浮层内相对坐标)→面板坐标：首选侧空间不足翻转对侧，仍不足贴边 clamp；align 越界也 clamp */
fun resolvePlacement(p: Placement, ax, ay, aw, ah, pw, ph, vw, vh, gap): Pair<Float, Float>
```

**修改** `helper/Dropdown.kt`、`helper/Popover.kt`：
- 基类加 `open val placement: Placement get() = Placement.BottomStart`；工厂加 `placement` 参数（默认 BottomStart = 现行为）。
- 面板 `argPosition` 改为调 `resolvePlacement`（锚 = `anchor.absoluteX - overlay.absoluteX` / Popover 用 `anchorRect`；视口 = 浮层根 outer 尺寸；面板尺寸 = 自身 outer 尺寸）。
- 翻转判据不含自身迭代量，x/y 两次调用幂等，避免一次布局内反复翻转。

**演示** `desktopApp/.../gallery/GalleryMenu.kt`：gSelect 卡片加 8 个方向切换小按钮（页面级 placement 信号驱动）。

**测试** 新建 `jvmTest/helper/PlacementTest.kt`（穷举 8 方向、双侧不足 clamp、align 越界）+ 扩展 `DropdownTest.kt`（TopStart 集成、贴底自动翻转断言）。TDD：先写测试再实现。

## W2 通用右键菜单

**新建** `helper/ContextMenu.kt`（约 200 行，注意 300 行限制）：
```kotlin
data class MenuItem(label, onClick?, enabled=true, danger=false, divided=false, shortcut?=null)
open class ContextMenuBase(context, atX, atY, items) : OverlayBase(context)
fun StateHolder<Node, List<Node>>.showContextMenu(e: PointerEvent, items: List<MenuItem>)
```
- 调用方在目标节点 `override fun onPointerContextMenu(e) { e.stopPropagation(); showContextMenu(e, items) }`。
- 面板定位：锚取点矩形 `(e.rootX, e.rootY, 0, 0)` 复用 `resolvePlacement`（天然避让+贴边）。
- **要点**：右键不派发 Click → 点外关闭不能只靠 `OverlayBase.onPointerClick`，需 override `onPointerContextMenu`：命中在浮层空白处时 `onDismiss()`。
- `trapsFocus = true`（菜单内 Tab 循环），打开聚焦首项；样式走 `Theme.current`（已信号化，自动昼/夜）。

**演示** 新建 `desktopApp/.../gallery/GalleryOverlay.kt`（第六行卡片：表格行/卡片右键，含 danger+divided 项）；`DemoGallery.kt` galleryContent 末尾接入。

**测试** 新建 `jvmTest/helper/ContextMenuTest.kt`：参照 PointerContextMenuDoubleClickTest 的真实生命周期（右键打开、点项触发并关闭、disabled 不触发、点外/Esc 关闭、贴右/下边 clamp、右键仍不派发 Down/Click）。

## W3 通用 Tooltip

**新建** `helper/Tooltip.kt`：
```kotlin
open class TooltipBase(context, anchor: LayoutNode, text: () -> String, scheduler = ::postDelayed) : OverlayBase(context)
    open val placement / delayMs=400L / override fun acceptHit(...) = false
fun <T: Node> ...attachTooltip(anchor, text, placement, delayMs, scheduler): T
```
- **hover 防抖动（关键）**：浮层根铺满窗口会吞 `moveHitTest` → `acceptHit = false` 让命中穿透（ToastBase 先例），锚点 hover 不抖动。
- 状态机：hover 进入 → `postDelayed(400ms)` 存取消句柄 → 到期 `appendPop` 显示；移出 → 先 cancel 未到期句柄、已显示则 `removePop`（立即隐藏）。用 `TrackSignal`/`memo` 驱动，`addDestroy` 兜底清理。
- 面板定位复用 `resolvePlacement`（自动避让）。

**演示** `GalleryOverlay.kt` 加 tooltip 卡片（按钮/徽标 hover 400ms 出提示，含贴边翻转样例）。

**测试** 新建 `jvmTest/helper/TooltipTest.kt`：仿 ToastTest 用假 scheduler（TimerRecord）+ ButtonTest 的 `g.moveHitTest` 驱动：hover 按 400ms 调度、到期挂载、移出立即移除并取消句柄、销毁清理、`acceptHit=false` 回归。

## W4 Chrome 动态多 tab

**新建** `desktopApp/.../DemoChrome.kt`（避免 DemoGrand 已超 300 行）：
- `data class ChromeTab(id: Long, type: DemoTab)` + 状态操作（放 buildGrandDemo 内、传给本文件函数）。
- tab 条节点：插在 argChildren 最首位（约 36f 高，BG 底+下分隔线）；每 tab 自绘（标题 + 「×」子热区 stopPropagation 后关闭），激活态高亮；末尾「+」→ `appendPop + dropdown()`（复用 W1 placement）列 5 种页型，允许重复。
- `focusTab(id)` / `openTab(type)`（已有则聚焦，无则新建激活）/ `closeTab(id)`（最后一个不关；关激活取左邻、无则右邻）。

**修改** `DemoGrand.kt`：
- 状态：`tabs: StoreRef<List<ChromeTab>>`（初始 5 个全开）+ `activeId`；保留现有 `active: StoreRef<DemoTab>` 激活页型信号，各操作闭包同步它（页面/状态栏签名不变）。
- 删除内容区 segTabs 分发，页面仍自带 `hide = active.value != 类型`（只渲染激活页）。
- 侧栏 `hide = 激活页不是 tabs[0]`；navItem onClick 改 focus-or-open(type)。
- 快捷键：Ctrl+1..9 聚焦第 N 个 tab、**Ctrl+T 新建、Ctrl+W 关闭当前**；保留 Ctrl+N（新增动态）/Ctrl+K；dStatusBar 文案同步。
- 看板 = 普通页型 tab（DASHBOARD 已是 DemoTab 成员）。

**修改** `DemoShot.kt`：`renderApp(tab)` = 把该页型排 `tabs[0]` 并激活 → 侧栏在场，保持既有探针坐标系假设。

**shot 探针回填**：布局净位移 = +36（tab 条）−（去 segTabs 行），**必须整轮重测不可手算**：先 `SHOT_DEBUG_TREE=1` 跑 shot 量几何，再回填。受影响段落：renderTyped mouseDown(944,28)、zoom bar/ink/spill 坐标、scrollbar scanRow/scanCol、gallery rows(gap) 与 header zoom、cardSpill y0、dash 蜡烛/book/depth/chart 探针、dark bg(900,40)。gallery 新增第六卡片行也会改变行位。

## 实施顺序与验证
1. **W1**（PlacementTest 先行 → 接入 → 演示）→ **W2** → **W3**（两者依赖 resolvePlacement）→ **W4**（独立，探针单独一轮回填）。
2. 每个工作项：`.\gradlew.bat :skia-engine:compileKotlinJvm :desktopApp:compileKotlin --no-daemon -q` 编译 → `test jvmTest` 全量 → 最终 `:desktopApp:shot` 目检+探针核对（注意：沙箱会拦 kotlin daemon 临时文件，测试需非沙箱运行）。
3. 手工清单（实窗或 shot）：8 方向切换+贴边翻转、右键菜单开/选/关、tooltip hover 延迟与移出即隐、tab 新建/关闭/聚焦/侧栏显隐/快捷键。
4. 分两个 commit 提交推送：①W1-W3 引擎浮层组件（含测试）②W4 Chrome tab + 探针回填。

## 风险
- 右键无 Click 事件 → 菜单空白处关闭必须走 `onPointerContextMenu` 覆写。
- Tooltip 必须 `acceptHit = false`，否则命中被浮层吞掉导致 hover 抖动（配回归测试）。
- `argPosition` 翻转判据不可含自身迭代量（防布局抖动）。
- W4 探针双向位移严禁手算，整轮重测回填。
