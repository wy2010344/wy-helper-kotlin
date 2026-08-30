package org.wy.engine

/** 桌面平台日志实现：stderr 输出，错误带完整堆栈便于按日志定位到源头。 */
internal actual fun engineLogError(tag: String, error: Throwable) {
    System.err.println("$tag--${error.stackTraceToString()}")
}

internal actual fun engineLogWarn(message: String) {
    System.err.println(message)
}
