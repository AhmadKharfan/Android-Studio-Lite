package com.example.androidstudiolite.feature.settings.editor
sealed interface EditorSettingsInteraction {
    data class FontSizeChanged(val size: Int) : EditorSettingsInteraction
    data class ColorSchemeChanged(val id: String) : EditorSettingsInteraction
    data class TabSizeChanged(val size: Int) : EditorSettingsInteraction
    data class ToggleAutoSave(val enabled: Boolean) : EditorSettingsInteraction
    data class ToggleKotlinLsp(val enabled: Boolean) : EditorSettingsInteraction
    data class ToggleJavaLsp(val enabled: Boolean) : EditorSettingsInteraction
    data class ToggleXmlLsp(val enabled: Boolean) : EditorSettingsInteraction
}
