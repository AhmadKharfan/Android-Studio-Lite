package com.ahmadkharfan.androidstudiolite.feature.openproject
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.ahmadkharfan.androidstudiolite.feature.projects.R

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
    val currentOnProjectSelected by rememberUpdatedState(onProjectSelected)

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is OpenProjectEffect.NavigateToProject -> currentOnProjectSelected(effect.id)
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
    AslBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.projects_open_title)) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            AslSearchField(
                value = uiState.query,
                onValueChange = { interactionListener.onQueryChanged(it) },
                placeholder = stringResource(R.string.projects_search_placeholder),
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
            title = stringResource(R.string.projects_empty_title),
            subtitle = stringResource(R.string.projects_empty_body),
            actionLabel = stringResource(R.string.projects_create_action),
            onAction = onCreateProject,
            secondaryLabel = stringResource(R.string.projects_clone_action),
            onSecondary = onCloneRepository,
        )
        uiState.filteredProjects.isEmpty() -> AslEmptyState(
            icon = "search-x",
            title = stringResource(R.string.projects_no_matches_title, uiState.query),
            subtitle = stringResource(R.string.projects_no_matches_body),
            secondaryLabel = stringResource(R.string.projects_browse_other),
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
        label = stringResource(R.string.projects_browse_other),
        onClick = onBrowseOtherLocation,
        variant = AslButtonVariant.Tertiary,
        icon = "folder-search",
        fullWidth = true,
        modifier = Modifier.padding(top = 8.dp),
    )
}
