package com.example.androidstudiolite.designsystem.modifier

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import com.example.androidstudiolite.designsystem.theme.AslMotion

/**
 * Springs the element down slightly while [interactionSource] reports a press, then back on release —
 * a subtle, tactile "give" for cards, tiles and buttons. Place this early in the modifier chain (before
 * `background`/`border`) so the scale encloses the whole surface, and pass the same [interactionSource]
 * to the element's `clickable` so press state stays in sync with the ripple.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = AslMotion.pressScaleSpec(),
        label = "pressScale",
    )
    return this.scale(scale)
}
