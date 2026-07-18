package com.ahmadkharfan.androidstudiolite.feature.settings.editor

interface EditorSettingsInteractionListener {
    fun onFontSizeChanged(size: Int)
    fun onColorSchemeChanged(id: String)
    fun onTabSizeChanged(size: Int)
    fun onToggleAutoSave(enabled: Boolean)
}
