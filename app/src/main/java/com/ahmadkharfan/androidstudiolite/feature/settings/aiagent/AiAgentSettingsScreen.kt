package com.ahmadkharfan.androidstudiolite.feature.settings.aiagent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import org.koin.androidx.compose.koinViewModel
import com.ahmadkharfan.androidstudiolite.designsystem.animation.AslStaggeredAppear
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslListItem
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslApiKeyCard
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslApiKeyStatus
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSwitch
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.layout.aslImePadding
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.domain.model.ApiKeyStatus
import com.ahmadkharfan.androidstudiolite.feature.settings.aiagent.AiAgentInteractionListener
import com.ahmadkharfan.androidstudiolite.feature.settings.aiagent.AiAgentUiState
import com.ahmadkharfan.androidstudiolite.feature.settings.aiagent.AiAgentViewModel
import kotlinx.coroutines.launch

@Composable
fun AiAgentSettingsRoute(
    onBack: () -> Unit,
    viewModel: AiAgentViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    AiAgentSettingsScreen(uiState = uiState, interactionListener = viewModel, onBack = onBack)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun AiAgentSettingsScreen(
    uiState: AiAgentUiState,
    interactionListener: AiAgentInteractionListener,
    onBack: () -> Unit,
) {
    val colors = AslTheme.colors
    var expandedIds by remember { mutableStateOf(setOf<String>()) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)

    LaunchedEffect(expandedIds, imeBottom) {
        if (expandedIds.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Scaffold(
        containerColor = colors.bgBase,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .aslImePadding(),
        ) {
            AslTopAppBar(title = "AI Agent", onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .imeNestedScroll()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AiAgentEnableToggle(uiState = uiState, interactionListener = interactionListener, colors = colors)

                val expanded = uiState.providers.filter { it.id in expandedIds }
                val collapsed = uiState.providers.filterNot { it.id in expandedIds }

                AiAgentExpandedProviderCards(providers = expanded, interactionListener = interactionListener)
                AiAgentCollapsedProviderList(
                    providers = collapsed,
                    colors = colors,
                    onExpand = { providerId -> expandedIds = expandedIds + providerId },
                )
                AiAgentInstructionsField(uiState = uiState, interactionListener = interactionListener, colors = colors)
            }
        }
    }
}

@Composable
private fun AiAgentEnableToggle(
    uiState: AiAgentUiState,
    interactionListener: AiAgentInteractionListener,
    colors: AslColorScheme,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, AslShape.lg)
            .border(1.dp, colors.borderDefault, AslShape.lg)
            .padding(horizontal = 16.dp),
    ) {
        AslSwitch(
            label = "Enable AI Agent",
            checked = uiState.enabled,
            onCheckedChange = { interactionListener.onToggleEnabled(it) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AiAgentExpandedProviderCards(
    providers: List<AiProviderUiModel>,
    interactionListener: AiAgentInteractionListener,
) {
    providers.forEachIndexed { index, provider ->
        AslStaggeredAppear(index = index) {
            AslApiKeyCard(
                provider = provider.name,
                providerIcon = provider.icon,
                description = provider.description,
                value = provider.apiKey,
                onValueChange = { interactionListener.onApiKeyChanged(provider.id, it) },
                status = provider.status.toAslStatus(),
                testing = provider.status == ApiKeyStatus.TESTING,
                onTest = { interactionListener.onTestApiKey(provider.id) },
            )
        }
    }
}

@Composable
private fun AiAgentCollapsedProviderList(
    providers: List<AiProviderUiModel>,
    colors: AslColorScheme,
    onExpand: (String) -> Unit,
) {
    if (providers.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, AslShape.lg)
            .border(1.dp, colors.borderDefault, AslShape.lg),
    ) {
        providers.forEachIndexed { index, provider ->
            AslListItem(
                title = provider.name,
                subtitle = provider.description,
                icon = provider.icon,
                divider = index != providers.lastIndex,
                trailing = { AslIcon(name = "chevron-right", size = 16.dp, tint = colors.textTertiary) },
                onClick = { onExpand(provider.id) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AiAgentInstructionsField(
    uiState: AiAgentUiState,
    interactionListener: AiAgentInteractionListener,
    colors: AslColorScheme,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column {
        Text(
            text = "Instructions",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        BasicTextField(
            value = uiState.instructions,
            onValueChange = { interactionListener.onInstructionsChanged(it) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textSecondary),
            cursorBrush = SolidColor(colors.accentPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged {
                    if (it.isFocused) {
                        scope.launch { bringIntoViewRequester.bringIntoView() }
                    }
                }
                .background(colors.bgElevated, AslShape.md)
                .border(1.dp, colors.borderStrong, AslShape.md)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

private fun ApiKeyStatus.toAslStatus(): AslApiKeyStatus = when (this) {
    ApiKeyStatus.VALID -> AslApiKeyStatus.Valid
    ApiKeyStatus.INVALID -> AslApiKeyStatus.Invalid
    ApiKeyStatus.EMPTY, ApiKeyStatus.TESTING -> AslApiKeyStatus.None
}
