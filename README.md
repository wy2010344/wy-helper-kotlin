# wy-helper

基于 **Kotlin Multiplatform + Skia（skiko）** 的声明式、信号驱动 GUI 引擎。用 Kotlin 对象描述节点树，引擎负责布局、绘制与事件分发。

[![CI](https://github.com/wy2010344/wy-helper-kotlin/actions/workflows/ci.yml/badge.svg)](https://github.com/wy2010344/wy-helper-kotlin/actions/workflows/ci.yml)
[![tests](https://img.shields.io/badge/tests-292%20passing-brightgreen)](https://github.com/wy2010344/wy-helper-kotlin/actions)
[![license](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
[![kotlin](https://img.shields.io/badge/kotlin-2.4.0-purple)](https://kotlinlang.org/)
[![platform](https://img.shields.io/badge/platform-desktop%20%7C%20android%20%7C%20web-orange)](#)

> <!-- TODO: 录一张 3–6 秒 GIF 放在这里，展示 DemoGrand 概览页的图表 + 列表 + Tab 切换。控制在 3MB 以内。 -->
> <!-- ![demo](docs/demo.gif) -->

## 能做什么

一个"小而完整"的 GUI 引擎——不是组件库，是从信号模型到像素绘制的全链路实现：

- **声明式节点树**：`object : XxxNode(this) { override val ... }` 匿名类描述界面，构造只执行一次
- **信号驱动**：`createSignal` / `memo` / `addEffect` 反应式状态，依赖变更才重算
- **自研布局**：Flex / Stack / absolute，零外部布局依赖
- **原生绘制**：Skia 直绘，支持渐变、圆角、裁剪、变换、缓存图层
- **文字与输入**：自动换行、富文本 span、可编辑输入框、选区/光标/IME、Undo/Redo
- **事件与焦点**：指针命中测试、键盘导航、焦点圈定（Dialog focusTrap）、快捷键
- **组件库**：Button / TextField / Switch / Slider / SegTabs / NavItem / Dropdown / Dialog / Popover / Toast
- **多平台**：Desktop（JVM）· Android · Web，核心逻辑在 `commonMain`

## 快速开始

### 直接运行（无需编译）

从 [Releases](https://github.com/wy2010344/wy-helper-kotlin/releases) 下载 `wy-helper-demo-windows-x64.zip`，解压后双击 `run.bat`（需本机有 JRE 21+）。

### 从源码运行

```bash
# 运行桌面展示 Demo（概览页：图表 + 动态列表 + 多 Tab）
./gradlew :desktopApp:run
```

最小窗口：

```kotlin
fun main() {
    object : SkiaApp(800, 600), FlexParam {
        override val layout = FlexObject(this)
        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            object : WrappedTextNode(this) {
                override val text get() = "hello wy-helper"
                override val fontSize get() = 24f
            }
        }
    }
}
```

## 仓库结构

| 模块 | 作用 |
|---|---|
| `skia-engine` | 引擎核心：节点、布局、绘制、文字、输入、组件库 |
| `layout` | 布局算法：Flex / Stack / absolute |
| `signal` | 信号与 memo（反应式状态） |
| `mve` | 节点树与列表渲染（`renderForEach`） |
| `desktopApp` | 桌面 Demo（运行入口） |

## 文档

- [设计取舍](DESIGN.md)：为什么用 Skia 直绘、为什么不做 diff、信号系统的纯派生链
- [设计哲学](docs/00-设计哲学.md)：单向数据流、信号写入边界、派生规则
- [快速上手](docs/01-快速上手.md)：20 行搭起第一个窗口
- [节点与布局](docs/02-节点与布局.md)：匿名类覆盖、信号、Flex 布局
- [文字](docs/03-文字.md)：文本、富文本、可编辑输入框
- [图片与绘制](docs/04-图片与绘制.md)：位图、画布图元、透明度与变换
- [列表与滚动](docs/05-列表与滚动.md)：数据列表、滚动容器
- [事件与焦点](docs/06-事件与焦点.md)：鼠标、键盘、焦点、剪贴板
- [多平台](docs/07-多平台.md)：各平台需要实现什么
- [组件库](docs/08-组件库.md)：标准组件与主题
- [动画信号](docs/09-动画信号.md)：Tween / Spring / AnimateSignal

## 技术栈

Kotlin 2.4 · Kotlin Multiplatform · Skia (skiko) · Compose Multiplatform (仅 desktop 宿主) · Gradle
