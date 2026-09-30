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
    val colors = AslTheme.colors
    val uriHandler = LocalUriHandler.current
    val screen = state.screen
    val copy = copyFor(screen)
    Column(
        modifier = modifier.fillMaxWidth().padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (screen.step == CloudBuildStep.Checking || state.checking) AslCircularProgress()
            Text(copy.title, style = AslTypography.titleMedium, color = colors.textPrimary)
        }
        copy.body?.let { Text(it, style = AslTypography.bodyMedium, color = colors.textSecondary) }
        screen.account?.let { account ->
            Text(stringResource(R.string.cloud_build_connected_as, account), style = AslTypography.bodySmall, color = colors.success)
            AslButton(
                label = stringResource(R.string.cloud_build_switch_account),
                onClick = actions::onCloudBuildSwitchAccount,
                variant = AslButtonVariant.Tertiary,
            )
        }
        screen.primary?.let { action ->
            AslButton(
                label = copy.primaryLabel,
                onClick = { perform(action, actions) { uriHandler.openUri(it) } },
                icon = if (action is CloudBuildAction.OpenGitHub) "external-link" else null,
                loading = state.checking && action == CloudBuildAction.CheckAgain,
                fullWidth = true,
            )
        }
        screen.secondary?.let { action ->
            copy.secondaryHint?.let { Text(it, style = AslTypography.bodySmall, color = colors.textTertiary) }
            AslButton(
                label = copy.secondaryLabel.orEmpty(),
                onClick = { perform(action, actions) { uriHandler.openUri(it) } },
                variant = AslButtonVariant.Secondary,
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
}

private fun perform(action: CloudBuildAction, actions: CloudBuildSetupActions, openUri: (String) -> Unit) = when (action) {
    CloudBuildAction.Connect -> actions.onCloudBuildConnect()
    CloudBuildAction.CheckAgain -> actions.onCloudBuildCheckAgain()
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
