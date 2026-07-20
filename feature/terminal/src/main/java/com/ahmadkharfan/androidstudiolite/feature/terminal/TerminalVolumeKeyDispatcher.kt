package com.ahmadkharfan.androidstudiolite.feature.terminal

import android.view.KeyEvent

object TerminalVolumeKeyDispatcher {
    var handler: ((KeyEvent) -> Boolean)? = null
}
