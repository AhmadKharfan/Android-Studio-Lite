package com.example.androidstudiolite.feature.folderpicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.example.androidstudiolite.designsystem.component.buttons.AslButton
import com.example.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.example.androidstudiolite.designsystem.component.content.AslFileTree
import com.example.androidstudiolite.designsystem.component.navigation.AslBreadcrumbBar
import com.example.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.example.androidstudiolite.designsystem.theme.AslTheme
import com.example.androidstudiolite.feature.folderpicker.FolderPickerInteractionListener
import com.example.androidstudiolite.feature.folderpicker.FolderPickerUiState
import com.example.androidstudiolite.feature.folderpicker.FolderPickerViewModel

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
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AslTopAppBar(title = "Choose folder", onBack = onCancel)
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
                    onToggle = { interactionListener.onToggleFolder(it) },
                    onSelect = { interactionListener.onSelectFolder(it.id) },
                )
            }
            HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AslButton(
                    label = "Cancel",
                    onClick = onCancel,
                    variant = AslButtonVariant.Secondary,
                    fullWidth = true,
                    modifier = Modifier.weight(1f),
                )
                AslButton(
                    label = "Select",
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
