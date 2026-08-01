package org.wy.engine

// ════════════════════════════════════════════════════
// 调色板
// ════════════════════════════════════════════════════
internal val BG = rgba(244, 246, 251)
internal val CARD = rgba(255, 255, 255)
internal val BORDER = rgba(226, 232, 240)
internal val TEXT = rgba(30, 41, 59)
internal val TEXT2 = rgba(100, 116, 139)
internal val ACCENT = rgba(79, 70, 229)
internal val ACCENT_HOVER = rgba(99, 91, 255)
internal val ACCENT_DARK = rgba(63, 55, 216)
internal val ACCENT_LIGHT = rgba(224, 231, 255)
internal val BAR = rgba(199, 210, 254)
internal val GREEN = rgba(16, 185, 129)
internal val AMBER = rgba(245, 158, 11)
internal val RED = rgba(239, 68, 68)
internal val GRID = rgba(241, 245, 249)
internal val PLACEHOLDER = rgba(148, 163, 184)

internal fun Node.isVisibleToRoot(): Boolean {
    var n: Node? = this
    while (n != null) {
        if (n.hide) return false
        n = n.parent
    }
    return true
}
