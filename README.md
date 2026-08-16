# wy-helper

基于 **Kotlin Multiplatform + Skia（skiko）** 的声明式、信号驱动 GUI 引擎。你用 Kotlin 对象描述界面节点树，引擎负责布局、绘制与事件分发。目前主要在 **Desktop (JVM)** 上运行。

## 仓库结构

| 模块 | 作用 |
|---|---|
| `skia-engine` | 引擎核心：节点、布局、绘制、文字、输入（`commonMain` 通用 + `jvmMain` Desktop 实现） |
| `layout` | 布局算法：Flex / Stack / absolute |
| `signal` | 信号与 memo（反应式状态） |
| `mve` | 节点树与列表渲染（`renderForEach`） |
| `desktopApp` | 桌面 Demo（运行入口） |

## 运行 Demo

```bash
./gradlew :desktopApp:run
```

Demo 入口：`desktopApp/src/main/kotlin/org/wy/engine/DemoMain.kt`，窗口里演示了文字、输入框、图片、图形、可滚动列表等常见用法。

## 文档

面向使用者的文档（按顺序阅读即可快速上手）：

- [01 快速上手](docs/01-快速上手.md)：20 行搭起第一个窗口
- [02 节点与布局](docs/02-节点与布局.md)：匿名类覆盖、信号、Flex 布局、尺寸
- [03 文字](docs/03-文字.md)：文本显示、富文本、可编辑输入框
- [04 图片与绘制](docs/04-图片与绘制.md)：位图、画布图元、透明度与变换
- [05 列表与滚动](docs/05-列表与滚动.md)：数据列表、滚动容器、滚动条
- [06 事件与焦点](docs/06-事件与焦点.md)：鼠标、键盘、焦点、剪贴板
- [07 多平台](docs/07-多平台.md)：各平台需要实现什么
- [08 组件库](docs/08-组件库.md)：标准组件（交互内置、样式可自定义）、主题
