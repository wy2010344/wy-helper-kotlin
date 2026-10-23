package org.wy.engine

import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets

/**
 * 桌面平台日志实现。
 *
 * 显式用 UTF-8 写 stderr：Windows 控制台默认 GBK，会把中文报错（含布局依赖链诊断）
 * 打成乱码，导致开发期与 AI 读取日志时都看不出关键信息。
 */
private val utf8Err = PrintStream(FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8.name())

internal actual fun engineLogError(tag: String, error: Throwable) {
    utf8Err.println("$tag--${error.stackTraceToString()}")
}

internal actual fun engineLogWarn(message: String) {
    utf8Err.println(message)
}