package com.ahmadkharfan.androidstudiolite.feature.terminal

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
fun TerminalVolumeScrollEffect(
    enabled: Boolean,
    onVolumeKey: (volumeUp: Boolean) -> Unit,
) {
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        TerminalVolumeKeyDispatcher.handler = { event ->
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    onVolumeKey(false)
                    true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    onVolumeKey(true)
                    true
                }
                else -> false
            }
        }
        onDispose {
            if (TerminalVolumeKeyDispatcher.handler != null) {
                TerminalVolumeKeyDispatcher.handler = null
            }
        }
    }
}
