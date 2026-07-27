package com.ahmadkharfan.androidstudiolite.feature.clonerepo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.ahmadkharfan.androidstudiolite.core.common.R as CommonR
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonSize
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslLinearProgress
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslStatus
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslStatusChip
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChip
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChipKind
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslBottomSheet
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.projects.R

@Composable
fun CloneRepoRoute(
    onDismiss: () -> Unit,
    onCloned: (String) -> Unit,
    viewModel: CloneRepoViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.clonedProjectId) {
        val clonedProjectId = uiState.clonedProjectId ?: return@LaunchedEffect
        onCloned(clonedProjectId)
        viewModel.onClonedProjectOpened()
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
        title = stringResource(
            if (uiState.cloning) R.string.projects_cloning_title else R.string.projects_clone_title,
        ),
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
    val chipLabel = uiState.progressMessage
        .substringBefore(' ')
        .takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.projects_cloning_detail, it) }
        ?: stringResource(R.string.projects_cloning_title)
    AslStatusChip(status = AslStatus.Syncing, label = chipLabel)
    AslLinearProgress(
        value = uiState.progressPercent.toFloat(),
        label = uiState.progressMessage.ifBlank { stringResource(R.string.projects_receiving_objects) },
        detail = "${uiState.progressPercent}%",
    )
    AslButton(
        label = stringResource(CommonR.string.action_cancel),
        onClick = onCancel,
        variant = AslButtonVariant.Secondary,
        fullWidth = true,
    )
}

@Composable
private fun CloneRepoForm(
    uiState: CloneRepoUiState,
    interactionListener: CloneRepoInteractionListener,
) {
    AslTextField(
        value = uiState.url,
        onValueChange = { interactionListener.onUrlChanged(it) },
        label = stringResource(R.string.projects_repository_url),
        placeholder = stringResource(R.string.projects_repository_url_placeholder),
        leadingIcon = "link",
        error = uiState.error,
    )
    AslTextField(
        value = uiState.branch,
        onValueChange = { interactionListener.onBranchChanged(it) },
        label = stringResource(R.string.projects_branch),
        placeholder = stringResource(R.string.projects_branch_placeholder),
        helper = stringResource(R.string.projects_branch_helper),
    )
    CloneRepoOptions(uiState = uiState, interactionListener = interactionListener)
    AslButton(
        label = stringResource(R.string.projects_clone_button),
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
        Text(
            text = stringResource(R.string.projects_options),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
