package com.ahmadkharfan.androidstudiolite.feature.editor

sealed interface EditorEffect {
    data object CloseProject : EditorEffect
    data object OpenSettings : EditorEffect
    data object OpenAiAgentSettings : EditorEffect
    /** Prompt for POST_NOTIFICATIONS so background build results can be posted. */
    data object RequestNotificationsPermission : EditorEffect
}
