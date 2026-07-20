package com.ahmadkharfan.androidstudiolite.domain.model

object EditorColorScheme {
    const val DARCULA = "darcula"
    const val LIGHT = "light"
    const val HIGH_CONTRAST = "hc"

    fun defaultId(isDarkUi: Boolean): String = if (isDarkUi) DARCULA else LIGHT

    fun isDark(id: String): Boolean = id != LIGHT
}
