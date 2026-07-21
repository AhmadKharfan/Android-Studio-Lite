package com.ahmadkharfan.androidstudiolite.feature.acsmissing
import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDialog
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

@Composable
fun AcsMissingRoute() {
    val activity = LocalContext.current as? Activity
    AcsMissingScreen(onExit = { activity?.finish() })
}

@Composable
private fun AcsMissingScreen(onExit: () -> Unit) {
    val colors = AslTheme.colors
    Box(modifier = Modifier.fillMaxSize().background(colors.bgBase)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.5f)
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text(text = "Good morning, Alex", style = MaterialTheme.typography.headlineLarge, color = colors.textPrimary)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .padding(top = 14.dp)
                    .background(colors.surface, AslShape.lg)
                    .border(1.dp, colors.borderDefault, AslShape.lg),
            )
        }
        AslDialog(
            title = "Build components missing",
            body = "This build of Android Studio Lite is missing its ACS core components. It may be an unofficial copy. Reinstall from an official source to continue.",
            confirmLabel = "Get official build",
            cancelLabel = "Exit",
            onConfirm = {},
            onDismiss = onExit,
        )
    }
}
