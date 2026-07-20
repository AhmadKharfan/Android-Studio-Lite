package com.ahmadkharfan.androidstudiolite.feature.clonerepo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonSize
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslLinearProgress
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslStatus
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslStatusChip
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChip
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChipKind
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextFieldType
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslBottomSheet
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.clonerepo.CloneRepoInteractionListener
import com.ahmadkharfan.androidstudiolite.feature.clonerepo.CloneRepoUiState
import com.ahmadkharfan.androidstudiolite.feature.clonerepo.CloneRepoViewModel

@Composable
fun CloneRepoRoute(
    onDismiss: () -> Unit,
    onCloned: (String) -> Unit,
    viewModel: CloneRepoViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.clonedProjectId) {
        uiState.clonedProjectId?.let(onCloned)
    }

    CloneRepoScreen(
        uiState = uiState,
        interactionListener = viewModel,
        onDismiss = onDismiss,
    )
}

@Composable
private fun CloneRepoScreen(
    uiState: CloneRepoUiState,
    interactionListener: CloneRepoInteractionListener,
    onDismiss: () -> Unit,
) {
    AslBottomSheet(
        onDismiss = onDismiss,
        title = if (uiState.cloning) "Cloning…" else "Clone repository",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (uiState.cloning) {
                CloneRepoProgress(uiState = uiState, onCancel = interactionListener::onCancelClone)
            } else {
                CloneRepoForm(uiState = uiState, interactionListener = interactionListener)
            }
        }
    }
}

@Composable
private fun CloneRepoProgress(uiState: CloneRepoUiState, onCancel: () -> Unit) {
    AslStatusChip(status = AslStatus.Syncing, label = "Cloning · ${uiState.progressMessage}")
    AslLinearProgress(
        value = uiState.progressPercent.toFloat(),
        label = "Receiving objects",
        detail = "${uiState.progressPercent}%",
    )
    AslButton(label = "Cancel", onClick = onCancel, variant = AslButtonVariant.Secondary, fullWidth = true)
}

@Composable
private fun CloneRepoForm(
    uiState: CloneRepoUiState,
    interactionListener: CloneRepoInteractionListener,
) {
    AslTextField(
        value = uiState.url,
        onValueChange = { interactionListener.onUrlChanged(it) },
        label = "Repository URL",
        placeholder = "https://github.com/user/repo.git",
        leadingIcon = "link",
    )
    AslTextField(
        value = uiState.branch,
        onValueChange = { interactionListener.onBranchChanged(it) },
        label = "Branch",
        placeholder = "main",
        helper = "Optional — defaults to the remote's default branch",
    )
    AslTextField(
        value = uiState.token,
        onValueChange = { interactionListener.onTokenChanged(it) },
        label = "Access token",
        placeholder = "ghp_…",
        helper = "Optional — required for private HTTPS repos; stored securely and reused for push/pull",
        type = AslTextFieldType.Password,
        leadingIcon = "key-round",
        error = uiState.error,
    )
    CloneRepoOptions(uiState = uiState, interactionListener = interactionListener)
    AslButton(
        label = "Clone",
        onClick = { interactionListener.onStartClone() },
        size = AslButtonSize.Lg,
        fullWidth = true,
        icon = "git-branch",
        disabled = uiState.url.isBlank(),
    )
}

@Composable
private fun CloneRepoOptions(
    uiState: CloneRepoUiState,
    interactionListener: CloneRepoInteractionListener,
) {
    val colors = AslTheme.colors
    Column {
        Text(text = "Options", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            uiState.options.forEach { option ->
                AslChip(
                    label = option.label,
                    kind = AslChipKind.Filter,
                    selected = option.selected,
                    onClick = { interactionListener.onToggleOption(option.id) },
                )
            }
        }
    }
}
