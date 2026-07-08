package com.example.androidstudiolite.feature.hub.screen

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.androidstudiolite.core.designsystem.component.buttons.AslIconButton
import com.example.androidstudiolite.core.designsystem.component.content.AslListItem
import com.example.androidstudiolite.core.designsystem.component.content.AslProjectCard
import com.example.androidstudiolite.core.designsystem.component.feedback.AslBanner
import com.example.androidstudiolite.core.designsystem.component.feedback.AslBannerTone
import com.example.androidstudiolite.core.designsystem.component.feedback.AslDialog
import com.example.androidstudiolite.core.designsystem.component.feedback.AslSkeleton
import com.example.androidstudiolite.core.designsystem.component.feedback.AslSkeletonVariant
import com.example.androidstudiolite.core.designsystem.component.feedback.AslSnackbar
import com.example.androidstudiolite.core.designsystem.component.feedback.AslSnackbarTone
import com.example.androidstudiolite.core.designsystem.icon.AslIcon
import com.example.androidstudiolite.core.designsystem.theme.AslCode
import com.example.androidstudiolite.core.designsystem.theme.AslShape
import com.example.androidstudiolite.core.designsystem.theme.AslTheme
import com.example.androidstudiolite.feature.hub.components.HubSectionHeader
import com.example.androidstudiolite.feature.hub.components.HubSectionTile
import com.example.androidstudiolite.feature.hub.interaction.HubInteraction
import com.example.androidstudiolite.feature.hub.uiState.HubDialogUiState
import com.example.androidstudiolite.feature.hub.uiState.HubProjectUiModel
import com.example.androidstudiolite.feature.hub.uiState.HubUiState
import com.example.androidstudiolite.feature.hub.viewModel.HubViewModel
import com.example.androidstudiolite.feature.openproject.screen.OpenProjectRoute
import com.example.androidstudiolite.feature.clonerepo.screen.CloneRepoRoute

private enum class HubSheet { OpenProject, CloneRepo }

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
    viewModel: HubViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var activeSheet by remember { mutableStateOf<HubSheet?>(null) }

    LaunchedEffect(pickedFolder) {
        pickedFolder?.let { path ->
            viewModel.handlePickedFolder(path, onOpenProject)
            onPickedFolderConsumed()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HubScreen(
            uiState = uiState,
            onInteraction = { interaction ->
                when (interaction) {
                    is HubInteraction.OpenProject -> onOpenProject(interaction.id)
                    is HubInteraction.ProjectMenu -> Unit
                    HubInteraction.ResumeProject -> uiState.resumeProject?.let { onOpenProject(it.id) }
                    HubInteraction.DismissResume -> viewModel.dismissResume()
                    HubInteraction.CreateProject -> onCreateProject()
                    HubInteraction.OpenProjectPicker -> activeSheet = HubSheet.OpenProject
                    HubInteraction.CloneRepository -> activeSheet = HubSheet.CloneRepo
                    HubInteraction.OpenPreferences -> onOpenPreferences()
                    HubInteraction.OpenTerminal -> onOpenTerminal()
                    HubInteraction.OpenIdeConfig -> onOpenIdeConfig()
                    HubInteraction.OpenDocs -> onOpenDocs()
                }
            },
        )

        if (uiState.dialog is HubDialogUiState.InvalidFolder) {
            AslSnackbar(
                message = "Not a valid Android project",
                icon = "octagon-alert",
                tone = AslSnackbarTone.Error,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            )
        }
    }

    when (activeSheet) {
        HubSheet.OpenProject -> OpenProjectRoute(
            onDismiss = { activeSheet = null },
            onProjectSelected = { id -> activeSheet = null; onOpenProject(id) },
            onBrowseOtherLocation = { activeSheet = null; onBrowseFolder() },
            onCreateProject = { activeSheet = null; onCreateProject() },
            onCloneRepository = { activeSheet = HubSheet.CloneRepo },
        )
        HubSheet.CloneRepo -> CloneRepoRoute(
            onDismiss = { activeSheet = null },
            onCloned = { id -> activeSheet = null; onOpenProject(id) },
        )
        null -> Unit
    }

    when (val dialog = uiState.dialog) {
        is HubDialogUiState.ResumeProject -> AslDialog(
            title = "Open last project?",
            body = "${dialog.projectName}\n${dialog.path}",
            confirmLabel = "Open",
            cancelLabel = "Not now",
            onConfirm = { viewModel.confirmResumeDialog(onOpenProject) },
            onDismiss = { viewModel.dismissResumeDialog() },
        )
        is HubDialogUiState.UpdateAvailable -> AslDialog(
            title = "Update available — v${dialog.toVersion}",
            body = "${dialog.notes}\nv${dialog.fromVersion} → v${dialog.toVersion} · ${dialog.sizeMb} MB",
            confirmLabel = "Download",
            cancelLabel = "Later",
            onConfirm = { viewModel.confirmUpdateDialog() },
            onDismiss = { viewModel.dismissUpdateDialog() },
        )
        is HubDialogUiState.InvalidFolder -> AslDialog(
            title = "Can't open folder",
            body = "${dialog.path} has no settings.gradle — it isn't a valid Android project.",
            confirmLabel = "Choose another",
            cancelLabel = "Cancel",
            onConfirm = { viewModel.confirmInvalidFolderDialog(onReopenPicker = onBrowseFolder) },
            onDismiss = { viewModel.dismissInvalidFolderDialog() },
        )
        HubDialogUiState.None -> Unit
    }
}

private val TABLET_BREAKPOINT = 600.dp

@Composable
private fun HubScreen(
    uiState: HubUiState,
    onInteraction: (HubInteraction) -> Unit,
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
                        AslIconButton(icon = "terminal", contentDescription = "Terminal", onClick = { onInteraction(HubInteraction.OpenTerminal) })
                    }
                    AslIconButton(icon = "settings", contentDescription = "Preferences", onClick = { onInteraction(HubInteraction.OpenPreferences) })
                }
                Text(
                    text = "${uiState.greeting}, ${uiState.userName}",
                    style = if (isTablet) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                )
                if (uiState.resumeProject != null) {
                    AslBanner(
                        tone = AslBannerTone.Info,
                        message = "Resume ${uiState.resumeProject.name} where you left off?",
                        actionLabel = "Resume",
                        onAction = { onInteraction(HubInteraction.ResumeProject) },
                        onDismiss = { onInteraction(HubInteraction.DismissResume) },
                    )
                }
                if (isTablet) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        Column(modifier = Modifier.weight(1.5f)) {
                            RecentProjectsList(uiState = uiState, onInteraction = onInteraction)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            StartSection(onInteraction = onInteraction)
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
                                    onClick = { onInteraction(HubInteraction.OpenIdeConfig) },
                                )
                                AslListItem(
                                    title = "Preferences",
                                    icon = "sliders-horizontal",
                                    trailing = { AslIcon(name = "chevron-right", size = 16.dp, tint = colors.textTertiary) },
                                    onClick = { onInteraction(HubInteraction.OpenPreferences) },
                                )
                                AslListItem(
                                    title = "Documentation",
                                    icon = "book-open",
                                    divider = false,
                                    trailing = { AslIcon(name = "chevron-right", size = 16.dp, tint = colors.textTertiary) },
                                    onClick = { onInteraction(HubInteraction.OpenDocs) },
                                )
                            }
                        }
                    }
                } else {
                    RecentProjectsRow(uiState = uiState, onInteraction = onInteraction)
                    StartSection(onInteraction = onInteraction)
                    HubSectionHeader("More")
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp),
                    ) {
                        item { HubSectionTile(icon = "terminal", label = "Terminal", onClick = { onInteraction(HubInteraction.OpenTerminal) }) }
                        item { HubSectionTile(icon = "wrench", label = "IDE config", onClick = { onInteraction(HubInteraction.OpenIdeConfig) }) }
                        item { HubSectionTile(icon = "sliders-horizontal", label = "Preferences", onClick = { onInteraction(HubInteraction.OpenPreferences) }) }
                        item { HubSectionTile(icon = "book-open", label = "Docs", onClick = { onInteraction(HubInteraction.OpenDocs) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentProjectsRow(uiState: HubUiState, onInteraction: (HubInteraction) -> Unit) {
    if (uiState.isLoadingRecents) {
        HubSectionHeader("Recent projects")
        AslSkeleton(variant = AslSkeletonVariant.List, rows = 2)
        return
    }
    if (uiState.recentProjects.isEmpty()) return
    HubSectionHeader("Recent projects")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        uiState.recentProjects.forEach { project ->
            AslProjectCard(
                name = project.name,
                path = project.path,
                lastOpened = project.lastOpenedText,
                language = project.language,
                modifier = Modifier.width(290.dp),
                onClick = { onInteraction(HubInteraction.OpenProject(project.id)) },
                onMenu = { onInteraction(HubInteraction.ProjectMenu(project.id)) },
            )
        }
    }
}

@Composable
private fun RecentProjectsList(uiState: HubUiState, onInteraction: (HubInteraction) -> Unit) {
    if (uiState.isLoadingRecents) {
        HubSectionHeader("Recent projects")
        AslSkeleton(variant = AslSkeletonVariant.List, rows = 2)
        return
    }
    if (uiState.recentProjects.isEmpty()) return
    HubSectionHeader("Recent projects")
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        uiState.recentProjects.forEach { project ->
            AslProjectCard(
                name = project.name,
                path = project.path,
                lastOpened = project.lastOpenedText,
                language = project.language,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onInteraction(HubInteraction.OpenProject(project.id)) },
                onMenu = { onInteraction(HubInteraction.ProjectMenu(project.id)) },
            )
        }
    }
}

@Composable
private fun StartSection(onInteraction: (HubInteraction) -> Unit) {
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
            onClick = { onInteraction(HubInteraction.CreateProject) },
        )
        AslListItem(
            title = "Open project",
            subtitle = "Browse device storage",
            icon = "folder-open",
            trailing = { AslIcon(name = "chevron-right", size = 16.dp, tint = colors.textTertiary) },
            onClick = { onInteraction(HubInteraction.OpenProjectPicker) },
        )
        AslListItem(
            title = "Clone repository",
            subtitle = "GitHub, GitLab or any URL",
            icon = "git-branch",
            divider = false,
            trailing = { AslIcon(name = "chevron-right", size = 16.dp, tint = colors.textTertiary) },
            onClick = { onInteraction(HubInteraction.CloneRepository) },
        )
    }
}
