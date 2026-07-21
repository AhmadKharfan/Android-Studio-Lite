package com.ahmadkharfan.androidstudiolite.feature.settings.aiagent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.feature.settings.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import org.koin.androidx.compose.koinViewModel
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslApiKeyCard
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslApiKeyStatus
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslDropdown
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslDropdownOption
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSwitch
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.layout.aslImePadding
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMotion
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

    Scaffold(
        containerColor = colors.bgBase,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AslTopAppBar(title = "AI Agent", onBack = onBack, applyStatusBarInset = true)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .aslImePadding(),
        ) {
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

                uiState.providers.forEach { provider ->
                    key(provider.id) {
                        AiAgentProviderSection(
                            provider = provider,
                            expanded = provider.id in expandedIds,
                            interactionListener = interactionListener,
                            colors = colors,
                            onToggleExpand = {
                                expandedIds = if (provider.id in expandedIds) {
                                    expandedIds - provider.id
                                } else {
                                    expandedIds + provider.id
                                }
                            },
                        )
                    }
                }
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
            label = stringResource(R.string.ai_agent_enable),
            checked = uiState.enabled,
            onCheckedChange = { interactionListener.onToggleEnabled(it) },
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
        AslSwitch(
            label = stringResource(R.string.ai_agent_auto_apply),
            checked = uiState.autoApply,
            onCheckedChange = { interactionListener.onToggleAutoApply(it) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AiAgentProviderSection(
    provider: AiProviderUiModel,
    expanded: Boolean,
    interactionListener: AiAgentInteractionListener,
    colors: AslColorScheme,
    onToggleExpand: () -> Unit,
) {
    val bringIntoViewRequester = remember(provider.id) { BringIntoViewRequester() }
    LaunchedEffect(expanded) {
        if (expanded) bringIntoViewRequester.bringIntoView()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester),
    ) {
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                (expandVertically(animationSpec = AslMotion.exitSpec()) + fadeIn(AslMotion.exitSpec()))
                    .togetherWith(shrinkVertically(animationSpec = AslMotion.exitSpec()) + fadeOut(AslMotion.exitSpec()))
            },
            label = "agentProviderExpand",
        ) { isExpanded ->
            if (isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AslApiKeyCard(
                        provider = provider.name,
                        providerIcon = provider.icon,
                        description = provider.description,
                        value = provider.apiKey,
                        onValueChange = { interactionListener.onApiKeyChanged(provider.id, it) },
                        status = provider.status.toAslStatus(),
                        testing = provider.status == ApiKeyStatus.TESTING,
                        errorMessage = provider.keyError,
                        onTest = { key -> interactionListener.onTestApiKey(provider.id, key) },
                        onCollapse = onToggleExpand,
                    )
                    if (provider.status == ApiKeyStatus.VALID) {
                        AiAgentModelPicker(provider = provider, interactionListener = interactionListener)
                    }
                }
            } else {
                AiAgentCollapsedProviderRow(
                    provider = provider,
                    colors = colors,
                    onExpand = onToggleExpand,
                )
            }
        }
    }
}

@Composable
private fun AiAgentCollapsedProviderRow(
    provider: AiProviderUiModel,
    colors: AslColorScheme,
    onExpand: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, AslShape.lg)
            .border(1.dp, colors.borderDefault, AslShape.lg)
            .defaultMinSize(minHeight = 60.dp)
            .clickable(onClick = onExpand)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AslIcon(name = provider.icon, size = 20.dp, tint = colors.textSecondary)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = provider.name,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = provider.description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        AslIcon(name = "chevron-right", size = 16.dp, tint = colors.textTertiary)
    }
}

@Composable
private fun AiAgentModelPicker(
    provider: AiProviderUiModel,
    interactionListener: AiAgentInteractionListener,
) {
    val colors = AslTheme.colors
    var custom by remember(provider.selectedModel) { mutableStateOf(provider.selectedModel) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, AslShape.lg)
            .border(1.dp, colors.borderDefault, AslShape.lg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.ai_chat_model),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            AslIconButton(
                icon = "refresh-cw",
                contentDescription = stringResource(R.string.ai_chat_refresh_models),
                size = 32.dp,
                iconSize = 16.dp,
                onClick = { interactionListener.onRefreshModels(provider.id) },
            )
        }
        if (provider.availableModels.isNotEmpty()) {
            AslDropdown(
                options = provider.availableModels.map { AslDropdownOption(it, it) },
                value = provider.selectedModel,
                onValueChange = { interactionListener.onModelChanged(provider.id, it) },
            )
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AslTextField(
                value = custom,
                onValueChange = { custom = it },
                placeholder = stringResource(R.string.ai_chat_custom_model),
                modifier = Modifier.weight(1f),
            )
            AslButton(
                label = stringResource(R.string.ai_chat_set_model),
                onClick = { if (custom.isNotBlank()) interactionListener.onModelChanged(provider.id, custom.trim()) },
                variant = AslButtonVariant.Secondary,
                disabled = custom.isBlank() || custom == provider.selectedModel,
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
    var draft by remember { mutableStateOf(uiState.instructions) }
    var focused by remember { mutableStateOf(false) }
    val commitChange by rememberUpdatedState(interactionListener::onInstructionsChanged)

    LaunchedEffect(uiState.instructions) {
        if (!focused) draft = uiState.instructions
    }

    DisposableEffect(Unit) {
        onDispose {
            if (focused) commitChange(draft)
        }
    }

    Column {
        Text(
            text = stringResource(R.string.ai_agent_instructions),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textSecondary),
            cursorBrush = SolidColor(colors.accentPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) {
                        scope.launch { bringIntoViewRequester.bringIntoView() }
                    } else {
                        commitChange(draft)
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
