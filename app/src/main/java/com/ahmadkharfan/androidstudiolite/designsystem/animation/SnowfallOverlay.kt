package com.ahmadkharfan.androidstudiolite.designsystem.animation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.sin
import kotlin.random.Random

private data class Snowflake(
    val xFraction: Float,
    val startYFraction: Float,
    val radiusDp: Float,
    val fallSpeed: Float,
    val driftAmplitude: Float,
    val driftFrequency: Float,
    val phase: Float,
    val alpha: Float,
)

/**
 * A quiet, non-interactive snowfall drawn on top of the whole app. Purely decorative: it fills its
 * bounds, never consumes touch, and drives itself off frame time so it stays smooth without a heavy
 * per-flake animation. Callers gate visibility (the "Snowfall in December" easter egg).
 */
@Composable
fun SnowfallOverlay(
    modifier: Modifier = Modifier,
    flakeCount: Int = 55,
    color: Color = Color.White,
) {
    val flakes = remember(flakeCount) {
        val random = Random(flakeCount)
        List(flakeCount) {
            Snowflake(
                xFraction = random.nextFloat(),
                startYFraction = random.nextFloat(),
                radiusDp = 1.5f + random.nextFloat() * 2.5f,
                fallSpeed = 0.02f + random.nextFloat() * 0.05f,
                driftAmplitude = 8f + random.nextFloat() * 22f,
                driftFrequency = 0.4f + random.nextFloat() * 0.9f,
                phase = random.nextFloat() * 6.2832f,
                alpha = 0.25f + random.nextFloat() * 0.45f,
            )
        }
    }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> elapsedMs = (now - startNanos) / 1_000_000 }
        }
    }
    val seconds = elapsedMs / 1000f
    Canvas(modifier = modifier.fillMaxSize()) {
        val height = size.height
        val width = size.width
        flakes.forEach { flake ->
            val fallen = (flake.startYFraction + flake.fallSpeed * seconds) % 1f
            val y = fallen * height
            val drift = sin(seconds * flake.driftFrequency + flake.phase) * flake.driftAmplitude
            val x = flake.xFraction * width + drift
            drawCircle(
                color = color.copy(alpha = flake.alpha),
                radius = flake.radiusDp * density,
                center = Offset(x, y),
            )
        }
    }
}
