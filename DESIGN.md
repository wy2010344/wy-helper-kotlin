# DESIGN.md

> 本文只写**取舍**，不写功能介绍。功能见 [README](README.md) 与 [docs/](docs/)。

## 1. 为什么直接用 Skia（skiko），而不是 Compose Desktop 的现成能力

Compose Desktop 底层也是 Skia，但它把渲染管线抽象成了 `Modifier.drawBehind` 之类的声明式画槽。本项目直接操作 `org.jetbrains.skia.Canvas`：

- **取舍**：拿到完整的 Canvas API（`saveLayerAlpha`、`drawPath`、渐变、变换矩阵），可以做缓存图层、命中测试用的几何路径、自定义画笔效果；代价是失去 Compose 的跨平台渲染抽象，Web/Android 需要各自实现 `PlatformCanvas`。
- **为什么值得**：GUI 引擎的核心竞争力在绘制能力，而不是"能画个矩形"。直接用 Skia 让渐变、圆角裁剪、图层透明度这些操作和原生应用没有差距。

## 2. 为什么节点树用「匿名类覆盖」，而不是 data class + diff

主流声明式框架（React、Compose、Flutter）都有一棵"描述树"和一棵"真实树"，每帧 diff。本项目只有一棵树：

- **取舍**：节点用 `object : XxxNode(this) { override val ... }` 匿名类创建，**构造只执行一次**，之后靠信号驱动属性 getter 重算。没有虚拟 DOM、没有 diff、没有 reconciliation。
- **代价**：列表更新需要 `renderForEach` 手动管理 key；条件渲染不能用 `if` 直接建节点（因为 `argChildren` 只跑一次），得用 `hide` 属性或信号切换。
- **为什么值得**：零 diff 开销 = 大列表不卡；节点生命周期可预测（创建一次、dispose 一次），不需要"effect 依赖数组"这种心智负担。

## 3. 为什么自研一维布局，而不是用 Taffy / Compose 布局引擎

- **取舍**：场景只有 Flex / Stack / absolute 三种，不需要 CSS Grid 那套。自研布局 = 零外部依赖、行为可预测、报错信息是自己的。
- **代价**：换行（wrap）、复杂的 `flex-basis` 计算需要自己实现；不像 Taffy 那样有 W3C 规范级别的兼容性。
- **为什么值得**：布局是热路径，自研可以针对节点树的特性做优化（比如 `IgnoreFlex` 让浮层子节点不占主轴空间），用现成库反而要绕开它的假设。

## 4. 信号系统：纯派生链，零命令式同步

这是本项目最核心的设计决策（详见 [00-设计哲学](docs/00-设计哲学.md)）：

- **取舍**：选区、焦点、列表项状态这些"派生态"**不写入信号**，只从原始信号计算。`SelectionManager` 没有 `setSelection()` 方法——"哪里被选中"是指针会话 + 编辑器光标的纯函数。
- **代价**：初次接触反直觉（"我想选中一段文字怎么没有 API？"），需要理解"命令 = 写原始信号，让派生链自己流动"。
- **为什么值得**：彻底消除状态同步 bug。历史教训：旧版编辑器每次键盘扩选要手动调 6 处 `setSelection` 同步全局会话，重构后全部删除，行为不变。

**判别式**：删掉这条 set，信息还能不能沿数据流自动到达？能 → 这条 set 是多余的。

## 5. SelectionManager 的优先级链

多个来源竞争同一状态时（"当前选区是什么"），不写"让 A 失效"的命令，而是写成优先级表达式：

```
currentPair = 按住中的指针会话      // #1 最高
           ?: 非塌缩的编辑器光标    // #2
           ?: 定格的指针会话        // #3
           ?: 塌缩的编辑器光标      // #4
           ?: 程序化选区            // #5 最低
```

高优先级出现即自然遮蔽低者——**让位是数据流的性质，不是某段代码执行的效果**。这比"事件触发时遍历清场再标记"简单且无竞态。

## 6. focusTrap：为什么在 Renderer 移动焦点时遍历子树

Dialog 打开时 Tab 只能在框内循环。实现方式不是全局拦截 Tab 事件，而是：

- `OverlayBase.trapsFocus = true` 时，节点声明 `focusTrap`；
- `Renderer` 在计算"下一个可聚焦节点"时，如果当前焦点在 trap 内，遍历 trap 子树找下一个，找不到就回到子树第一个。

- **取舍**：焦点导航的逻辑集中在 Renderer 一处，浮层只需声明"我圈定焦点"，不用自己处理 Tab。代价是 Renderer 需要知道 `focusTrap` 的语义。
- **为什么值得**：如果用全局拦截，每个浮层都要注册/注销快捷键，容易遗漏；集中在 Renderer 后，Dialog、Dropdown（非模态不 trap）、未来的 Popover 都复用同一套机制。

## 7. Overlay 体系：Dialog / Dropdown / Toast 收敛到 OverlayBase

浮层有共性：铺满窗口、打开时聚焦、关闭时还原焦点、Esc/点外部关闭。差异只有：是否模态（trap focus）、样式、内容。

- `OverlayBase` 提供共性（铺满、焦点移入还原、Esc 关闭、`notInLayout` 不占 flex 空间）；
- `dialog` / `dropdown` / `toast` 工厂给出默认样式和 `trapsFocus` 取值。

- **取舍**：一个基类 + 几个工厂，而不是每个浮层各写一套。代价是基类需要足够通用，新增浮层类型时要确认基类能覆盖。

## 8. 平台分离：expect/actual 的边界

核心逻辑（节点树、布局、信号、选区）全在 `commonMain`。平台相关的只有：

- 文字：换行（`LineBreaker`）、字素簇边界（`Graphemes`）——交给平台 `BreakIterator`
- 图片解码、剪贴板、光标、定时器、窗口/视图

- **取舍**：文字排版是平台差异最大的地方（中英文混排、emoji 字素簇），不自己实现 UAX#29，用平台原生能力。代价是 expect/actual 声明要维护三套。
- **为什么值得**：字素簇边界自己写容易出错（历史上写过近似版，边缘 case 一直漏），平台 `BreakIterator` 是经过验证的。
