package com.ahmadkharfan.androidstudiolite.feature.projects.folderpicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslHorizontalDivider
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.ahmadkharfan.androidstudiolite.core.common.R as CommonR
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslFileTree
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslBreadcrumbBar
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.projects.R

@Composable
fun FolderPickerRoute(
    onCancel: () -> Unit,
    onFolderSelected: (String) -> Unit,
    viewModel: FolderPickerViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    FolderPickerScreen(
        uiState = uiState,
        interactionListener = viewModel,
        onCancel = onCancel,
        onSelect = { uiState.selectedPath?.let(onFolderSelected) },
    )
}

@Composable
private fun FolderPickerScreen(
    uiState: FolderPickerUiState,
    interactionListener: FolderPickerInteractionListener,
    onCancel: () -> Unit,
    onSelect: () -> Unit,
) {
    val colors = AslTheme.colors
    AslScaffold(containerColor = colors.bgBase) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AslTopAppBar(title = stringResource(R.string.projects_choose_folder), onBack = onCancel)
            AslBreadcrumbBar(segments = uiState.breadcrumb)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                AslFileTree(
                    items = uiState.items,
                    expandedIds = uiState.expandedIds,
                    selectedId = uiState.selectedId,
                    selectDirectories = true,
                    onToggle = { interactionListener.onToggleFolder(it) },
                    onSelect = { interactionListener.onSelectFolder(it.id) },
                )
            }
            AslHorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (uiState.creatingFolder) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.Bottom,
                    ) {
                        AslTextField(
                            value = uiState.newFolderName,
                            onValueChange = { interactionListener.onNewFolderNameChanged(it) },
                            label = stringResource(R.string.projects_folder_name),
                            placeholder = stringResource(R.string.projects_new_folder),
                            error = uiState.createFolderError,
                            modifier = Modifier.weight(1f),
                        )
                        AslButton(
                            label = stringResource(CommonR.string.action_create),
                            onClick = { interactionListener.onConfirmCreateFolder() },
                            disabled = uiState.newFolderName.isBlank(),
                        )
                    }
                    AslButton(
                        label = stringResource(CommonR.string.action_cancel),
                        onClick = { interactionListener.onCancelCreateFolder() },
                        variant = AslButtonVariant.Tertiary,
                        fullWidth = true,
                    )
                } else {
                    AslButton(
                        label = stringResource(R.string.projects_new_folder),
                        icon = "folder",
                        onClick = { interactionListener.onStartCreateFolder() },
                        variant = AslButtonVariant.Secondary,
                        fullWidth = true,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AslButton(
                        label = stringResource(CommonR.string.action_cancel),
                        onClick = onCancel,
                        variant = AslButtonVariant.Secondary,
                        fullWidth = true,
                        modifier = Modifier.weight(1f),
                    )
                    AslButton(
                        label = stringResource(R.string.projects_select),
                        onClick = onSelect,
                        variant = AslButtonVariant.Primary,
                        disabled = uiState.selectedId == null,
                        fullWidth = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
