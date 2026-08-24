package org.wy.engine

import android.util.Log

/** Android 平台日志实现：走 logcat 正式通道（println 不分级、难过滤）。 */
internal actual fun engineLogError(tag: String, error: Throwable) {
    Log.e("wy-engine", "$tag--$error", error)
}

internal actual fun engineLogWarn(message: String) {
    Log.w("wy-engine", message)
}
