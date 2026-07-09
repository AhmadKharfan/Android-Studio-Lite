package com.example.androidstudiolite.feature.onboarding.complete
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import com.example.androidstudiolite.designsystem.component.buttons.AslButton
import com.example.androidstudiolite.designsystem.component.buttons.AslButtonSize
import com.example.androidstudiolite.designsystem.icon.AslIcon
import com.example.androidstudiolite.designsystem.component.inputs.AslWizardStepper
import com.example.androidstudiolite.designsystem.theme.AslTheme
import com.example.androidstudiolite.feature.onboarding.common.ONBOARDING_STEPS
import com.example.androidstudiolite.feature.onboarding.complete.CompleteViewModel

@Composable
fun CompleteRoute(
    onOpenHub: () -> Unit,
    viewModel: CompleteViewModel = koinViewModel(),
) {
    CompleteScreen(onOpen = { viewModel.onFinish(onOpenHub) })
}

@Composable
private fun CompleteScreen(onOpen: () -> Unit) {
    val colors = AslTheme.colors
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            AslWizardStepper(steps = ONBOARDING_STEPS, current = 3, modifier = Modifier.padding(horizontal = 4.dp))
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .background(colors.successContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    AslIcon(name = "check", size = 40.dp, tint = colors.success)
                }
                Text(
                    text = "You're ready to build",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(
                    text = "JDK and SDK installed. Everything runs on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            AslButton(label = "Open Android Studio Lite", onClick = onOpen, size = AslButtonSize.Lg, fullWidth = true)
        }
    }
}
