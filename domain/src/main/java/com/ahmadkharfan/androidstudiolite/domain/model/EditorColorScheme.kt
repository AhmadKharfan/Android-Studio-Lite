package com.ahmadkharfan.androidstudiolite.domain.model

/**
 * Stable identifiers and defaults for editor color schemes.
 *
 * This deliberately contains no UI colors so persistence and settings may depend on it without
 * depending on the editor implementation.
 */
object EditorColorScheme {
    const val DARCULA = "darcula"
    const val LIGHT = "light"
    const val HIGH_CONTRAST = "hc"

    fun defaultId(isDarkUi: Boolean): String = if (isDarkUi) DARCULA else LIGHT

    fun isDark(id: String): Boolean = id != LIGHT
}
