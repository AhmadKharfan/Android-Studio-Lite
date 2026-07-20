package com.ahmadkharfan.androidstudiolite.designsystem.layout

import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier

/** Keeps content above the soft keyboard when the activity uses `adjustResize`. */
fun Modifier.aslImePadding(): Modifier = imePadding()
