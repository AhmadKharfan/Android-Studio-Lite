package com.example.androidstudiolite.core.designsystem.component.buttons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button as M3Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.core.designsystem.icon.AslIcon
import com.example.androidstudiolite.core.designsystem.modifier.pressScale
import com.example.androidstudiolite.core.designsystem.theme.AslShape
import com.example.androidstudiolite.core.designsystem.theme.AslTheme

/** Button.jsx — primary (accent fill), secondary (outlined), tertiary (text), destructive (error fill). */
enum class AslButtonVariant { Primary, Secondary, Tertiary, Destructive }

/** md = 40dp (toolbar density), lg = 48dp (hub / thumb zone). */
enum class AslButtonSize(val height: Dp) {
    Md(40.dp), Lg(48.dp),
}

@Composable
fun AslButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AslButtonVariant = AslButtonVariant.Primary,
    size: AslButtonSize = AslButtonSize.Md,
    icon: String? = null,
    trailingIcon: String? = null,
    disabled: Boolean = false,
    loading: Boolean = false,
    fullWidth: Boolean = false,
) {
    val colors = AslTheme.colors
    val inactive = disabled || loading
    val interactionSource = remember { MutableInteractionSource() }
    val sizedModifier = (if (fullWidth) modifier.fillMaxWidth() else modifier.widthIn(min = 64.dp))
        .height(size.height)
        .pressScale(interactionSource, pressedScale = 0.96f)
    val shape = AslShape.md
    val content: @Composable () -> Unit = {
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
            icon != null -> AslIcon(name = icon, size = 18.dp)
        }
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        if (trailingIcon != null) {
            AslIcon(name = trailingIcon, size = 18.dp)
        }
    }

    when (variant) {
        AslButtonVariant.Primary -> M3Button(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = !inactive,
            interactionSource = interactionSource,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accentPrimary,
                contentColor = colors.accentOnPrimary,
                disabledContainerColor = colors.surfaceContainerHigh,
                disabledContentColor = colors.textDisabled,
            ),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) { ButtonRow(content) }

        AslButtonVariant.Secondary -> OutlinedButton(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = !inactive,
            interactionSource = interactionSource,
            shape = shape,
            border = BorderStroke(1.dp, if (inactive) Color.Transparent else colors.borderStrong),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (inactive) colors.surfaceContainerHigh else Color.Transparent,
                contentColor = colors.textPrimary,
                disabledContainerColor = colors.surfaceContainerHigh,
                disabledContentColor = colors.textDisabled,
            ),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) { ButtonRow(content) }

        AslButtonVariant.Tertiary -> TextButton(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = !inactive,
            interactionSource = interactionSource,
            shape = shape,
            colors = ButtonDefaults.textButtonColors(
                contentColor = colors.accentPrimary,
                disabledContentColor = colors.textDisabled,
            ),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) { ButtonRow(content) }

        AslButtonVariant.Destructive -> M3Button(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = !inactive,
            interactionSource = interactionSource,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.error,
                contentColor = Color.White,
                disabledContainerColor = colors.surfaceContainerHigh,
                disabledContentColor = colors.textDisabled,
            ),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) { ButtonRow(content) }
    }
}

@Composable
private fun ButtonRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = { content() },
    )
}
