package org.wy.engine

import java.text.BreakIterator
import java.util.Locale as JavaLocale

actual fun lineBreakOpportunities(text: String, locale: String?): List<Int> {
    if (text.isEmpty()) return emptyList()
    val bi = if (locale != null) BreakIterator.getLineInstance(JavaLocale.forLanguageTag(locale))
             else BreakIterator.getLineInstance()
    bi.setText(text)
    val result = mutableListOf<Int>()
    var pos = bi.first()
    while (pos != BreakIterator.DONE) {
        if (pos > 0) result.add(pos)
        pos = bi.next()
    }
    return result
}
