package com.ahmadkharfan.androidstudiolite.feature.terminal

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

@Composable
fun TerminalVolumeScrollEffect(
    enabled: Boolean,
    onVolumeKey: (volumeUp: Boolean) -> Unit,
) {
    val currentOnVolumeKey by rememberUpdatedState(onVolumeKey)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        TerminalVolumeKeyDispatcher.handler = { event ->
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    currentOnVolumeKey(false)
                    true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    currentOnVolumeKey(true)
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
