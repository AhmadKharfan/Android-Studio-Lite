package com.example.androidstudiolite.core.designsystem.component.ide

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.core.designsystem.component.buttons.AslButton
import com.example.androidstudiolite.core.designsystem.component.buttons.AslButtonVariant
import com.example.androidstudiolite.core.designsystem.component.inputs.AslTextField
import com.example.androidstudiolite.core.designsystem.component.inputs.AslTextFieldType
import com.example.androidstudiolite.core.designsystem.icon.AslIcon
import com.example.androidstudiolite.core.designsystem.theme.AslShape
import com.example.androidstudiolite.core.designsystem.theme.AslTheme

enum class AslApiKeyStatus { None, Valid, Invalid }

/** ApiKeyCard.jsx — AI-provider API key card: header, masked key field with reveal, Test button. */
@Composable
fun AslApiKeyCard(
    provider: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    providerIcon: String = "sparkles",
    description: String? = null,
    placeholder: String = "sk-…",
    status: AslApiKeyStatus = AslApiKeyStatus.None,
    onTest: () -> Unit = {},
    testing: Boolean = false,
) {
    val colors = AslTheme.colors
    var reveal by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, AslShape.lg)
            .border(1.dp, colors.borderDefault, AslShape.lg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(colors.bgSunken, AslShape.md)
                    .border(1.dp, colors.borderSubtle, AslShape.md),
                contentAlignment = Alignment.Center,
            ) {
                AslIcon(name = providerIcon, size = 18.dp, tint = colors.textSecondary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = provider, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                if (description != null) {
                    Text(text = description, style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
                }
            }
            when (status) {
                AslApiKeyStatus.Valid -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AslIcon(name = "check", size = 14.dp, tint = colors.success)
                    Text(text = "Valid", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = colors.success)
                }
                AslApiKeyStatus.Invalid -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AslIcon(name = "x", size = 14.dp, tint = colors.error)
                    Text(text = "Invalid", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = colors.error)
                }
                AslApiKeyStatus.None -> Unit
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AslTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                label = "API key",
                placeholder = placeholder,
                type = if (reveal) AslTextFieldType.Text else AslTextFieldType.Password,
                trailingIcon = if (reveal) "eye-off" else "eye",
                onTrailingClick = { reveal = !reveal },
            )
            AslButton(label = "Test", onClick = onTest, variant = AslButtonVariant.Secondary, loading = testing)
        }
    }
}
