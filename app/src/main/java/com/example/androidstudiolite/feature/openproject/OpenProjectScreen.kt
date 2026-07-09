package com.example.androidstudiolite.feature.openproject
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.example.androidstudiolite.designsystem.component.buttons.AslButton
import com.example.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.example.androidstudiolite.designsystem.component.content.AslEmptyState
import com.example.androidstudiolite.designsystem.component.content.AslListItem
import com.example.androidstudiolite.designsystem.component.inputs.AslSearchField
import com.example.androidstudiolite.designsystem.component.navigation.AslBottomSheet
import com.example.androidstudiolite.feature.openproject.OpenProjectInteraction
import com.example.androidstudiolite.feature.openproject.OpenProjectUiState
import com.example.androidstudiolite.feature.openproject.OpenProjectViewModel

@Composable
fun OpenProjectRoute(
    onDismiss: () -> Unit,
    onProjectSelected: (String) -> Unit,
    onBrowseOtherLocation: () -> Unit,
    onCreateProject: () -> Unit,
    onCloneRepository: () -> Unit,
    viewModel: OpenProjectViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    OpenProjectScreen(
        uiState = uiState,
        onInteraction = { interaction ->
            viewModel.onInteraction(interaction)
            if (interaction is OpenProjectInteraction.SelectProject) onProjectSelected(interaction.id)
        },
        onDismiss = onDismiss,
        onBrowseOtherLocation = onBrowseOtherLocation,
        onCreateProject = onCreateProject,
        onCloneRepository = onCloneRepository,
    )
}

@Composable
private fun OpenProjectScreen(
    uiState: OpenProjectUiState,
    onInteraction: (OpenProjectInteraction) -> Unit,
    onDismiss: () -> Unit,
    onBrowseOtherLocation: () -> Unit,
    onCreateProject: () -> Unit,
    onCloneRepository: () -> Unit,
) {
    AslBottomSheet(onDismiss = onDismiss, title = "Open project") {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            AslSearchField(
                value = uiState.query,
                onValueChange = { onInteraction(OpenProjectInteraction.QueryChanged(it)) },
                placeholder = "Search projects",
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                uiState.allProjects.isEmpty() -> AslEmptyState(
                    title = "No projects yet",
                    subtitle = "Create a new project or clone one from Git to get started.",
                    actionLabel = "Create project",
                    onAction = onCreateProject,
                    secondaryLabel = "Clone repo",
                    onSecondary = onCloneRepository,
                )
                uiState.filteredProjects.isEmpty() -> AslEmptyState(
                    icon = "search-x",
                    title = "No matches for “${uiState.query}”",
                    subtitle = "Check the spelling or browse another folder.",
                    secondaryLabel = "Browse other location",
                    onSecondary = onBrowseOtherLocation,
                )
                else -> {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        uiState.filteredProjects.forEachIndexed { index, project ->
                            AslListItem(
                                title = project.name,
                                subtitle = project.subtitle,
                                icon = "smartphone",
                                divider = index != uiState.filteredProjects.lastIndex,
                                onClick = { onInteraction(OpenProjectInteraction.SelectProject(project.id)) },
                            )
                        }
                    }
                    AslButton(
                        label = "Browse other location",
                        onClick = onBrowseOtherLocation,
                        variant = AslButtonVariant.Tertiary,
                        icon = "folder-search",
                        fullWidth = true,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
