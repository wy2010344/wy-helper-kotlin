package org.wy.engine

/**
 * 在当前 UI 线程上同步执行 [block] 并返回结果。
 *
 * signal 的批次协程跑在 Dispatchers.Main（UI 线程）。测试若在其它线程同步操作 signal
 * （set / 读 memo / flush 批次），会与 UI 线程上的批次协程并发访问非线程安全的全局态，
 * 导致 memo 出入栈不匹配或触发布次守卫。本函数把测试体投递到 UI 线程，与批次协程
 * 同线程串行执行，模拟生产环境（单一 UI 线程）的驱动方式。
 *
 * [block] 抛出的异常会被原样传播回调用线程。
 */
internal expect fun runOnUiThread(block: () -> Unit)
