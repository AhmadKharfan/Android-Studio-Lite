package com.ahmadkharfan.androidstudiolite.feature.editor.view

import android.view.KeyEvent

object EditorVolumeKeyDispatcher {
    var handler: ((KeyEvent) -> Boolean)? = null
}
