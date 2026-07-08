package com.example.androidstudiolite.feature.onboarding.permissions.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.example.androidstudiolite.core.designsystem.animation.AslStaggeredAppear
import com.example.androidstudiolite.core.designsystem.component.buttons.AslButton
import com.example.androidstudiolite.core.designsystem.component.buttons.AslButtonSize
import com.example.androidstudiolite.core.designsystem.component.ide.AslPermissionCard
import com.example.androidstudiolite.core.designsystem.component.inputs.AslWizardStepper
import com.example.androidstudiolite.core.designsystem.theme.AslTheme
import com.example.androidstudiolite.feature.onboarding.common.ONBOARDING_STEPS
import com.example.androidstudiolite.feature.onboarding.permissions.interaction.PermissionsInteraction
import com.example.androidstudiolite.feature.onboarding.permissions.uiState.PermissionsUiState
import com.example.androidstudiolite.feature.onboarding.permissions.viewModel.PermissionsViewModel

@Composable
fun PermissionsRoute(
    onContinue: () -> Unit,
    viewModel: PermissionsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PermissionsScreen(
        uiState = uiState,
        onInteraction = viewModel::onInteraction,
        onContinue = onContinue,
    )
}

@Composable
private fun PermissionsScreen(
    uiState: PermissionsUiState,
    onInteraction: (PermissionsInteraction) -> Unit,
    onContinue: () -> Unit,
) {
    val colors = AslTheme.colors
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            AslWizardStepper(steps = ONBOARDING_STEPS, current = 1, modifier = Modifier.padding(horizontal = 4.dp))
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 18.dp)) {
                Text(text = "A few permissions", style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary)
                Text(
                    text = "The IDE needs these to build and install your apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(uiState.permissions, key = { _, it -> it.id }) { index, permission ->
                    AslStaggeredAppear(index = index) {
                        AslPermissionCard(
                            title = permission.title,
                            reason = permission.reason,
                            icon = permission.icon,
                            granted = permission.granted,
                            onGrant = { onInteraction(PermissionsInteraction.GrantPermission(permission.id)) },
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(top = 14.dp)) {
                AslButton(
                    label = "Continue",
                    onClick = onContinue,
                    size = AslButtonSize.Lg,
                    fullWidth = true,
                    disabled = !uiState.canContinue,
                )
                if (!uiState.canContinue) {
                    Text(
                        text = "Grant storage and install access to continue",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
