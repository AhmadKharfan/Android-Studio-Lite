package com.ahmadkharfan.androidstudiolite.feature.openproject
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslEmptyState
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslListItem
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSearchField
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslBottomSheet
import com.ahmadkharfan.androidstudiolite.feature.openproject.OpenProjectUiState
import com.ahmadkharfan.androidstudiolite.feature.openproject.OpenProjectViewModel

@Composable
fun OpenProjectRoute(
    onDismiss: () -> Unit,
    onProjectSelected: (String) -> Unit,
    onBrowseOtherLocation: () -> Unit,
    onCreateProject: () -> Unit,
    onCloneRepository: () -> Unit,
    viewModel: OpenProjectViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is OpenProjectEffect.NavigateToProject -> onProjectSelected(effect.id)
            }
        }
    }

    OpenProjectScreen(
        uiState = uiState,
        interactionListener = viewModel,
        onDismiss = onDismiss,
        onBrowseOtherLocation = onBrowseOtherLocation,
        onCreateProject = onCreateProject,
        onCloneRepository = onCloneRepository,
    )
}

@Composable
private fun OpenProjectScreen(
    uiState: OpenProjectUiState,
    interactionListener: OpenProjectInteractionListener,
    onDismiss: () -> Unit,
    onBrowseOtherLocation: () -> Unit,
    onCreateProject: () -> Unit,
    onCloneRepository: () -> Unit,
) {
    AslBottomSheet(onDismiss = onDismiss, title = "Open project") {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            AslSearchField(
                value = uiState.query,
                onValueChange = { interactionListener.onQueryChanged(it) },
                placeholder = "Search projects",
                modifier = Modifier.fillMaxWidth(),
            )
            OpenProjectResults(
                uiState = uiState,
                interactionListener = interactionListener,
                onBrowseOtherLocation = onBrowseOtherLocation,
                onCreateProject = onCreateProject,
                onCloneRepository = onCloneRepository,
            )
        }
    }
}

@Composable
private fun OpenProjectResults(
    uiState: OpenProjectUiState,
    interactionListener: OpenProjectInteractionListener,
    onBrowseOtherLocation: () -> Unit,
    onCreateProject: () -> Unit,
    onCloneRepository: () -> Unit,
) {
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
        else -> OpenProjectList(
            uiState = uiState,
            interactionListener = interactionListener,
            onBrowseOtherLocation = onBrowseOtherLocation,
        )
    }
}

@Composable
private fun OpenProjectList(
    uiState: OpenProjectUiState,
    interactionListener: OpenProjectInteractionListener,
    onBrowseOtherLocation: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        uiState.filteredProjects.forEachIndexed { index, project ->
            AslListItem(
                title = project.name,
                subtitle = project.subtitle,
                icon = "smartphone",
                divider = index != uiState.filteredProjects.lastIndex,
                onClick = { interactionListener.onSelectProject(project.id) },
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
