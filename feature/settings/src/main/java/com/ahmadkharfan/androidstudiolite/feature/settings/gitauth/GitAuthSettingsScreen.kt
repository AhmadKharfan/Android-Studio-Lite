package com.ahmadkharfan.androidstudiolite.feature.settings.gitauth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.ahmadkharfan.androidstudiolite.designsystem.layout.aslImePadding
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitHubAuthDialog
import com.ahmadkharfan.androidstudiolite.feature.hub.components.HubSectionHeader
import com.ahmadkharfan.androidstudiolite.core.common.R as CommonR
import com.ahmadkharfan.androidstudiolite.feature.settings.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun GitAuthSettingsRoute(
    onBack: () -> Unit,
    viewModel: GitAuthSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    GitAuthSettingsScreen(uiState = uiState, interactionListener = viewModel, onBack = onBack)
}

@Composable
private fun GitAuthSettingsScreen(
    uiState: GitAuthSettingsUiState,
    interactionListener: GitAuthSettingsInteractionListener,
    onBack: () -> Unit,
) {
    val colors = AslTheme.colors
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AslTopAppBar(
                title = stringResource(CommonR.string.settings_git_auth),
                subtitle = stringResource(CommonR.string.settings_git_auth_sub),
                onBack = onBack,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .aslImePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                HubSectionHeader(stringResource(R.string.settings_git_account))
                GitHubAccountCard(uiState = uiState, interactionListener = interactionListener)

                Spacer(Modifier.height(20.dp))
                HubSectionHeader(stringResource(R.string.settings_git_author))
                Text(
                    text = stringResource(R.string.settings_git_author_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(8.dp))
                AslTextField(
                    value = uiState.gitAuthorName,
                    onValueChange = interactionListener::onGitAuthorNameChanged,
                    label = stringResource(R.string.settings_git_name),
                    placeholder = stringResource(R.string.settings_git_name_placeholder),
                )
                Spacer(Modifier.height(10.dp))
                AslTextField(
                    value = uiState.gitAuthorEmail,
                    onValueChange = interactionListener::onGitAuthorEmailChanged,
                    label = stringResource(R.string.settings_git_email),
                    placeholder = stringResource(R.string.settings_git_email_placeholder),
                )
                Spacer(Modifier.height(10.dp))
                AslButton(
                    label = stringResource(R.string.settings_git_save_author),
                    onClick = interactionListener::onSaveGitAuthor,
                    disabled = !uiState.gitAuthorDirty,
                )

                if (uiState.statusMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = uiState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.isError) colors.error else colors.success,
                    )
                }
            }
        }
    }

    GitHubAuthDialog(uiState.authPrompt, interactionListener)
}

@Composable
private fun GitHubAccountCard(
    uiState: GitAuthSettingsUiState,
    interactionListener: GitAuthSettingsInteractionListener,
) {
    val colors = AslTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, AslShape.lg)
            .border(1.dp, colors.borderDefault, AslShape.lg)
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(if (uiState.gitHubConnected) R.string.settings_git_connected else R.string.settings_git_not_connected),
            style = MaterialTheme.typography.titleSmall,
            color = if (uiState.gitHubConnected) colors.success else colors.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (uiState.gitHubConnected) {
                stringResource(R.string.settings_git_connected_hint)
            } else if (uiState.gitHubAvailable) {
                stringResource(R.string.settings_git_sign_in_hint)
            } else {
                stringResource(R.string.settings_git_token_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AslButton(
                label = when {
                    uiState.gitHubConnected -> stringResource(R.string.settings_git_update_credential)
                    uiState.gitHubAvailable -> stringResource(R.string.settings_git_sign_in)
                    else -> stringResource(R.string.settings_git_add_token)
                },
                icon = if (uiState.gitHubAvailable && !uiState.gitHubConnected) "github" else "key-round",
                onClick = interactionListener::onConnectGitHub,
            )
            if (uiState.gitHubConnected) {
                AslButton(
                    label = stringResource(R.string.settings_git_sign_out),
                    onClick = interactionListener::onDisconnectGitHub,
                    variant = AslButtonVariant.Secondary,
                )
            }
        }
    }
}
