package com.ahmadkharfan.androidstudiolite.feature.settings.about
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private data class AboutLink(val title: String, val subtitle: String?, val icon: String)

private val ABOUT_LINKS = listOf(
    AboutLink("GitHub", "Source & issues", "github"),
    AboutLink("Telegram", "Community channel", "send"),
    AboutLink("Documentation", null, "book-open"),
    AboutLink("Email", "team@aslite.dev", "mail"),
)

@Composable
fun AboutRoute(onBack: () -> Unit) {
    AboutScreen(onBack = onBack)
}

@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val colors = AslTheme.colors
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AslTopAppBar(title = "About", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                AboutHeader(colors = colors)
                AboutLinksList(colors = colors)
                AslButton(
                    label = "Contributors",
                    onClick = {},
                    variant = AslButtonVariant.Secondary,
                    icon = "users",
                    fullWidth = true,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    text = "Free software · GPLv3 license",
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
            text = "Android Studio Lite",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Simply, an IDE for Android",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
        AslChip(label = "v1.2.0 · build 214", kind = AslChipKind.Status)
    }
}

@Composable
private fun AboutLinksList(colors: AslColorScheme) {
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
                onClick = {},
            )
        }
    }
}
