package com.ahmadkharfan.androidstudiolite.feature.onboarding.howitworks
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmadkharfan.androidstudiolite.designsystem.animation.AslStaggeredAppear
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonSize
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslWizardStepper
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.onboarding.R
import com.ahmadkharfan.androidstudiolite.feature.onboarding.common.ONBOARDING_STEPS

@Composable
fun HowItWorksRoute(onContinue: () -> Unit) {
    HowItWorksScreen(onContinue = onContinue)
}

private data class HowStep(val icon: String, val title: String, val text: String)

@Composable
private fun HowItWorksScreen(onContinue: () -> Unit) {
    val colors = AslTheme.colors
    val steps = listOf(
        HowStep(
            icon = "file-code",
            title = stringResource(R.string.onboarding_how_write_title),
            text = stringResource(R.string.onboarding_how_write_text),
        ),
        HowStep(
            icon = "git-branch",
            title = stringResource(R.string.onboarding_how_git_title),
            text = stringResource(R.string.onboarding_how_git_text),
        ),
        HowStep(
            icon = "smartphone",
            title = stringResource(R.string.onboarding_how_run_title),
            text = stringResource(R.string.onboarding_how_run_text),
        ),
    )
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            AslWizardStepper(
                steps = ONBOARDING_STEPS,
                current = 0,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                HowItWorksHeader(colors = colors)
                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                    steps.forEachIndexed { index, step ->
                        AslStaggeredAppear(index = index) {
                            HowStepRow(
                                number = index + 1,
                                step = step,
                                isLast = index == steps.lastIndex,
                                colors = colors,
                            )
                        }
                    }
                }
            }
            AslButton(
                label = stringResource(R.string.onboarding_how_continue),
                onClick = onContinue,
                size = AslButtonSize.Lg,
                fullWidth = true,
            )
        }
    }
}

@Composable
private fun HowItWorksHeader(colors: AslColorScheme) {
    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 22.dp)) {
        Text(
            text = stringResource(R.string.onboarding_how_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.onboarding_how_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun HowStepRow(
    number: Int,
    step: HowStep,
    isLast: Boolean,
    colors: AslColorScheme,
) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Column(
            modifier = Modifier.width(44.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(colors.accentPrimaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AslIcon(name = step.icon, size = 21.dp, tint = colors.accentPrimary)
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .padding(vertical = 6.dp)
                        .background(colors.borderDefault),
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.padding(bottom = if (isLast) 0.dp else 22.dp)) {
            Text(
                text = stringResource(R.string.onboarding_how_step_label, number).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                letterSpacing = 1.sp,
            )
            Text(
                text = step.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = step.text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
