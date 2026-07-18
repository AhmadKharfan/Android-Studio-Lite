package com.ahmadkharfan.androidstudiolite.feature.settings.server

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextFieldType
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.ahmadkharfan.androidstudiolite.designsystem.layout.aslImePadding
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.hub.components.HubSectionHeader
import org.koin.androidx.compose.koinViewModel

@Composable
fun ServerSettingsRoute(
    onBack: () -> Unit,
    viewModel: ServerSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    ServerSettingsScreen(uiState = uiState, interactionListener = viewModel, onBack = onBack)
}

@Composable
private fun ServerSettingsScreen(
    uiState: ServerSettingsUiState,
    interactionListener: ServerSettingsInteractionListener,
    onBack: () -> Unit,
) {
    val colors = AslTheme.colors
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AslTopAppBar(title = "Build server", subtitle = "Server-side builds", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .aslImePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                HubSectionHeader("Endpoint")
                AslTextField(
                    value = uiState.baseUrl,
                    onValueChange = interactionListener::onBaseUrlChanged,
                    label = "Base URL",
                    placeholder = "https://build.example.com",
                    helper = "Control-plane address. Builds POST to /v1 here.",
                    type = AslTextFieldType.Url,
                    leadingIcon = "server",
                )
                Spacer(Modifier.height(10.dp))
                AslButton(
                    label = "Save",
                    onClick = interactionListener::onSaveBaseUrl,
                    disabled = !uiState.dirty,
                )

                Spacer(Modifier.height(20.dp))
                HubSectionHeader("Device")
                DeviceCard(uiState = uiState, interactionListener = interactionListener)

                if (uiState.statusMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = uiState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.isError) colors.error else colors.success,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    uiState: ServerSettingsUiState,
    interactionListener: ServerSettingsInteractionListener,
) {
    val colors = AslTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, AslShape.lg)
            .border(1.dp, colors.borderDefault, AslShape.lg)
            .padding(16.dp),
    ) {
        Text(
            text = if (uiState.isRegistered) "Registered" else "Not registered",
            style = MaterialTheme.typography.titleSmall,
            color = if (uiState.isRegistered) colors.success else colors.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = uiState.tokenPreview?.let { "Device token: $it" }
                ?: "Register this device to mint an anonymous build token.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AslButton(
                label = if (uiState.isRegistered) "Re-register" else "Register",
                onClick = interactionListener::onRegisterDevice,
                loading = uiState.isRegistering,
            )
            if (uiState.isRegistered) {
                AslButton(
                    label = "Clear token",
                    onClick = interactionListener::onClearToken,
                    variant = AslButtonVariant.Secondary,
                    disabled = uiState.isRegistering,
                )
            }
        }
    }
}
