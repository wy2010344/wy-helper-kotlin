package org.wy.engine

import android.view.KeyEvent as AndroidKeyEvent

actual enum class KeyCode(val androidCode: Int) {
    Backspace(AndroidKeyEvent.KEYCODE_DEL),
    Delete(AndroidKeyEvent.KEYCODE_FORWARD_DEL),
    Left(AndroidKeyEvent.KEYCODE_DPAD_LEFT),
    Right(AndroidKeyEvent.KEYCODE_DPAD_RIGHT),
    Home(AndroidKeyEvent.KEYCODE_MOVE_HOME),
    End(AndroidKeyEvent.KEYCODE_MOVE_END),
    Up(AndroidKeyEvent.KEYCODE_DPAD_UP),
    Down(AndroidKeyEvent.KEYCODE_DPAD_DOWN),
    Enter(AndroidKeyEvent.KEYCODE_ENTER),
    Tab(AndroidKeyEvent.KEYCODE_TAB),
    Escape(AndroidKeyEvent.KEYCODE_ESCAPE),
    Unknown(-1);

    companion object {
        fun fromAndroid(code: Int): KeyCode =
            entries.firstOrNull { it.androidCode == code } ?: Unknown
    }
}
