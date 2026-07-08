package com.example.androidstudiolite.core.designsystem.component.inputs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.core.designsystem.icon.AslIcon
import com.example.androidstudiolite.core.designsystem.theme.AslTheme

/** WizardStepper.jsx — horizontal steps with connector lines (project wizard 1-4). */
@Composable
fun AslWizardStepper(
    steps: List<String>,
    current: Int,
    modifier: Modifier = Modifier,
) {
    val colors = AslTheme.colors

    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        steps.forEachIndexed { index, step ->
            val done = index < current
            val active = index == current
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 13.dp)
                        .height(2.dp)
                        .background(if (index <= current) colors.accentPrimary else colors.borderDefault),
                )
            }
            Column(
                modifier = Modifier.widthIn(min = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .then(
                            if (done || active) {
                                Modifier.background(colors.accentPrimary, CircleShape)
                            } else {
                                Modifier
                                    .background(colors.bgElevated, CircleShape)
                                    .border(1.5.dp, colors.borderStrong, CircleShape)
                            },
                        )
                        .then(
                            if (active) {
                                Modifier.border(4.dp, colors.accentPrimaryContainer, CircleShape)
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        AslIcon(name = "check", size = 15.dp, tint = colors.accentOnPrimary)
                    } else {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) colors.accentOnPrimary else colors.textTertiary,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = step,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = when {
                        active -> colors.textPrimary
                        done -> colors.textSecondary
                        else -> colors.textTertiary
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 84.dp),
                )
            }
        }
    }
}
