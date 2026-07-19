package com.ahmadkharfan.androidstudiolite.feature.hub
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import com.ahmadkharfan.androidstudiolite.R
import com.ahmadkharfan.androidstudiolite.designsystem.animation.AslStaggeredAppear
import com.ahmadkharfan.androidstudiolite.designsystem.animation.AslStateCrossfade
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslListItem
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslProjectCard
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslBanner
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslBannerTone
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDialog
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDialogVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslBottomSheet
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslBottomSheetSize
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslSkeleton
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslSkeletonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslSnackbar
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslSnackbarTone
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMotion
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.hub.components.HubSectionHeader
import com.ahmadkharfan.androidstudiolite.feature.openproject.OpenProjectRoute
import com.ahmadkharfan.androidstudiolite.feature.clonerepo.CloneRepoRoute

@Composable
fun HubRoute(
    onOpenProject: (String) -> Unit,
    onCreateProject: () -> Unit,
    onOpenPreferences: () -> Unit,
    onBrowseFolder: () -> Unit,
    pickedFolder: String?,
    onPickedFolderConsumed: () -> Unit,
    viewModel: HubViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(pickedFolder) {
        pickedFolder?.let { path ->
            viewModel.handlePickedFolder(path)
            onPickedFolderConsumed()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is HubEffect.NavigateToProject -> onOpenProject(effect.id)
                HubEffect.NavigateToCreateProject -> onCreateProject()
                HubEffect.NavigateToPreferences -> onOpenPreferences()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HubScreen(uiState = uiState, interactionListener = viewModel)

        if (uiState.dialog is HubDialogUiState.InvalidFolder) {
            AslSnackbar(
                message = stringResource(R.string.hub_invalid_project_snackbar),
                icon = "octagon-alert",
                tone = AslSnackbarTone.Error,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            )
        }
    }

    HubSheetHost(
        uiState = uiState,
        viewModel = viewModel,
        onOpenProject = onOpenProject,
        onCreateProject = onCreateProject,
        onBrowseFolder = onBrowseFolder,
    )

    HubDialogHost(uiState = uiState, viewModel = viewModel, onBrowseFolder = onBrowseFolder)

    HubProjectMenuHost(uiState = uiState, viewModel = viewModel, onOpenProject = onOpenProject)
}

@Composable
private fun HubProjectMenuHost(
    uiState: HubUiState,
    viewModel: HubViewModel,
    onOpenProject: (String) -> Unit,
) {
    val colors = AslTheme.colors
    uiState.projectMenu?.let { project ->
        AslBottomSheet(
            onDismiss = { viewModel.dismissProjectMenu() },
            title = project.name,
            size = AslBottomSheetSize.Peek,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                AslListItem(
                    title = stringResource(R.string.hub_project_menu_open),
                    icon = "folder-open",
                    onClick = { viewModel.dismissProjectMenu(); onOpenProject(project.id) },
                )
                AslListItem(
                    title = stringResource(R.string.hub_project_menu_rename),
                    icon = "pencil",
                    onClick = { viewModel.requestRenameProject() },
                )
                AslListItem(
                    title = stringResource(R.string.hub_project_menu_delete),
                    icon = "trash-2",
                    iconColor = colors.error,
                    divider = false,
                    onClick = { viewModel.requestDeleteProject() },
                )
            }
        }
    }
}

@Composable
private fun HubSheetHost(
    uiState: HubUiState,
    viewModel: HubViewModel,
    onOpenProject: (String) -> Unit,
    onCreateProject: () -> Unit,
    onBrowseFolder: () -> Unit,
) {
    when (uiState.sheet) {
        HubSheetUiState.OpenProject -> OpenProjectRoute(
            onDismiss = { viewModel.dismissSheet() },
            onProjectSelected = { id -> viewModel.dismissSheet(); onOpenProject(id) },
            onBrowseOtherLocation = { viewModel.dismissSheet(); onBrowseFolder() },
            onCreateProject = { viewModel.dismissSheet(); onCreateProject() },
            onCloneRepository = { viewModel.onCloneRepository() },
        )
        HubSheetUiState.CloneRepo -> CloneRepoRoute(
            onDismiss = { viewModel.dismissSheet() },
            onCloned = { id -> viewModel.dismissSheet(); onOpenProject(id) },
        )
        HubSheetUiState.None -> Unit
    }
}

@Composable
private fun HubDialogHost(
    uiState: HubUiState,
    viewModel: HubViewModel,
    onBrowseFolder: () -> Unit,
) {
    when (val dialog = uiState.dialog) {
        is HubDialogUiState.ResumeProject -> AslDialog(
            title = stringResource(R.string.hub_dialog_resume_title),
            body = "${dialog.projectName}\n${dialog.path}",
            confirmLabel = stringResource(R.string.action_open),
            cancelLabel = stringResource(R.string.action_not_now),
            onConfirm = { viewModel.confirmResumeDialog() },
            onDismiss = { viewModel.dismissResumeDialog() },
        )
        is HubDialogUiState.InvalidFolder -> AslDialog(
            title = stringResource(R.string.hub_dialog_invalid_folder_title),
            body = stringResource(R.string.hub_dialog_invalid_folder_body, dialog.path),
            confirmLabel = stringResource(R.string.action_choose_another),
            cancelLabel = stringResource(R.string.action_cancel),
            onConfirm = { viewModel.confirmInvalidFolderDialog(onReopenPicker = onBrowseFolder) },
            onDismiss = { viewModel.dismissInvalidFolderDialog() },
        )
        is HubDialogUiState.RenameProject -> {
            var name by remember(dialog.id) { mutableStateOf(dialog.currentName) }
            AslDialog(
                title = stringResource(R.string.hub_rename_project_title),
                onDismiss = { viewModel.dismissProjectDialog() },
                variant = AslDialogVariant.Input,
                confirmLabel = stringResource(R.string.action_rename),
                cancelLabel = stringResource(R.string.action_cancel),
                onConfirm = { viewModel.confirmRenameProject(name) },
                inputContent = {
                    AslTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.hub_rename_project_label))
                },
            )
        }
        is HubDialogUiState.DeleteProject -> AslDialog(
            title = stringResource(R.string.hub_delete_project_title, dialog.name),
            body = stringResource(R.string.hub_delete_project_body),
            variant = AslDialogVariant.Confirm,
            confirmLabel = stringResource(R.string.action_delete),
            cancelLabel = stringResource(R.string.action_cancel),
            onConfirm = { viewModel.confirmDeleteProject() },
            onDismiss = { viewModel.dismissProjectDialog() },
        )
        HubDialogUiState.None -> Unit
    }
}

private val TABLET_BREAKPOINT = 600.dp

@Composable
private fun HubScreen(
    uiState: HubUiState,
    interactionListener: HubInteractionListener,
) {
    val colors = AslTheme.colors
    Scaffold(containerColor = colors.bgBase) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val isTablet = maxWidth >= TABLET_BREAKPOINT
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
            ) {
                HubTopBar(interactionListener = interactionListener, colors = colors)
                HubGreeting(uiState = uiState, isTablet = isTablet, colors = colors)
                HubResumeBanner(uiState = uiState, interactionListener = interactionListener)
                if (isTablet) {
                    HubTabletLayout(uiState = uiState, interactionListener = interactionListener, colors = colors)
                } else {
                    HubPhoneLayout(uiState = uiState, interactionListener = interactionListener)
                }
            }
        }
    }
}

@Composable
private fun HubTopBar(
    interactionListener: HubInteractionListener,
    colors: AslColorScheme,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HubHorizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(Color(0xFF1E1E2E), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "{ }", color = Color(0xFF34D399), fontFamily = AslCode.codeBody.fontFamily, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        }
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        AslIconButton(
            icon = "settings",
            contentDescription = "Preferences",
            onClick = { interactionListener.onOpenPreferences() },
        )
    }
}

@Composable
private fun HubGreeting(
    uiState: HubUiState,
    isTablet: Boolean,
    colors: AslColorScheme,
) {
    Text(
        text = stringResource(
            R.string.greeting_user,
            uiState.greeting,
            stringResource(R.string.default_user_name),
        ),
        style = if (isTablet) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineLarge,
        color = colors.textPrimary,
        modifier = Modifier
            .padding(horizontal = HubHorizontalPadding)
            .padding(top = 6.dp, bottom = 12.dp),
    )
}

@Composable
private fun HubResumeBanner(
    uiState: HubUiState,
    interactionListener: HubInteractionListener,
) {
    // Animate the resume banner in/out; retain the last project so its name stays rendered
    // while the banner collapses away after dismissal.
    val resume = uiState.resumeProject
    var lastResume by remember { mutableStateOf(resume) }
    if (resume != null) lastResume = resume
    AnimatedVisibility(
        visible = resume != null,
        modifier = Modifier.padding(horizontal = HubHorizontalPadding),
        enter = expandVertically(AslMotion.enterSpec()) + fadeIn(AslMotion.enterSpec()),
        exit = shrinkVertically(AslMotion.exitSpec()) + fadeOut(AslMotion.exitSpec()),
    ) {
        lastResume?.let { project ->
            AslBanner(
                tone = AslBannerTone.Info,
                message = stringResource(R.string.hub_resume_banner, project.name),
                actionLabel = stringResource(R.string.action_resume),
                onAction = { interactionListener.onResumeProject() },
                onDismiss = { interactionListener.onDismissResume() },
            )
        }
    }
}

@Composable
private fun HubTabletLayout(
    uiState: HubUiState,
    interactionListener: HubInteractionListener,
    colors: AslColorScheme,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HubHorizontalPadding)
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(modifier = Modifier.weight(1.5f)) {
            RecentProjectsList(uiState = uiState, interactionListener = interactionListener)
        }
        Column(modifier = Modifier.weight(1f)) {
            StartSection(interactionListener = interactionListener)
        }
    }
}

private val HubHorizontalPadding = 20.dp

@Composable
private fun HubPhoneLayout(
    uiState: HubUiState,
    interactionListener: HubInteractionListener,
) {
    RecentProjectsRow(uiState = uiState, interactionListener = interactionListener)
    StartSection(interactionListener = interactionListener)
}

@Composable
private fun RecentProjectsRow(uiState: HubUiState, interactionListener: HubInteractionListener) {
    if (!uiState.isLoadingRecents && uiState.recentProjects.isEmpty()) return
    HubSectionHeader(
        text = stringResource(R.string.hub_section_recent),
        modifier = Modifier.padding(horizontal = HubHorizontalPadding),
    )
    AslStateCrossfade(targetState = uiState.isLoadingRecents, label = "hubRecentsRow") { loading ->
        if (loading) {
            AslSkeleton(
                variant = AslSkeletonVariant.List,
                rows = 2,
                modifier = Modifier.padding(horizontal = HubHorizontalPadding),
            )
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val cardWidth = minOf(290.dp, maxWidth * 0.82f)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = HubHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(uiState.recentProjects, key = { _, project -> project.id }) { index, project ->
                        AslStaggeredAppear(index = index) {
                            AslProjectCard(
                                name = project.name,
                                path = project.path,
                                lastOpened = project.lastOpenedText,
                                language = project.language,
                                modifier = Modifier.width(cardWidth),
                                onClick = { interactionListener.onOpenProject(project.id) },
                                onMenu = { interactionListener.onProjectMenu(project.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentProjectsList(uiState: HubUiState, interactionListener: HubInteractionListener) {
    if (!uiState.isLoadingRecents && uiState.recentProjects.isEmpty()) return
    HubSectionHeader(stringResource(R.string.hub_section_recent))
    AslStateCrossfade(targetState = uiState.isLoadingRecents, label = "hubRecentsList") { loading ->
        if (loading) {
            AslSkeleton(variant = AslSkeletonVariant.List, rows = 2)
        } else {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                uiState.recentProjects.forEachIndexed { index, project ->
                    AslStaggeredAppear(index = index) {
                        AslProjectCard(
                            name = project.name,
                            path = project.path,
                            lastOpened = project.lastOpenedText,
                            language = project.language,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { interactionListener.onOpenProject(project.id) },
                            onMenu = { interactionListener.onProjectMenu(project.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StartSection(interactionListener: HubInteractionListener) {
    val colors = AslTheme.colors
    Column(modifier = Modifier.padding(horizontal = HubHorizontalPadding)) {
        HubSectionHeader(stringResource(R.string.hub_section_start))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, AslShape.lg),
        ) {
        AslListItem(
            title = stringResource(R.string.hub_create_project),
            subtitle = stringResource(R.string.hub_create_project_sub),
            icon = "plus",
            iconColor = colors.accentPrimary,
            trailing = { AslIcon(name = "chevron-right", size = 16.dp, tint = colors.textTertiary) },
            onClick = { interactionListener.onCreateProject() },
        )
        AslListItem(
            title = stringResource(R.string.hub_open_project),
            subtitle = stringResource(R.string.hub_open_project_sub),
            icon = "folder-open",
            trailing = { AslIcon(name = "chevron-right", size = 16.dp, tint = colors.textTertiary) },
            onClick = { interactionListener.onOpenProjectPicker() },
        )
        AslListItem(
            title = stringResource(R.string.hub_clone_repo),
            subtitle = stringResource(R.string.hub_clone_repo_sub),
            icon = "git-branch",
            divider = false,
            trailing = { AslIcon(name = "chevron-right", size = 16.dp, tint = colors.textTertiary) },
            onClick = { interactionListener.onCloneRepository() },
        )
        }
    }
}
