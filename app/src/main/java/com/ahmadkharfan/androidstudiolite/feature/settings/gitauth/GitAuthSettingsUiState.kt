package com.ahmadkharfan.androidstudiolite.feature.settings.gitauth

import androidx.compose.runtime.Immutable
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitAuthPromptState

@Immutable
data class GitAuthSettingsUiState(
    /** Whether a GitHub OAuth client id is configured (device-flow sign-in available). */
    val gitHubAvailable: Boolean = false,
    /** True when a token is stored for github.com. */
    val gitHubConnected: Boolean = false,
    val gitAuthorName: String = "",
    val gitAuthorEmail: String = "",
    val gitAuthorDirty: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    /** Drives the shared GitHub sign-in / token dialog. */
    val authPrompt: GitAuthPromptState = GitAuthPromptState(),
)
