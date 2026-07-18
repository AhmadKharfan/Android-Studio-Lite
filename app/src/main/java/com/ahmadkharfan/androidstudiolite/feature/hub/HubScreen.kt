package com.ahmadkharfan.androidstudiolite.feature.hub
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
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
import com.ahmadkharfan.androidstudiolite.feature.hub.components.HubSectionTile
import com.ahmadkharfan.androidstudiolite.feature.openproject.OpenProjectRoute
import com.ahmadkharfan.androidstudiolite.feature.clonerepo.CloneRepoRoute

@Composable
fun HubRoute(
    onOpenProject: (String) -> Unit,
    onCreateProject: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenIdeConfig: () -> Unit,
    onOpenDocs: () -> Unit,
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
                HubEffect.NavigateToTerminal -> onOpenTerminal()
                HubEffect.NavigateToIdeConfig -> onOpenIdeConfig()
                HubEffect.NavigateToDocs -> onOpenDocs()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HubScreen(uiState = uiState, interactionListener = viewModel)

        if (uiState.dialog is HubDialogUiState.InvalidFolder) {
            AslSnackbar(
                message = "Not a valid Android project",
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
                    title = "Open",
                    icon = "folder-open",
                    onClick = { viewModel.dismissProjectMenu(); onOpenProject(project.id) },
                )
                AslListItem(
                    title = "Rename",
                    icon = "pencil",
                    onClick = { viewModel.requestRenameProject() },
                )
                AslListItem(
                    title = "Delete from device",
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
            title = "Open last project?",
            body = "${dialog.projectName}\n${dialog.path}",
            confirmLabel = "Open",
            cancelLabel = "Not now",
            onConfirm = { viewModel.confirmResumeDialog() },
            onDismiss = { viewModel.dismissResumeDialog() },
        )
        is HubDialogUiState.InvalidFolder -> AslDialog(
            title = "Can't open folder",
            body = "${dialog.path} has no settings.gradle — it isn't a valid Android project.",
            confirmLabel = "Choose another",
            cancelLabel = "Cancel",
            onConfirm = { viewModel.confirmInvalidFolderDialog(onReopenPicker = onBrowseFolder) },
            onDismiss = { viewModel.dismissInvalidFolderDialog() },
        )
        is HubDialogUiState.RenameProject -> {
            var name by remember(dialog.id) { mutableStateOf(dialog.currentName) }
            AslDialog(
                title = "Rename project",
                onDismiss = { viewModel.dismissProjectDialog() },
                variant = AslDialogVariant.Input,
                confirmLabel = "Rename",
                cancelLabel = "Cancel",
                onConfirm = { viewModel.confirmRenameProject(name) },
                inputContent = {
                    AslTextField(value = name, onValueChange = { name = it }, label = "Project name")
                },
            )
        }
        is HubDialogUiState.DeleteProject -> AslDialog(
            title = "Delete ${dialog.name}?",
            body = "This permanently deletes the project folder and all its files from this device. This can't be undone.",
            variant = AslDialogVariant.Confirm,
            confirmLabel = "Delete",
            cancelLabel = "Cancel",
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
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                HubTopBar(interactionListener = interactionListener, isTablet = isTablet, colors = colors)
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
    isTablet: Boolean,
    colors: AslColorScheme,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
            text = "Android Studio Lite",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (isTablet) {
            AslIconButton(icon = "terminal", contentDescription = "Terminal", onClick = { interactionListener.onOpenTerminal() })
        }
        AslIconButton(icon = "settings", contentDescription = "Preferences", onClick = { interactionListener.onOpenPreferences() })
    }
}

@Composable
private fun HubGreeting(
    uiState: HubUiState,
    isTablet: Boolean,
    colors: AslColorScheme,
) {
    Text(
        text = "${uiState.greeting}, ${uiState.userName}",
        style = if (isTablet) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineLarge,
        color = colors.textPrimary,
        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
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
        enter = expandVertically(AslMotion.enterSpec()) + fadeIn(AslMotion.enterSpec()),
        exit = shrinkVertically(AslMotion.exitSpec()) + fadeOut(AslMotion.exitSpec()),
    ) {
        lastResume?.let { project ->
            AslBanner(
                tone = AslBannerTone.Info,
                message = "Resume ${project.name} where you left off?",
                actionLabel = "Resume",
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
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(modifier = Modifier.weight(1.5f)) {
            RecentProjectsList(uiState = uiState, interactionListener = interactionListener)
        }
        Column(modifier = Modifier.weight(1f)) {
            StartSection(interactionListener = interactionListener)
            HubMoreSectionTablet(interactionListener = interactionListener, colors = colors)
        }
    }
}

@Composable
private fun HubMoreSectionTablet(
    interactionListener: HubInteractionListener,
    colors: AslColorScheme,
) {
    HubSectionHeader("More")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, AslShape.lg),
    ) {
        AslListItem(
            title = "IDE configurations",
            icon = "wrench",
            trailing = { AslIcon(name = "chevron-right", size = 16.dp, tint = colors.textTertiary) },
            onClick = { interactionListener.onOpenIdeConfig() },
        )
        AslListItem(
            title = "Preferences",
            icon = "sliders-horizontal",
            trailing = { AslIcon(name = "chevron-right", size = 16.dp, tint = colors.textTertiary) },
            onClick = { interactionListener.onOpenPreferences() },
        )
        AslListItem(
            title = "Documentation",
            icon = "book-open",
            divider = false,
            trailing = { AslIcon(name = "chevron-right", size = 16.dp, tint = colors.textTertiary) },
            onClick = { interactionListener.onOpenDocs() },
        )
    }
}

@Composable
private fun HubPhoneLayout(
    uiState: HubUiState,
    interactionListener: HubInteractionListener,
) {
    RecentProjectsRow(uiState = uiState, interactionListener = interactionListener)
    StartSection(interactionListener = interactionListener)
    HubSectionHeader("More")
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp),
    ) {
        item { HubSectionTile(icon = "terminal", label = "Terminal", onClick = { interactionListener.onOpenTerminal() }) }
        item { HubSectionTile(icon = "wrench", label = "IDE config", onClick = { interactionListener.onOpenIdeConfig() }) }
        item { HubSectionTile(icon = "sliders-horizontal", label = "Preferences", onClick = { interactionListener.onOpenPreferences() }) }
        item { HubSectionTile(icon = "book-open", label = "Docs", onClick = { interactionListener.onOpenDocs() }) }
    }
}

@Composable
private fun RecentProjectsRow(uiState: HubUiState, interactionListener: HubInteractionListener) {
    if (!uiState.isLoadingRecents && uiState.recentProjects.isEmpty()) return
    HubSectionHeader("Recent projects")
    AslStateCrossfade(targetState = uiState.isLoadingRecents, label = "hubRecentsRow") { loading ->
        if (loading) {
            AslSkeleton(variant = AslSkeletonVariant.List, rows = 2)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                uiState.recentProjects.forEachIndexed { index, project ->
                    AslStaggeredAppear(index = index) {
                        AslProjectCard(
                            name = project.name,
                            path = project.path,
                            lastOpened = project.lastOpenedText,
                            language = project.language,
                            modifier = Modifier.width(290.dp),
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
private fun RecentProjectsList(uiState: HubUiState, interactionListener: HubInteractionListener) {
    if (!uiState.isLoadingRecents && uiState.recentProjects.isEmpty()) return
    HubSectionHeader("Recent projects")
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
    HubSectionHeader("Start")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, AslShape.lg),
    ) {
        AslListItem(
            title = "Create project",
            subtitle = "From a template",
            icon = "plus",
            iconColor = colors.accentPrimary,
            trailing = { AslIcon(name = "chevron-right", size = 16.dp, tint = colors.textTertiary) },
            onClick = { interactionListener.onCreateProject() },
        )
        AslListItem(
            title = "Open project",
            subtitle = "Browse device storage",
            icon = "folder-open",
            trailing = { AslIcon(name = "chevron-right", size = 16.dp, tint = colors.textTertiary) },
            onClick = { interactionListener.onOpenProjectPicker() },
        )
        AslListItem(
            title = "Clone repository",
            subtitle = "GitHub, GitLab or any URL",
            icon = "git-branch",
            divider = false,
            trailing = { AslIcon(name = "chevron-right", size = 16.dp, tint = colors.textTertiary) },
            onClick = { interactionListener.onCloneRepository() },
        )
    }
}
