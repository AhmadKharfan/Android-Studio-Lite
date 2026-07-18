package com.ahmadkharfan.androidstudiolite.feature.createproject
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
import com.ahmadkharfan.androidstudiolite.designsystem.animation.AslSlideContent
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonSize
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslWizardStepper
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.ahmadkharfan.androidstudiolite.designsystem.layout.aslImePadding
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.createproject.components.ConfigureStep
import com.ahmadkharfan.androidstudiolite.feature.createproject.components.SummaryStep
import com.ahmadkharfan.androidstudiolite.feature.createproject.components.TemplateStep
import com.ahmadkharfan.androidstudiolite.feature.createproject.CreateProjectInteractionListener
import com.ahmadkharfan.androidstudiolite.feature.createproject.CreateProjectUiState
import com.ahmadkharfan.androidstudiolite.feature.createproject.CreateProjectViewModel

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
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.createdProjectId) {
        uiState.createdProjectId?.let(onCreated)
    }
    LaunchedEffect(pickedFolder) {
        pickedFolder?.let {
            viewModel.onLocationChanged(it)
            onPickedFolderConsumed()
        }
    }

    CreateProjectScreen(
        uiState = uiState,
        interactionListener = viewModel,
        onBack = onBack,
        onBrowseLocation = onBrowseLocation,
    )
}

@Composable
private fun CreateProjectScreen(
    uiState: CreateProjectUiState,
    interactionListener: CreateProjectInteractionListener,
    onBack: () -> Unit,
    onBrowseLocation: () -> Unit,
) {
    val colors = AslTheme.colors
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).aslImePadding()) {
            AslTopAppBar(
                title = "Create project",
                onBack = { if (uiState.step == 0) onBack() else interactionListener.onBackStep() },
            )
            AslWizardStepper(
                steps = CREATE_PROJECT_STEPS,
                current = uiState.step,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )
            CreateProjectStepContent(
                uiState = uiState,
                interactionListener = interactionListener,
                onBrowseLocation = onBrowseLocation,
                modifier = Modifier.weight(1f).fillMaxSize().padding(horizontal = 20.dp),
            )
            CreateProjectPrimaryAction(uiState = uiState, interactionListener = interactionListener)
        }
    }
}

@Composable
private fun CreateProjectStepContent(
    uiState: CreateProjectUiState,
    interactionListener: CreateProjectInteractionListener,
    onBrowseLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
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
                    onSelect = { interactionListener.onSelectTemplate(it) },
                )
                1 -> ConfigureStep(
                    projectName = uiState.projectName,
                    packageName = uiState.packageName,
                    location = uiState.location,
                    minSdk = uiState.minSdk,
                    language = uiState.language,
                    nameError = uiState.nameError,
                    packageError = uiState.packageError,
                    onNameChanged = { interactionListener.onNameChanged(it) },
                    onPackageChanged = { interactionListener.onPackageChanged(it) },
                    onLocationChanged = { interactionListener.onLocationChanged(it) },
                    onMinSdkChanged = { interactionListener.onMinSdkChanged(it) },
                    onLanguageChanged = { interactionListener.onLanguageChanged(it) },
                    onBrowseLocation = onBrowseLocation,
                )
                else -> SummaryStep(uiState = uiState)
            }
        }
    }
}

@Composable
private fun CreateProjectPrimaryAction(
    uiState: CreateProjectUiState,
    interactionListener: CreateProjectInteractionListener,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        AslButton(
            label = if (uiState.step == 2) "Create project" else "Next",
            icon = if (uiState.step == 2) "hammer" else null,
            onClick = {
                if (uiState.step == 2) {
                    interactionListener.onCreateProject()
                } else {
                    interactionListener.onNextStep()
                }
            },
            size = AslButtonSize.Lg,
            fullWidth = true,
            disabled = !uiState.canGoNext || uiState.creating,
            loading = uiState.step == 2 && uiState.creating,
        )
    }
}
