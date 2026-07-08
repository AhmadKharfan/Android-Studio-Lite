package com.example.androidstudiolite.core.designsystem.component.ide

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.core.designsystem.animation.AslStateCrossfade
import com.example.androidstudiolite.core.designsystem.component.buttons.AslButton
import com.example.androidstudiolite.core.designsystem.icon.AslIcon
import com.example.androidstudiolite.core.designsystem.theme.AslMotion
import com.example.androidstudiolite.core.designsystem.theme.AslShape
import com.example.androidstudiolite.core.designsystem.theme.AslTheme

/** PermissionCard.jsx — onboarding permission card: icon, title, why-needed copy, Grant button / Granted state. */
@Composable
fun AslPermissionCard(
    title: String,
    reason: String,
    modifier: Modifier = Modifier,
    icon: String = "folder-lock",
    granted: Boolean = false,
    onGrant: () -> Unit = {},
) {
    val colors = AslTheme.colors
    // Single continuous cursor drives the chip colour and icon cross-dissolve in lockstep (same
    // pattern as AslWizardStepper) so they can never land out of sync with each other.
    val progress = remember { Animatable(if (granted) 1f else 0f) }
    LaunchedEffect(granted) {
        progress.animateTo(if (granted) 1f else 0f, animationSpec = AslMotion.standardSpec(320))
    }
    val g = progress.value

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, AslShape.lg)
            .border(1.dp, colors.borderDefault, AslShape.lg)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(lerp(colors.surfaceContainerLow, colors.successContainer, g), AslShape.md),
            contentAlignment = Alignment.Center,
        ) {
            AslIcon(name = icon, size = 22.dp, tint = colors.textSecondary, modifier = Modifier.alpha(1f - g))
            AslIcon(name = "check", size = 22.dp, tint = colors.success, modifier = Modifier.alpha(g))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            // The button (taller) and "Granted" label (shorter) differ in height, so the fade alone
            // would still end in a hard size snap the instant the outgoing content leaves composition —
            // animateContentSize smooths that final resize into the same motion as the cross-dissolve.
            Box(modifier = Modifier.animateContentSize(AslMotion.standardSpec())) {
                AslStateCrossfade(targetState = granted, label = "permCta") { isGranted ->
                    if (isGranted) {
                        Text(text = "Granted", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = colors.success)
                    } else {
                        AslButton(label = "Grant access", onClick = onGrant)
                    }
                }
            }
        }
    }
}
