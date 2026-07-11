package com.example.androidstudiolite.feature.editor

sealed interface EditorEffect {
    data object CloseProject : EditorEffect
    data object OpenSettings : EditorEffect
    data object OpenAiAgentSettings : EditorEffect
}
