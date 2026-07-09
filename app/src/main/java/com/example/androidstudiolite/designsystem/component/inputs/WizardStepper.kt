package com.example.androidstudiolite.designsystem.component.inputs

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.designsystem.icon.AslIcon
import com.example.androidstudiolite.designsystem.theme.AslMotion
import com.example.androidstudiolite.designsystem.theme.AslTheme
import kotlin.math.abs

/**
 * WizardStepper.jsx — horizontal steps with connector lines (project wizard 1-4).
 *
 * Every visual (connector fill, circle fill/outline, halo ring, glyph, label colour) is driven off a
 * single continuous [progress] cursor rather than independent per-property animations, so the whole
 * row moves in lockstep. This also fixes the case where each step lives on its own screen/navigation
 * destination (onboarding): a plain `animateColorAsState` has nothing to animate *from* the first time
 * a new destination composes already at its final `current` value, so it used to just snap in. Here
 * [progress] is seeded one step behind `current` on first composition and animates forward immediately,
 * so the "become active → become done" motion plays even on a freshly-mounted screen.
 */
@Composable
fun AslWizardStepper(
    steps: List<String>,
    current: Int,
    modifier: Modifier = Modifier,
) {
    val colors = AslTheme.colors
    val progress = remember { Animatable(if (current > 0) (current - 1).toFloat() else current.toFloat()) }
    LaunchedEffect(current) {
        progress.animateTo(current.toFloat(), animationSpec = AslMotion.standardSpec(420))
    }
    val p = progress.value

    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        steps.forEachIndexed { index, step ->
            if (index > 0) {
                // Fills as progress sweeps from (index - 1) to index.
                val connectorAmount = (p - (index - 1)).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 13.dp)
                        .height(2.dp)
                        .background(lerp(colors.borderDefault, colors.accentPrimary, connectorAmount)),
                )
            }
            // filled: 0 while untouched, 1 once progress has reached (or passed) this step.
            val filled = (p - index + 1f).coerceIn(0f, 1f)
            // done: 0 while this is the active/upcoming step, 1 once progress has moved past it.
            val done = (p - index).coerceIn(0f, 1f)
            // halo: peaks at 1 exactly when progress sits on this step, fades out on either side.
            val halo = (1f - abs(p - index)).coerceIn(0f, 1f)

            Column(
                modifier = Modifier.widthIn(min = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(lerp(colors.bgElevated, colors.accentPrimary, filled), CircleShape)
                        .border(1.5.dp, colors.borderStrong.copy(alpha = 1f - filled), CircleShape)
                        .border(4.dp * halo, colors.accentPrimaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    // Number and check are cross-dissolved off the same `done` value — no separate
                    // Crossfade/AnimatedVisibility needed, so there's nothing else to fall out of sync.
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = lerp(colors.textTertiary, colors.accentOnPrimary, filled),
                        modifier = Modifier.alpha(1f - done),
                    )
                    AslIcon(
                        name = "check",
                        size = 15.dp,
                        tint = colors.accentOnPrimary,
                        modifier = Modifier.alpha(done),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = step,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (halo > 0.5f) FontWeight.SemiBold else FontWeight.Medium,
                    color = lerp(lerp(colors.textTertiary, colors.textSecondary, filled), colors.textPrimary, halo),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 84.dp),
                )
            }
        }
    }
}
