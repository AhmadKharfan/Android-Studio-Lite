package com.example.androidstudiolite.feature.settings.aiagent.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.androidstudiolite.core.designsystem.component.content.AslListItem
import com.example.androidstudiolite.core.designsystem.component.ide.AslApiKeyCard
import com.example.androidstudiolite.core.designsystem.component.ide.AslApiKeyStatus
import com.example.androidstudiolite.core.designsystem.component.inputs.AslSwitch
import com.example.androidstudiolite.core.designsystem.component.navigation.AslTopAppBar
import com.example.androidstudiolite.core.designsystem.icon.AslIcon
import com.example.androidstudiolite.core.designsystem.theme.AslShape
import com.example.androidstudiolite.core.designsystem.theme.AslTheme
import com.example.androidstudiolite.domain.model.ApiKeyStatus
import com.example.androidstudiolite.feature.settings.aiagent.interaction.AiAgentInteraction
import com.example.androidstudiolite.feature.settings.aiagent.uiState.AiAgentUiState
import com.example.androidstudiolite.feature.settings.aiagent.viewModel.AiAgentViewModel

@Composable
fun AiAgentSettingsRoute(
    onBack: () -> Unit,
    viewModel: AiAgentViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AiAgentSettingsScreen(uiState = uiState, onInteraction = viewModel::onInteraction, onBack = onBack)
}

@Composable
private fun AiAgentSettingsScreen(
    uiState: AiAgentUiState,
    onInteraction: (AiAgentInteraction) -> Unit,
    onBack: () -> Unit,
) {
    val colors = AslTheme.colors
    var expandedIds by remember { mutableStateOf(setOf<String>()) }

    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AslTopAppBar(title = "AI Agent", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        onCheckedChange = { onInteraction(AiAgentInteraction.ToggleEnabled(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                val expanded = uiState.providers.filter { it.featured || it.id in expandedIds }
                val collapsed = uiState.providers.filterNot { it.featured || it.id in expandedIds }

                expanded.forEach { provider ->
                    AslApiKeyCard(
                        provider = provider.name,
                        providerIcon = provider.icon,
                        description = provider.description,
                        value = provider.apiKey,
                        onValueChange = { onInteraction(AiAgentInteraction.ApiKeyChanged(provider.id, it)) },
                        status = provider.status.toAslStatus(),
                        testing = provider.status == ApiKeyStatus.TESTING,
                        onTest = { onInteraction(AiAgentInteraction.TestApiKey(provider.id)) },
                    )
                }

                if (collapsed.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface, AslShape.lg)
                            .border(1.dp, colors.borderDefault, AslShape.lg),
                    ) {
                        collapsed.forEachIndexed { index, provider ->
                            AslListItem(
                                title = provider.name,
                                subtitle = provider.description,
                                icon = provider.icon,
                                divider = index != collapsed.lastIndex,
                                trailing = { AslIcon(name = "chevron-right", size = 16.dp, tint = colors.textTertiary) },
                                onClick = { expandedIds = expandedIds + provider.id },
                            )
                        }
                    }
                }

                Column {
                    Text(
                        text = "Instructions",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    BasicTextField(
                        value = uiState.instructions,
                        onValueChange = { onInteraction(AiAgentInteraction.InstructionsChanged(it)) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textSecondary),
                        cursorBrush = SolidColor(colors.accentPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.bgElevated, AslShape.md)
                            .border(1.dp, colors.borderStrong, AslShape.md)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

private fun ApiKeyStatus.toAslStatus(): AslApiKeyStatus = when (this) {
    ApiKeyStatus.VALID -> AslApiKeyStatus.Valid
    ApiKeyStatus.INVALID -> AslApiKeyStatus.Invalid
    ApiKeyStatus.EMPTY, ApiKeyStatus.TESTING -> AslApiKeyStatus.None
}
