package com.example.androidstudiolite.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** tokens/shape.css — JetBrains New UI rounding. Exposed per-radius since components need
 *  xs/sm/md/lg/xl/full independently, not just M3's small/medium/large/extraLarge buckets. */
object AslShape {
    val none = RoundedCornerShape(0.dp)
    val xs = RoundedCornerShape(4.dp)
    val sm = RoundedCornerShape(6.dp)
    val md = RoundedCornerShape(8.dp)
    val lg = RoundedCornerShape(12.dp)
    val xl = RoundedCornerShape(16.dp)
    val full = RoundedCornerShape(percent = 50)
}

val AslShapes = Shapes(
    extraSmall = AslShape.xs,
    small = AslShape.sm,
    medium = AslShape.md,
    large = AslShape.lg,
    extraLarge = AslShape.xl,
)
