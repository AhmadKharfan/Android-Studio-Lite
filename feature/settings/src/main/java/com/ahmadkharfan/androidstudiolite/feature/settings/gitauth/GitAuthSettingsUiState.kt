package com.ahmadkharfan.androidstudiolite.feature.settings.gitauth

import androidx.compose.runtime.Immutable
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitAuthPromptState

@Immutable
data class GitAuthSettingsUiState(
    val gitHubAvailable: Boolean = false,
    val gitHubConnected: Boolean = false,
    val gitAuthorName: String = "",
    val gitAuthorEmail: String = "",
    val gitAuthorDirty: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val authPrompt: GitAuthPromptState = GitAuthPromptState(),
)
