package com.example.androidstudiolite.feature.onboarding.permissions
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.example.androidstudiolite.designsystem.animation.AslStaggeredAppear
import com.example.androidstudiolite.designsystem.component.buttons.AslButton
import com.example.androidstudiolite.designsystem.component.buttons.AslButtonSize
import com.example.androidstudiolite.designsystem.component.ide.AslPermissionCard
import com.example.androidstudiolite.designsystem.component.inputs.AslWizardStepper
import com.example.androidstudiolite.designsystem.theme.AslColorScheme
import com.example.androidstudiolite.designsystem.theme.AslTheme
import com.example.androidstudiolite.feature.onboarding.common.ONBOARDING_STEPS
import com.example.androidstudiolite.feature.onboarding.permissions.PermissionsInteractionListener
import com.example.androidstudiolite.feature.onboarding.permissions.PermissionsUiState
import com.example.androidstudiolite.feature.onboarding.permissions.PermissionsViewModel

@Composable
fun PermissionsRoute(
    onContinue: () -> Unit,
    viewModel: PermissionsViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.onPermissionsUpdated() }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.onPermissionsUpdated() }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PermissionsEffect.RequestRuntimePermissions ->
                    runtimePermissionLauncher.launch(effect.permissions.toTypedArray())
                is PermissionsEffect.OpenSettingsScreen -> {
                    val intent = Intent(effect.intentAction).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    settingsLauncher.launch(intent)
                }
            }
        }
    }

    // Safety net: some OEMs return from a Settings screen without always firing the
    // StartActivityForResult callback promptly, so re-check on every resume too.
    val currentOnResume = rememberUpdatedState(viewModel::onPermissionsUpdated)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) currentOnResume.value()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PermissionsScreen(
        uiState = uiState,
        interactionListener = viewModel,
        onContinue = onContinue,
    )
}

@Composable
private fun PermissionsScreen(
    uiState: PermissionsUiState,
    interactionListener: PermissionsInteractionListener,
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
            AslWizardStepper(steps = ONBOARDING_STEPS, current = 0, modifier = Modifier.padding(horizontal = 4.dp))
            PermissionsHeader(colors = colors)
            PermissionsList(
                uiState = uiState,
                interactionListener = interactionListener,
                modifier = Modifier.weight(1f),
            )
            PermissionsContinueSection(uiState = uiState, onContinue = onContinue, colors = colors)
        }
    }
}

@Composable
private fun PermissionsHeader(colors: AslColorScheme) {
    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 18.dp)) {
        Text(text = "A few permissions", style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary)
        Text(
            text = "The IDE needs these to build and install your apps.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun PermissionsList(
    uiState: PermissionsUiState,
    interactionListener: PermissionsInteractionListener,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(uiState.permissions, key = { _, it -> it.id }) { index, permission ->
            AslStaggeredAppear(index = index) {
                AslPermissionCard(
                    title = permission.title,
                    reason = permission.reason,
                    icon = permission.icon,
                    granted = permission.granted,
                    onGrant = { interactionListener.onGrantPermission(permission.id) },
                )
            }
        }
    }
}

@Composable
private fun PermissionsContinueSection(
    uiState: PermissionsUiState,
    onContinue: () -> Unit,
    colors: AslColorScheme,
) {
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
