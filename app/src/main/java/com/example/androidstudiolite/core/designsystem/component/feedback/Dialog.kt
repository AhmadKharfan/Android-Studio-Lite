package com.example.androidstudiolite.core.designsystem.component.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.androidstudiolite.core.designsystem.component.buttons.AslButton
import com.example.androidstudiolite.core.designsystem.component.buttons.AslButtonVariant
import com.example.androidstudiolite.core.designsystem.theme.AslShape
import com.example.androidstudiolite.core.designsystem.theme.AslTheme

enum class AslDialogVariant { Alert, Confirm, Input }

/** Dialog.jsx — centered modal: alert (1-2 buttons), confirm (destructive accent), input (embedded field). */
@Composable
fun AslDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AslDialogVariant = AslDialogVariant.Alert,
    body: String? = null,
    confirmLabel: String = "OK",
    cancelLabel: String? = null,
    destructive: Boolean = false,
    onConfirm: () -> Unit = {},
    inputContent: (@Composable () -> Unit)? = null,
) {
    val colors = AslTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier.widthIn(max = 360.dp),
            shape = AslShape.xl,
            color = colors.surface,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
                if (body != null) {
                    Text(text = body, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
                if (variant == AslDialogVariant.Input && inputContent != null) {
                    inputContent()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (cancelLabel != null) {
                        AslButton(
                            label = cancelLabel,
                            variant = AslButtonVariant.Tertiary,
                            onClick = onDismiss,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    AslButton(
                        label = confirmLabel,
                        variant = if (destructive) AslButtonVariant.Destructive else AslButtonVariant.Primary,
                        onClick = onConfirm,
                    )
                }
            }
        }
    }
}
