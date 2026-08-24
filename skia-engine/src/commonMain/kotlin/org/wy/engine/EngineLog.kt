package org.wy.engine

/**
 * 引擎内部日志（跨平台 expect/actual），仅用于异常兜底与诊断输出。
 *
 * - 桌面（JVM）：写入 stderr；
 * - Android：走 android.util.Log 正式通道（println 不进 logcat 分级）。
 *
 * internal：不作为公共 API 暴露；引擎代码的 catch 兜底一律用它替代 println。
 */
internal expect fun engineLogError(tag: String, error: Throwable)

/** 引擎内部警告日志（如字体回退等非异常提示）。 */
internal expect fun engineLogWarn(message: String)
