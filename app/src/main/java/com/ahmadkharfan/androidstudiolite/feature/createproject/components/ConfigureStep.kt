package com.ahmadkharfan.androidstudiolite.feature.createproject.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslDropdown
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslDropdownOption
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.feature.createproject.MIN_SDK_OPTIONS

@Composable
fun ConfigureStep(
    projectName: String,
    packageName: String,
    location: String,
    minSdk: String,
    nameError: String?,
    onNameChanged: (String) -> Unit,
    onPackageChanged: (String) -> Unit,
    onLocationChanged: (String) -> Unit,
    onMinSdkChanged: (String) -> Unit,
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
        )
        AslTextField(
            value = location,
            onValueChange = onLocationChanged,
            label = "Save location",
            trailingIcon = "folder-open",
            onTrailingClick = onBrowseLocation,
        )
        AslDropdown(
            label = "Minimum SDK",
            value = minSdk,
            onValueChange = onMinSdkChanged,
            options = MIN_SDK_OPTIONS.map { AslDropdownOption(it.label, it.value) },
        )
    }
}
