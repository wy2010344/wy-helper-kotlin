package org.wy.engine

/** 桌面平台日志实现：stderr 输出（保持既有 "tag--error" 格式）。 */
internal actual fun engineLogError(tag: String, error: Throwable) {
    System.err.println("$tag--$error")
}

internal actual fun engineLogWarn(message: String) {
    System.err.println(message)
}
