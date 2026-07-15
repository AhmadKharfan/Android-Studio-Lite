package com.ahmadkharfan.androidstudiolite.feature.createproject.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslDropdown
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslDropdownOption
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSegmentedButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSegmentedOption
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.feature.createproject.LANG_JAVA
import com.ahmadkharfan.androidstudiolite.feature.createproject.LANG_KOTLIN
import com.ahmadkharfan.androidstudiolite.feature.createproject.MIN_SDK_OPTIONS

@Composable
fun ConfigureStep(
    projectName: String,
    packageName: String,
    location: String,
    minSdk: String,
    language: String,
    nameError: String?,
    packageError: String?,
    onNameChanged: (String) -> Unit,
    onPackageChanged: (String) -> Unit,
    onLocationChanged: (String) -> Unit,
    onMinSdkChanged: (String) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onBrowseLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AslTextField(
            value = projectName,
            onValueChange = onNameChanged,
            label = "Project name",
            error = nameError,
            helper = if (nameError == null) "Letters and digits, no spaces" else null,
        )
        AslTextField(
            value = packageName,
            onValueChange = onPackageChanged,
            label = "Package name",
            error = packageError,
            helper = if (packageError == null) "Also the applicationId, e.g. com.example.myapp" else null,
        )
        AslTextField(
            value = location,
            onValueChange = onLocationChanged,
            label = "Save location",
            helper = "The project folder is created here",
            trailingIcon = "folder-open",
            onTrailingClick = onBrowseLocation,
        )
        LabeledSegmented(
            label = "Language",
            value = language,
            onValueChange = onLanguageChanged,
            options = listOf(
                AslSegmentedOption("Kotlin", LANG_KOTLIN),
                AslSegmentedOption("Java", LANG_JAVA),
            ),
        )
        AslDropdown(
            label = "Minimum SDK",
            value = minSdk,
            onValueChange = onMinSdkChanged,
            options = MIN_SDK_OPTIONS.map { AslDropdownOption(it.label, it.value) },
        )
    }
}

@Composable
private fun LabeledSegmented(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<AslSegmentedOption>,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = AslTheme.colors.textSecondary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        AslSegmentedButton(
            options = options,
            value = value,
            onValueChange = onValueChange,
            fullWidth = true,
        )
    }
}
