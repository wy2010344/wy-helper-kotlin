package org.wy.engine

/**
 * UAX #14 断行机会检测。
 *
 * 返回 text 中所有合法的行断开位置（索引，不含 0 和 text.length）。
 * - JVM: java.text.BreakIterator.getLineInstance()
 * - Android: android.icu.text.BreakIterator.getLineInstance()
 */
expect fun lineBreakOpportunities(text: String, locale: String? = null): List<Int>
