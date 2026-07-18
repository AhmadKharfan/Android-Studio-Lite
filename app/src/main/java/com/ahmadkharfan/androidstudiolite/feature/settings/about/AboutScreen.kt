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
import com.ahmadkharfan.androidstudiolite.BuildConfig
import com.ahmadkharfan.androidstudiolite.R
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslListItem
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChip
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChipKind
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

private const val REPO_URL = "https://github.com/AhmadKharfan/Android-Studio-Lite"

private data class AboutLink(val title: String, val subtitle: String?, val icon: String, val uri: String)

private val ABOUT_LINKS @Composable get() = listOf(
    AboutLink(stringResource(R.string.about_github), stringResource(R.string.about_github_sub), "github", REPO_URL),
    AboutLink(stringResource(R.string.about_docs), stringResource(R.string.about_docs_sub), "book-open", "$REPO_URL/tree/main/docs"),
    AboutLink(stringResource(R.string.about_email), "team@aslite.dev", "mail", "mailto:team@aslite.dev"),
)

/** Opens [uri] in an external handler (browser, email client, …), ignoring "no app can handle it". */
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
            AslTopAppBar(title = stringResource(R.string.about_title), onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                AboutHeader(colors = colors)
                AboutLinksList(colors = colors, onOpen = { context.openExternal(it) })
                AslButton(
                    label = stringResource(R.string.about_contributors),
                    onClick = { context.openExternal("$REPO_URL/graphs/contributors") },
                    variant = AslButtonVariant.Secondary,
                    icon = "users",
                    fullWidth = true,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    text = stringResource(R.string.about_license),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 20.dp),
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
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
        AslChip(
            label = stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            kind = AslChipKind.Status,
        )
    }
}

@Composable
private fun AboutLinksList(colors: AslColorScheme, onOpen: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, AslShape.lg)
            .border(1.dp, colors.borderDefault, AslShape.lg),
    ) {
        ABOUT_LINKS.forEachIndexed { index, link ->
            AslListItem(
                title = link.title,
                subtitle = link.subtitle,
                icon = link.icon,
                divider = index != ABOUT_LINKS.lastIndex,
                trailing = { AslIcon(name = "arrow-up-right", size = 16.dp, tint = colors.textTertiary) },
                onClick = { onOpen(link.uri) },
            )
        }
    }
}
