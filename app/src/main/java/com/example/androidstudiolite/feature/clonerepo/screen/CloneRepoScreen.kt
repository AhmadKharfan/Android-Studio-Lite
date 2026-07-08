package com.example.androidstudiolite.feature.clonerepo.screen

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
import com.example.androidstudiolite.core.designsystem.component.buttons.AslButton
import com.example.androidstudiolite.core.designsystem.component.buttons.AslButtonSize
import com.example.androidstudiolite.core.designsystem.component.buttons.AslButtonVariant
import com.example.androidstudiolite.core.designsystem.component.feedback.AslLinearProgress
import com.example.androidstudiolite.core.designsystem.component.feedback.AslStatus
import com.example.androidstudiolite.core.designsystem.component.feedback.AslStatusChip
import com.example.androidstudiolite.core.designsystem.component.inputs.AslChip
import com.example.androidstudiolite.core.designsystem.component.inputs.AslChipKind
import com.example.androidstudiolite.core.designsystem.component.inputs.AslTextField
import com.example.androidstudiolite.core.designsystem.component.navigation.AslBottomSheet
import com.example.androidstudiolite.core.designsystem.theme.AslTheme
import com.example.androidstudiolite.feature.clonerepo.interaction.CloneRepoInteraction
import com.example.androidstudiolite.feature.clonerepo.uiState.CloneRepoUiState
import com.example.androidstudiolite.feature.clonerepo.viewModel.CloneRepoViewModel

@Composable
fun CloneRepoRoute(
    onDismiss: () -> Unit,
    onCloned: (String) -> Unit,
    viewModel: CloneRepoViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.clonedProjectId) {
        uiState.clonedProjectId?.let(onCloned)
    }

    CloneRepoScreen(
        uiState = uiState,
        onInteraction = viewModel::onInteraction,
        onDismiss = onDismiss,
    )
}

@Composable
private fun CloneRepoScreen(
    uiState: CloneRepoUiState,
    onInteraction: (CloneRepoInteraction) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AslTheme.colors
    AslBottomSheet(
        onDismiss = onDismiss,
        title = if (uiState.cloning) "Cloning…" else "Clone repository",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (uiState.cloning) {
                AslStatusChip(status = AslStatus.Syncing, label = "Cloning · ${uiState.progressMessage}")
                AslLinearProgress(
                    value = uiState.progressPercent.toFloat(),
                    label = "Receiving objects",
                    detail = "${uiState.progressPercent}%",
                )
                AslButton(label = "Cancel", onClick = onDismiss, variant = AslButtonVariant.Secondary, fullWidth = true)
            } else {
                AslTextField(
                    value = uiState.url,
                    onValueChange = { onInteraction(CloneRepoInteraction.UrlChanged(it)) },
                    label = "Repository URL",
                    placeholder = "https://github.com/user/repo.git",
                    leadingIcon = "link",
                )
                AslTextField(
                    value = uiState.branch,
                    onValueChange = { onInteraction(CloneRepoInteraction.BranchChanged(it)) },
                    label = "Branch",
                    placeholder = "main",
                    helper = "Optional — defaults to the remote's default branch",
                )
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
                                onClick = { onInteraction(CloneRepoInteraction.ToggleOption(option.id)) },
                            )
                        }
                    }
                }
                AslButton(
                    label = "Clone",
                    onClick = { onInteraction(CloneRepoInteraction.StartClone) },
                    size = AslButtonSize.Lg,
                    fullWidth = true,
                    icon = "git-branch",
                    disabled = uiState.url.isBlank(),
                )
            }
        }
    }
}
