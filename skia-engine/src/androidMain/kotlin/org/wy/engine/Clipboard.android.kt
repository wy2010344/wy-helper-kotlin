package org.wy.engine

// Android 平台暂时不支持剪贴板（需要 Activity Context），
// 等桌面端成熟后再通过 EngineGlobal 注入。
actual fun clipboardGetText(): String? {
    throw NotImplementedError("clipboard is not supported on Android yet")
}

actual fun clipboardSetText(text: String) {
    throw NotImplementedError("clipboard is not supported on Android yet")
}
