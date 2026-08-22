package org.wy.engine

/**
 * 全局链接打开器：由各平台宿主注册实现
 * （Desktop 的 `Desktop.browse`、Android 的 Intent、Web 的 `window.open`）。
 * 未注册时点击链接静默忽略——引擎层不持有平台环境（如 Android Context）。
 */
object UrlOpener {
    /** 平台注入的打开实现。 */
    var opener: ((String) -> Unit)? = null

    fun open(url: String) {
        opener?.invoke(url)
    }
}
