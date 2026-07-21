package com.ahmadkharfan.androidstudiolite.feature.settings.about
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmadkharfan.androidstudiolite.feature.settings.BuildConfig
import com.ahmadkharfan.androidstudiolite.core.common.R as CommonR
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslListItem
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChip
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChipKind
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.layout.aslImePadding
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

private const val REPO_URL = "https://github.com/AhmadKharfan/Android-Studio-Lite"

private fun Context.openExternal(uri: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { if (it !is ActivityNotFoundException) throw it }
}

@Composable
fun AboutRoute(onBack: () -> Unit) {
    AboutScreen(onBack = onBack)
}

@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val colors = AslTheme.colors
    val context = LocalContext.current
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AslTopAppBar(title = stringResource(CommonR.string.about_title), onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .aslImePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AboutHeader(colors = colors)
                Text(
                    text = stringResource(CommonR.string.about_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
                OpenSourceCard(colors = colors, onContribute = { context.openExternal(REPO_URL) })
                AboutLinksCard(
                    colors = colors,
                    onOpenRepo = { context.openExternal(REPO_URL) },
                    onOpenContributors = { context.openExternal("$REPO_URL/graphs/contributors") },
                )
                Text(
                    text = stringResource(CommonR.string.about_license),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AboutHeader(colors: AslColorScheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(Color(0xFF1E1E2E), RoundedCornerShape(19.dp))
                .border(1.dp, colors.borderDefault, RoundedCornerShape(19.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "{ }",
                color = Color(0xFF34D399),
                fontFamily = AslCode.codeBody.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
            )
        }
        Text(
            text = stringResource(CommonR.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(CommonR.string.app_tagline),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
        AslChip(
            label = stringResource(CommonR.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            kind = AslChipKind.Status,
        )
    }
}

@Composable
private fun OpenSourceCard(colors: AslColorScheme, onContribute: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.accentPrimaryContainer, AslShape.lg)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AslIcon(name = "heart-handshake", size = 18.dp, tint = colors.accentPrimary)
            Text(
                text = stringResource(CommonR.string.about_open_source_title),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
        }
        Text(
            text = stringResource(CommonR.string.about_open_source_body),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        AslButton(
            label = stringResource(CommonR.string.about_contribute),
            onClick = onContribute,
            variant = AslButtonVariant.Primary,
            icon = "git-pull-request",
            fullWidth = true,
        )
    }
}

@Composable
private fun AboutLinksCard(
    colors: AslColorScheme,
    onOpenRepo: () -> Unit,
    onOpenContributors: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, AslShape.lg)
            .border(1.dp, colors.borderDefault, AslShape.lg),
    ) {
        AslListItem(
            title = stringResource(CommonR.string.about_github),
            subtitle = stringResource(CommonR.string.about_github_sub),
            icon = "github",
            trailing = { AslIcon(name = "arrow-up-right", size = 16.dp, tint = colors.textTertiary) },
            onClick = onOpenRepo,
        )
        AslListItem(
            title = stringResource(CommonR.string.about_contributors),
            subtitle = stringResource(CommonR.string.about_contributors_sub),
            icon = "users",
            divider = false,
            trailing = { AslIcon(name = "chevron-right", size = 16.dp, tint = colors.textTertiary) },
            onClick = onOpenContributors,
        )
    }
}
