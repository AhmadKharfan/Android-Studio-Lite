package com.ahmadkharfan.androidstudiolite.feature.settings.buildrun
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDialog
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDialogVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSwitch
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextFieldType
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.ahmadkharfan.androidstudiolite.designsystem.layout.aslImePadding
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.hub.components.HubSectionHeader
import org.koin.androidx.compose.koinViewModel

@Composable
fun BuildRunSettingsRoute(
    onBack: () -> Unit,
    viewModel: BuildRunViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    BuildRunSettingsScreen(uiState = uiState, interactionListener = viewModel, onBack = onBack)
}

@Composable
private fun BuildRunSettingsScreen(
    uiState: BuildRunUiState,
    interactionListener: BuildRunInteractionListener,
    onBack: () -> Unit,
) {
    val colors = AslTheme.colors
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            interactionListener.onMessageShown()
        }
    }
    Scaffold(
        containerColor = colors.bgBase,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AslTopAppBar(title = "Build & Run", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .aslImePadding()
                    .padding(16.dp),
            ) {
                BuildRunOutputSection(uiState = uiState, interactionListener = interactionListener, colors = colors)
                BuildRunSigningSection(uiState = uiState, interactionListener = interactionListener, colors = colors)
                BuildRunAfterBuildSection(uiState = uiState, interactionListener = interactionListener, colors = colors)
            }
        }
    }
    val dialogMode = uiState.keystoreDialog
    if (dialogMode != null) {
        ReleaseKeystoreDialog(
            mode = dialogMode,
            suggestedPath = uiState.suggestedReleaseKeystorePath,
            isBusy = uiState.keystoreBusy,
            error = uiState.keystoreError,
            onDismiss = { interactionListener.onDismissKeystoreDialog() },
            onSubmit = { form ->
                when (dialogMode) {
                    KeystoreDialogMode.Create -> interactionListener.onCreateReleaseKeystore(form)
                    KeystoreDialogMode.Import -> interactionListener.onImportReleaseKeystore(form)
                }
            },
        )
    }
}

@Composable
private fun BuildRunOutputSection(
    uiState: BuildRunUiState,
    interactionListener: BuildRunInteractionListener,
    colors: AslColorScheme,
) {
    HubSectionHeader("Output format")
    SectionCard(colors) {
        AslSwitch(
            label = "Build App Bundle (.aab) for release",
            checked = uiState.buildOutputAab,
            onCheckedChange = { interactionListener.onToggleAabOutput(it) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Text(
        text = "App Bundles are required for Play Store uploads; APKs install directly on device.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.textTertiary,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
    )
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun BuildRunSigningSection(
    uiState: BuildRunUiState,
    interactionListener: BuildRunInteractionListener,
    colors: AslColorScheme,
) {
    HubSectionHeader("Signing")
    SectionCard(colors) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text("Debug keystore", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            Text(
                text = uiState.debugKeystorePath.ifBlank { "Auto-generated on first build" },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("Release keystore", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            if (uiState.hasReleaseKeystore) {
                Text(
                    text = uiState.releaseKeystoreSummary.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                AslButton(
                    label = "Remove",
                    onClick = { interactionListener.onRemoveReleaseKeystore() },
                    variant = AslButtonVariant.Tertiary,
                )
            } else {
                Text(
                    text = "Release builds are blocked until you create or import a valid private-key keystore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AslButton(
                        label = "Create…",
                        onClick = { interactionListener.onOpenKeystoreDialog(KeystoreDialogMode.Create) },
                        variant = AslButtonVariant.Secondary,
                        icon = "plus",
                    )
                    AslButton(
                        label = "Import…",
                        onClick = { interactionListener.onOpenKeystoreDialog(KeystoreDialogMode.Import) },
                        variant = AslButtonVariant.Secondary,
                        icon = "folder-open",
                    )
                }
            }
        }
    }
}

@Composable
private fun BuildRunAfterBuildSection(
    uiState: BuildRunUiState,
    interactionListener: BuildRunInteractionListener,
    colors: AslColorScheme,
) {
    HubSectionHeader("After build")
    SectionCard(colors) {
        AslSwitch(
            label = "Launch app after install",
            checked = uiState.launchAfterInstall,
            onCheckedChange = { interactionListener.onToggleLaunchAfterInstall(it) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SectionCard(colors: AslColorScheme, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, AslShape.lg)
            .border(1.dp, colors.borderDefault, AslShape.lg)
            .padding(horizontal = 16.dp),
        content = { content() },
    )
}

@Composable
private fun ReleaseKeystoreDialog(
    mode: KeystoreDialogMode,
    suggestedPath: String,
    isBusy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (KeystoreForm) -> Unit,
) {
    val creating = mode == KeystoreDialogMode.Create
    var path by remember { mutableStateOf(if (creating) suggestedPath else "") }
    var storePassword by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var keyPassword by remember { mutableStateOf("") }
    var validity by remember { mutableStateOf("25") }
    var commonName by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    val keystorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) path = uri.toString()
    }

    AslDialog(
        title = if (creating) "Create release keystore" else "Import release keystore",
        onDismiss = onDismiss,
        variant = AslDialogVariant.Input,
        confirmLabel = if (creating) "Create" else "Import",
        cancelLabel = "Cancel",
        onConfirm = {
            if (!isBusy) {
                onSubmit(
                    KeystoreForm(
                        storePath = path.trim(),
                        storePassword = storePassword,
                        keyAlias = alias.trim(),
                        keyPassword = keyPassword,
                        validityYears = validity,
                        commonName = commonName,
                        organization = organization,
                        country = country,
                    ),
                )
            }
        },
        inputContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AslTextField(value = path, onValueChange = { path = it }, label = "Keystore path")
                if (!creating) {
                    AslButton(
                        label = "Choose keystore file",
                        onClick = { keystorePicker.launch(arrayOf("application/octet-stream", "application/x-pkcs12")) },
                        variant = AslButtonVariant.Secondary,
                    )
                }
                AslTextField(value = storePassword, onValueChange = { storePassword = it }, label = "Store password", type = AslTextFieldType.Password)
                AslTextField(value = alias, onValueChange = { alias = it }, label = "Key alias")
                AslTextField(value = keyPassword, onValueChange = { keyPassword = it }, label = "Key password", type = AslTextFieldType.Password)
                if (creating) {
                    AslTextField(value = commonName, onValueChange = { commonName = it }, label = "Name (CN)")
                    AslTextField(value = organization, onValueChange = { organization = it }, label = "Organization (O)")
                    AslTextField(value = country, onValueChange = { country = it }, label = "Country (C)")
                    AslTextField(value = validity, onValueChange = { validity = it }, label = "Validity (years)", type = AslTextFieldType.Number)
                }
                if (error != null) {
                    Text(text = error, style = MaterialTheme.typography.bodySmall, color = AslTheme.colors.error)
                }
            }
        },
    )
}
