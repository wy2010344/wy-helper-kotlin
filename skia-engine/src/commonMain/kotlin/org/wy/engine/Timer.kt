package org.wy.engine

import org.wy.lib.EmptyFun

/**
 * 延时调度：[delayMs] 毫秒后在平台主线程执行 [action]，返回取消句柄。
 *
 * 与渲染无关的时间驱动逻辑（如 Toast 定时关闭）使用，
 * 保证即使没有后续重绘也能按时触发。
 */
expect fun postDelayed(delayMs: Long, action: () -> Unit): EmptyFun
