package com.ahmadkharfan.androidstudiolite.feature.onboarding.welcome
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmadkharfan.androidstudiolite.R
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonSize
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

@Composable
fun WelcomeRoute(onGetStarted: () -> Unit) {
    WelcomeScreen(onGetStarted = onGetStarted)
}

@Composable
private fun WelcomeScreen(onGetStarted: () -> Unit) {
    val colors = AslTheme.colors
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            WelcomeHero(colors = colors, modifier = Modifier.weight(1.1f).fillMaxWidth())
            WelcomeBulletList(modifier = Modifier.padding(vertical = 8.dp))
            AslButton(
                label = stringResource(R.string.action_get_started),
                onClick = onGetStarted,
                size = AslButtonSize.Lg,
                fullWidth = true,
            )
        }
    }
}

@Composable
private fun WelcomeHero(colors: AslColorScheme, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(Color(0xFF1E1E2E), RoundedCornerShape(22.dp))
                    .border(1.dp, colors.borderDefault, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "{ }",
                    color = Color(0xFF34D399),
                    fontFamily = AslCode.codeBody.fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.onboarding_welcome_title),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.onboarding_welcome_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun WelcomeBulletList(modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = modifier) {
        WelcomeBullet(
            icon = "hammer",
            title = stringResource(R.string.onboarding_bullet_builds_title),
            text = stringResource(R.string.onboarding_bullet_builds_text),
        )
        WelcomeBullet(
            icon = "braces",
            title = stringResource(R.string.onboarding_bullet_code_title),
            text = stringResource(R.string.onboarding_bullet_code_text),
        )
        WelcomeBullet(
            icon = "git-branch",
            title = stringResource(R.string.onboarding_bullet_git_ai_title),
            text = stringResource(R.string.onboarding_bullet_git_ai_text),
        )
    }
}

@Composable
private fun WelcomeBullet(icon: String, title: String, text: String) {
    val colors = AslTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(colors.accentPrimaryContainer, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AslIcon(name = icon, size = 20.dp, tint = colors.accentPrimary)
        }
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
