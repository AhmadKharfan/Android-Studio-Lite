package com.ahmadkharfan.androidstudiolite.designsystem.component.inputs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMetrics
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import kotlinx.coroutines.launch

enum class AslTextFieldType { Text, Password, Email, Url, Number }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AslTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helper: String? = null,
    error: String? = null,
    disabled: Boolean = false,
    type: AslTextFieldType = AslTextFieldType.Text,
    leadingIcon: String? = null,
    trailingIcon: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val colors = AslTheme.colors
    var focused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val borderColor = when {
        error != null -> colors.error
        focused -> colors.accentPrimary
        else -> colors.borderStrong
    }
    val fieldTextStyle = if (type == AslTextFieldType.Password) AslTheme.code.codeBody else MaterialTheme.typography.bodyMedium

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (disabled) colors.textDisabled else colors.textSecondary,
            )
            Spacer(Modifier.height(6.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AslMetrics.fieldHeight)
                .background(if (disabled) colors.surfaceContainerLow else colors.bgElevated, AslShape.md)
                .border(if (focused && error == null) 2.dp else 1.dp, if (disabled) colors.borderDefault else borderColor, AslShape.md)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                AslIcon(name = leadingIcon, size = 18.dp, tint = colors.textTertiary)
                Spacer(Modifier.width(8.dp))
            }
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder != null) {
                    Text(text = placeholder, style = fieldTextStyle, color = colors.textTertiary)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = !disabled,
                    singleLine = true,
                    textStyle = fieldTextStyle.copy(color = if (disabled) colors.textDisabled else colors.textPrimary),
                    cursorBrush = SolidColor(colors.accentPrimary),
                    visualTransformation = if (type == AslTextFieldType.Password) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (type) {
                            AslTextFieldType.Email -> KeyboardType.Email
                            AslTextFieldType.Url -> KeyboardType.Uri
                            AslTextFieldType.Number -> KeyboardType.Number
                            AslTextFieldType.Password -> KeyboardType.Password
                            AslTextFieldType.Text -> KeyboardType.Text
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .onFocusChanged {
                            focused = it.isFocused
                            onFocusChanged?.invoke(it.isFocused)
                            if (it.isFocused) {
                                scope.launch { bringIntoViewRequester.bringIntoView() }
                            }
                        },
                )
            }
            if (trailingIcon != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .then(if (onTrailingClick != null) Modifier.clickable(onClick = onTrailingClick) else Modifier),
                ) {
                    AslIcon(name = trailingIcon, size = 18.dp, tint = colors.textTertiary)
                }
            }
        }
        val message = error ?: helper
        if (message != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) colors.error else colors.textTertiary,
            )
        }
    }
}
