package com.example.androidstudiolite.feature.settings.editor

interface EditorSettingsInteractionListener {
    fun onFontSizeChanged(size: Int)
    fun onColorSchemeChanged(id: String)
    fun onTabSizeChanged(size: Int)
    fun onToggleAutoSave(enabled: Boolean)
    fun onToggleKotlinLsp(enabled: Boolean)
    fun onToggleJavaLsp(enabled: Boolean)
    fun onToggleXmlLsp(enabled: Boolean)
}
