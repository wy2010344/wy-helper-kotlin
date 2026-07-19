package org.wy.engine

actual enum class KeyCode(val awtCode: Int) {
    Backspace(java.awt.event.KeyEvent.VK_BACK_SPACE),
    Delete(java.awt.event.KeyEvent.VK_DELETE),
    Left(java.awt.event.KeyEvent.VK_LEFT),
    Right(java.awt.event.KeyEvent.VK_RIGHT),
    Home(java.awt.event.KeyEvent.VK_HOME),
    End(java.awt.event.KeyEvent.VK_END),
    Up(java.awt.event.KeyEvent.VK_UP),
    Down(java.awt.event.KeyEvent.VK_DOWN),
    Enter(java.awt.event.KeyEvent.VK_ENTER),
    Tab(java.awt.event.KeyEvent.VK_TAB),
    Escape(java.awt.event.KeyEvent.VK_ESCAPE),
    Unknown(-1);

    companion object {
        fun fromAwt(code: Int): KeyCode =
            entries.firstOrNull { it.awtCode == code } ?: Unknown
    }
}
