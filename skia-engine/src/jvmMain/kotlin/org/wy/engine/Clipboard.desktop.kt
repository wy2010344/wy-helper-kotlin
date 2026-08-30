package org.wy.engine

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

actual fun clipboardGetText(): String? {
    return try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) return null
        clipboard.getData(DataFlavor.stringFlavor) as? String
    } catch (e: Throwable) {
        engineLogWarn("clipboardGetText error--${e.stackTraceToString()}")
        null
    }
}

actual fun clipboardSetText(text: String) {
    try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    } catch (e: Throwable) {
        engineLogWarn("clipboardSetText error--${e.stackTraceToString()}")
    }
}
