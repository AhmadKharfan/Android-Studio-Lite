package com.example.androidstudiolite.feature.createproject.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.example.androidstudiolite.core.designsystem.animation.AslSlideContent
import com.example.androidstudiolite.core.designsystem.component.buttons.AslButton
import com.example.androidstudiolite.core.designsystem.component.buttons.AslButtonSize
import com.example.androidstudiolite.core.designsystem.component.inputs.AslWizardStepper
import com.example.androidstudiolite.core.designsystem.component.navigation.AslTopAppBar
import com.example.androidstudiolite.core.designsystem.theme.AslTheme
import com.example.androidstudiolite.feature.createproject.components.ConfigureStep
import com.example.androidstudiolite.feature.createproject.components.SummaryStep
import com.example.androidstudiolite.feature.createproject.components.TemplateStep
import com.example.androidstudiolite.feature.createproject.interaction.CreateProjectInteraction
import com.example.androidstudiolite.feature.createproject.uiState.CreateProjectUiState
import com.example.androidstudiolite.feature.createproject.viewModel.CreateProjectViewModel

private val CREATE_PROJECT_STEPS = listOf("Template", "Configure", "Create")

@Composable
fun CreateProjectRoute(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    onBrowseLocation: () -> Unit,
    pickedFolder: String?,
    onPickedFolderConsumed: () -> Unit,
    viewModel: CreateProjectViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.createdProjectId) {
        uiState.createdProjectId?.let(onCreated)
    }
    LaunchedEffect(pickedFolder) {
        pickedFolder?.let {
            viewModel.onInteraction(CreateProjectInteraction.LocationChanged(it))
            onPickedFolderConsumed()
        }
    }

    CreateProjectScreen(
        uiState = uiState,
        onInteraction = viewModel::onInteraction,
        onBack = onBack,
        onBrowseLocation = onBrowseLocation,
    )
}

@Composable
private fun CreateProjectScreen(
    uiState: CreateProjectUiState,
    onInteraction: (CreateProjectInteraction) -> Unit,
    onBack: () -> Unit,
    onBrowseLocation: () -> Unit,
) {
    val colors = AslTheme.colors
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AslTopAppBar(
                title = "Create project",
                onBack = { if (uiState.step == 0) onBack() else onInteraction(CreateProjectInteraction.BackStep) },
            )
            AslWizardStepper(
                steps = CREATE_PROJECT_STEPS,
                current = uiState.step,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
            ) {
                // AnimatedContent needs bounded (non-scrolling) constraints to size/slide its two panes —
                // each step scrolls internally instead of the wrapper scrolling around the animation.
                AslSlideContent(
                    modifier = Modifier.fillMaxSize(),
                    targetState = uiState.step,
                    isForward = { initial, target -> target >= initial },
                    label = "createProjectStep",
                ) { step ->
                    when (step) {
                        0 -> TemplateStep(
                            templates = uiState.templates,
                            selectedId = uiState.selectedTemplateId,
                            onSelect = { onInteraction(CreateProjectInteraction.SelectTemplate(it)) },
                        )
                        1 -> ConfigureStep(
                            projectName = uiState.projectName,
                            packageName = uiState.packageName,
                            location = uiState.location,
                            minSdk = uiState.minSdk,
                            nameError = uiState.nameError,
                            onNameChanged = { onInteraction(CreateProjectInteraction.NameChanged(it)) },
                            onPackageChanged = { onInteraction(CreateProjectInteraction.PackageChanged(it)) },
                            onLocationChanged = { onInteraction(CreateProjectInteraction.LocationChanged(it)) },
                            onMinSdkChanged = { onInteraction(CreateProjectInteraction.MinSdkChanged(it)) },
                            onBrowseLocation = onBrowseLocation,
                        )
                        else -> SummaryStep(uiState = uiState)
                    }
                }
            }
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                AslButton(
                    label = if (uiState.step == 2) "Create project" else "Next",
                    icon = if (uiState.step == 2) "hammer" else null,
                    onClick = {
                        if (uiState.step == 2) {
                            onInteraction(CreateProjectInteraction.CreateProject)
                        } else {
                            onInteraction(CreateProjectInteraction.NextStep)
                        }
                    },
                    size = AslButtonSize.Lg,
                    fullWidth = true,
                    disabled = !uiState.canGoNext || uiState.creating,
                    loading = uiState.step == 2 && uiState.creating,
                )
            }
        }
    }
}
