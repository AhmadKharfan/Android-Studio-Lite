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
import androidx.compose.ui.res.stringResource
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
import com.ahmadkharfan.androidstudiolite.feature.projects.R

@Composable
fun ConfigureStep(
    projectName: String,
    packageName: String,
    location: String,
    minSdk: String,
    language: String,
    supportsJava: Boolean,
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
            label = stringResource(R.string.projects_project_name),
            error = nameError,
            helper = if (nameError == null) stringResource(R.string.projects_project_name_helper) else null,
        )
        AslTextField(
            value = packageName,
            onValueChange = onPackageChanged,
            label = stringResource(R.string.projects_package_name),
            error = packageError,
            helper = if (packageError == null) stringResource(R.string.projects_package_name_helper) else null,
        )
        AslTextField(
            value = location,
            onValueChange = onLocationChanged,
            label = stringResource(R.string.projects_save_location),
            helper = stringResource(R.string.projects_save_location_helper),
            trailingIcon = "folder-open",
            onTrailingClick = onBrowseLocation,
        )
        LabeledSegmented(
            label = stringResource(R.string.projects_language),
            value = language,
            onValueChange = onLanguageChanged,
            options = listOf(
                AslSegmentedOption(stringResource(R.string.projects_language_kotlin), LANG_KOTLIN),
                AslSegmentedOption(stringResource(R.string.projects_language_java), LANG_JAVA, enabled = supportsJava),
            ),
        )
        if (!supportsJava) {
            Text(
                text = stringResource(R.string.projects_kotlin_required),
                style = MaterialTheme.typography.bodySmall,
                color = AslTheme.colors.textSecondary,
            )
        }
        AslDropdown(
            label = stringResource(R.string.projects_minimum_sdk),
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
