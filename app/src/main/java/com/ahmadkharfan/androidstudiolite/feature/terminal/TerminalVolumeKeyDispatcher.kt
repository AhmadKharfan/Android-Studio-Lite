package com.ahmadkharfan.androidstudiolite.feature.terminal

import android.view.KeyEvent

/**
 * Lets the terminal screen intercept volume keys at [android.app.Activity.dispatchKeyEvent], which is
 * more reliable than wrapping [android.view.Window.Callback] (Compose/edge-to-edge often bypass that).
 */
object TerminalVolumeKeyDispatcher {
    var handler: ((KeyEvent) -> Boolean)? = null
}
