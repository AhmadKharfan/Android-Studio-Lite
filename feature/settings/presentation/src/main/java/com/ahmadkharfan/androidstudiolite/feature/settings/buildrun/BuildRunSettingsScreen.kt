package com.ahmadkharfan.androidstudiolite.feature.settings.buildrun
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslScaffold
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslSnackbarHost
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslSnackbarState
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.rememberAslSnackbarState
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslSectionHeader
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDialog
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDialogVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSwitch
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextFieldType
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.ahmadkharfan.androidstudiolite.designsystem.layout.aslImePadding
import com.ahmadkharfan.androidstudiolite.designsystem.modifier.aslCard
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTextStyles
import com.ahmadkharfan.androidstudiolite.core.common.R as CommonR
import com.ahmadkharfan.androidstudiolite.feature.settings.R
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
    val snackbarHostState = rememberAslSnackbarState()
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            interactionListener.onMessageShown()
        }
    }
    AslScaffold(
        containerColor = colors.bgBase,
        snackbarHost = { AslSnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AslTopAppBar(title = stringResource(CommonR.string.settings_build_run), onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .aslImePadding()
                    .padding(16.dp),
            ) {
                BuildRunOutputSection(uiState = uiState, interactionListener = interactionListener, colors = colors)
                BuildRunSigningSection(uiState = uiState, interactionListener = interactionListener, colors = colors)
                BuildRunAfterBuildSection(uiState = uiState, interactionListener = interactionListener)
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
    AslSectionHeader(stringResource(R.string.settings_build_output_format))
    SectionCard {
        AslSwitch(
            label = stringResource(R.string.settings_build_aab_release),
            checked = uiState.buildOutputAab,
            onCheckedChange = { interactionListener.onToggleAabOutput(it) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    AslText(
        text = stringResource(R.string.settings_build_aab_hint),
        style = AslTextStyles.bodySmall,
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
    AslSectionHeader(stringResource(R.string.settings_build_signing))
    SectionCard {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            AslText(stringResource(R.string.settings_build_debug_keystore), style = AslTextStyles.labelMedium, color = colors.textSecondary)
            AslText(
                text = uiState.debugKeystorePath.ifBlank { stringResource(R.string.settings_build_debug_keystore_auto) },
                style = AslTextStyles.bodySmall,
                color = colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            AslText(stringResource(R.string.settings_build_release_keystore), style = AslTextStyles.labelMedium, color = colors.textSecondary)
            if (uiState.hasReleaseKeystore) {
                AslText(
                    text = uiState.releaseKeystoreSummary.orEmpty(),
                    style = AslTextStyles.bodySmall,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                AslButton(
                    label = stringResource(CommonR.string.action_remove),
                    onClick = { interactionListener.onRemoveReleaseKeystore() },
                    variant = AslButtonVariant.Tertiary,
                )
            } else {
                AslText(
                    text = stringResource(R.string.settings_build_release_blocked),
                    style = AslTextStyles.bodySmall,
                    color = colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AslButton(
                        label = stringResource(R.string.settings_build_create_ellipsis),
                        onClick = { interactionListener.onOpenKeystoreDialog(KeystoreDialogMode.Create) },
                        variant = AslButtonVariant.Secondary,
                        icon = "plus",
                    )
                    AslButton(
                        label = stringResource(R.string.settings_build_import_ellipsis),
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
) {
    AslSectionHeader(stringResource(R.string.settings_build_after_build))
    SectionCard {
        AslSwitch(
            label = stringResource(R.string.settings_build_launch_after_install),
            checked = uiState.launchAfterInstall,
            onCheckedChange = { interactionListener.onToggleLaunchAfterInstall(it) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aslCard()
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
        title = stringResource(if (creating) R.string.settings_build_create_release_keystore else R.string.settings_build_import_release_keystore),
        onDismiss = onDismiss,
        variant = AslDialogVariant.Input,
        confirmLabel = stringResource(if (creating) CommonR.string.action_create else CommonR.string.action_import),
        cancelLabel = stringResource(CommonR.string.action_cancel),
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
                AslTextField(value = path, onValueChange = { path = it }, label = stringResource(R.string.settings_build_keystore_path))
                if (!creating) {
                    AslButton(
                        label = stringResource(R.string.settings_build_choose_keystore),
                        onClick = { keystorePicker.launch(arrayOf("application/octet-stream", "application/x-pkcs12")) },
                        variant = AslButtonVariant.Secondary,
                    )
                }
                AslTextField(value = storePassword, onValueChange = { storePassword = it }, label = stringResource(R.string.settings_build_store_password), type = AslTextFieldType.Password)
                AslTextField(value = alias, onValueChange = { alias = it }, label = stringResource(R.string.settings_build_key_alias))
                AslTextField(value = keyPassword, onValueChange = { keyPassword = it }, label = stringResource(R.string.settings_build_key_password), type = AslTextFieldType.Password)
                if (creating) {
                    AslTextField(value = commonName, onValueChange = { commonName = it }, label = stringResource(R.string.settings_build_common_name))
                    AslTextField(value = organization, onValueChange = { organization = it }, label = stringResource(R.string.settings_build_organization))
                    AslTextField(value = country, onValueChange = { country = it }, label = stringResource(R.string.settings_build_country))
                    AslTextField(value = validity, onValueChange = { validity = it }, label = stringResource(R.string.settings_build_validity_years), type = AslTextFieldType.Number)
                }
                if (error != null) {
                    AslText(text = error, style = AslTextStyles.bodySmall, color = AslTheme.colors.error)
                }
            }
        },
    )
}
