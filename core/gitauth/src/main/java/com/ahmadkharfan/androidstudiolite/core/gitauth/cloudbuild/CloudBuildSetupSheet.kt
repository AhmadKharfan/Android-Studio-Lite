package com.ahmadkharfan.androidstudiolite.core.gitauth.cloudbuild

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ahmadkharfan.androidstudiolite.core.gitauth.GitHubAuthDialog
import com.ahmadkharfan.androidstudiolite.core.gitauth.R
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslCircularProgress
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslBottomSheet
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTypography
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildStorageCreation
import java.text.DateFormat
import java.util.Date

/**
 * The Cloud Build setup sheet, plus the GitHub sign-in dialog it opens. Coming back to the app after
 * going to GitHub from the sheet checks again by itself.
 */
@Composable
fun CloudBuildSetupSheet(state: CloudBuildSetupUiState, actions: CloudBuildSetupActions, onResumed: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onResumed() }
    GitHubAuthDialog(state.authPrompt, actions)
    if (!state.visible || state.authPrompt.visible) return
    AslBottomSheet(onDismiss = actions::onCloudBuildDismiss, title = stringResource(R.string.cloud_build_title)) {
        CloudBuildSetupContent(state, actions)
    }
}

/** The body of the setup for [state], also usable inline (e.g. in settings). */
@Composable
fun CloudBuildSetupContent(
    state: CloudBuildSetupUiState,
    actions: CloudBuildSetupActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
) {
    val copy = copyFor(state.screen)
    Column(
        modifier = modifier.fillMaxWidth().padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SetupHeader(state, copy, actions)
        SetupButtons(state, copy, actions)
    }
}

/** The step's title and explanation, what creating the storage did, and the connected account. */
@Composable
private fun SetupHeader(state: CloudBuildSetupUiState, copy: SetupCopy, actions: CloudBuildSetupActions) {
    val colors = AslTheme.colors
    val screen = state.screen
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (screen.step == CloudBuildStep.Checking || state.checking) AslCircularProgress()
        Text(copy.title, style = AslTypography.titleMedium, color = colors.textPrimary)
    }
    val body = if (screen.primary == CloudBuildAction.CreateStorage) {
        stringResource(R.string.cloud_build_create_auto_body)
    } else {
        copy.body
    }
    body?.let { Text(it, style = AslTypography.bodyMedium, color = colors.textSecondary) }
    StorageCreationNote(state.storageCreation)
    screen.account?.let { account ->
        Text(stringResource(R.string.cloud_build_connected_as, account), style = AslTypography.bodySmall, color = colors.success)
        AslButton(
            label = stringResource(R.string.cloud_build_switch_account),
            onClick = actions::onCloudBuildSwitchAccount,
            variant = AslButtonVariant.Tertiary,
        )
    }
}

/** The step's actions, plus "Check again" whenever the fix happens on GitHub. */
@Composable
private fun SetupButtons(state: CloudBuildSetupUiState, copy: SetupCopy, actions: CloudBuildSetupActions) {
    val colors = AslTheme.colors
    val uriHandler = LocalUriHandler.current
    val screen = state.screen
    val labels = ActionLabels(
        create = stringResource(R.string.cloud_build_create_auto_action),
        manualInstead = stringResource(R.string.cloud_build_create_manual_instead),
    )
    screen.primary?.let { action ->
        AslButton(
            label = labels.primary(action, copy),
            onClick = { perform(action, actions) { uriHandler.openUri(it) } },
            icon = if (action is CloudBuildAction.OpenGitHub) "external-link" else null,
            loading = state.isBusyWith(action),
            fullWidth = true,
        )
    }
    screen.secondary?.let { action ->
        copy.secondaryHint?.let { Text(it, style = AslTypography.bodySmall, color = colors.textTertiary) }
        AslButton(
            label = labels.secondary(action, screen, copy),
            onClick = { perform(action, actions) { uriHandler.openUri(it) } },
            variant = AslButtonVariant.Secondary,
            loading = state.isBusyWith(action),
            fullWidth = true,
        )
    }
    if (screen.primary is CloudBuildAction.OpenGitHub) {
        Text(stringResource(R.string.cloud_build_back_hint), style = AslTypography.bodySmall, color = colors.textTertiary)
        AslButton(
            label = stringResource(R.string.cloud_build_check_again),
            onClick = actions::onCloudBuildCheckAgain,
            variant = AslButtonVariant.Tertiary,
            loading = state.checking,
        )
    }
}

/** Labels for actions whose text doesn't depend on the step. */
private class ActionLabels(val create: String, val manualInstead: String) {
    fun primary(action: CloudBuildAction, copy: SetupCopy): String =
        if (action == CloudBuildAction.CreateStorage) create else copy.primaryLabel

    fun secondary(action: CloudBuildAction, screen: CloudBuildSetupScreen, copy: SetupCopy): String = when {
        action == CloudBuildAction.CreateStorage -> create
        screen.primary == CloudBuildAction.CreateStorage -> manualInstead
        else -> copy.secondaryLabel.orEmpty()
    }
}

private fun CloudBuildSetupUiState.isBusyWith(action: CloudBuildAction): Boolean = when (action) {
    CloudBuildAction.CheckAgain -> checking
    CloudBuildAction.CreateStorage -> creatingStorage
    else -> false
}

/** What the last attempt to create the build storage from the app did, or why it couldn't. */
@Composable
private fun StorageCreationNote(outcome: CloudBuildStorageCreation?) {
    val colors = AslTheme.colors
    val (text, success) = when (outcome) {
        null -> return
        is CloudBuildStorageCreation.Created -> stringResource(R.string.cloud_build_created) to true
        is CloudBuildStorageCreation.AlreadyExists -> stringResource(R.string.cloud_build_created_existing) to true
        is CloudBuildStorageCreation.ExistsButPublic -> stringResource(R.string.cloud_build_create_public) to false
        is CloudBuildStorageCreation.AccountMismatch ->
            stringResource(R.string.cloud_build_create_mismatch, outcome.signInAccount, outcome.buildAccount) to false
        CloudBuildStorageCreation.NotAllowed -> stringResource(R.string.cloud_build_create_not_allowed) to false
        CloudBuildStorageCreation.SignInExpired -> stringResource(R.string.cloud_build_create_expired) to false
        CloudBuildStorageCreation.NoSignIn -> stringResource(R.string.cloud_build_create_no_sign_in) to false
        CloudBuildStorageCreation.BuildNotConnected -> stringResource(R.string.cloud_build_create_not_connected) to false
        CloudBuildStorageCreation.Offline -> stringResource(R.string.cloud_build_create_offline) to false
        CloudBuildStorageCreation.GitHubUnavailable -> stringResource(R.string.cloud_build_create_unavailable) to false
        is CloudBuildStorageCreation.Failed -> stringResource(R.string.cloud_build_create_failed, outcome.message) to false
    }
    Text(text, style = AslTypography.bodySmall, color = if (success) colors.success else colors.error)
}

private fun perform(action: CloudBuildAction, actions: CloudBuildSetupActions, openUri: (String) -> Unit) = when (action) {
    CloudBuildAction.Connect -> actions.onCloudBuildConnect()
    CloudBuildAction.CheckAgain -> actions.onCloudBuildCheckAgain()
    CloudBuildAction.CreateStorage -> actions.onCloudBuildCreateStorage()
    is CloudBuildAction.OpenGitHub -> {
        actions.onCloudBuildOpenedGitHub()
        openUri(action.url)
    }
}

private data class SetupCopy(
    val title: String,
    val body: String?,
    val primaryLabel: String,
    val secondaryLabel: String? = null,
    val secondaryHint: String? = null,
)

@Composable
private fun copyFor(screen: CloudBuildSetupScreen): SetupCopy {
    val createLabel = stringResource(R.string.cloud_build_create_secondary)
    return when (screen.step) {
        CloudBuildStep.Checking -> SetupCopy(stringResource(R.string.cloud_build_checking), null, "")
        CloudBuildStep.Connect -> SetupCopy(
            stringResource(R.string.cloud_build_connect_title),
            stringResource(R.string.cloud_build_connect_body),
            stringResource(R.string.cloud_build_connect_action),
        )
        CloudBuildStep.Reconnect -> SetupCopy(
            stringResource(R.string.cloud_build_reconnect_title),
            stringResource(R.string.cloud_build_reconnect_body),
            stringResource(R.string.cloud_build_reconnect_action),
        )
        CloudBuildStep.AllowAccess -> SetupCopy(
            stringResource(R.string.cloud_build_allow_title),
            stringResource(R.string.cloud_build_allow_body),
            stringResource(R.string.cloud_build_allow_action),
            secondaryLabel = createLabel,
            secondaryHint = stringResource(R.string.cloud_build_allow_no_storage),
        )
        CloudBuildStep.AccessPaused -> SetupCopy(
            stringResource(R.string.cloud_build_paused_title),
            stringResource(R.string.cloud_build_paused_body),
            stringResource(R.string.cloud_build_open_github),
        )
        CloudBuildStep.CreateStorage -> SetupCopy(
            stringResource(R.string.cloud_build_create_title),
            stringResource(R.string.cloud_build_create_body),
            stringResource(R.string.cloud_build_create_action),
        )
        CloudBuildStep.StorageNotReachable -> SetupCopy(
            stringResource(R.string.cloud_build_unreachable_title),
            stringResource(R.string.cloud_build_unreachable_body),
            stringResource(R.string.cloud_build_unreachable_action),
            secondaryLabel = createLabel,
        )
        else -> statusCopy(screen)
    }
}

/** Copy for states the user fixes by waiting, approving, or making the storage private. */
@Composable
private fun statusCopy(screen: CloudBuildSetupScreen): SetupCopy {
    val tryAgain = stringResource(R.string.cloud_build_try_again)
    val safe = stringResource(R.string.cloud_build_project_safe)
    return when (screen.step) {
        CloudBuildStep.StoragePublic -> SetupCopy(
            stringResource(R.string.cloud_build_public_title),
            stringResource(R.string.cloud_build_public_body),
            stringResource(R.string.cloud_build_public_action),
        )
        CloudBuildStep.ApproveUpdate -> SetupCopy(
            stringResource(R.string.cloud_build_update_title),
            stringResource(R.string.cloud_build_update_body),
            stringResource(R.string.cloud_build_update_action),
        )
        CloudBuildStep.Offline -> SetupCopy(
            stringResource(R.string.cloud_build_offline_title),
            "${stringResource(R.string.cloud_build_offline_body)} $safe",
            tryAgain,
        )
        CloudBuildStep.GitHubUnavailable -> SetupCopy(
            stringResource(R.string.cloud_build_down_title),
            "${stringResource(R.string.cloud_build_down_body)} $safe",
            tryAgain,
        )
        CloudBuildStep.RateLimited -> SetupCopy(
            stringResource(R.string.cloud_build_rate_title),
            screen.resetAtEpochSeconds
                ?.let { stringResource(R.string.cloud_build_rate_body_at, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it * 1000))) }
                ?: stringResource(R.string.cloud_build_rate_body),
            tryAgain,
        )
        CloudBuildStep.Ready -> SetupCopy(stringResource(R.string.cloud_build_ready_title), null, "")
        else -> SetupCopy(stringResource(R.string.cloud_build_unknown_title), screen.detail, tryAgain)
    }
}
