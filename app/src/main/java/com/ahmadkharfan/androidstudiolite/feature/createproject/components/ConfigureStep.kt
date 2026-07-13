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
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSwitch
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.feature.createproject.DSL_GROOVY
import com.ahmadkharfan.androidstudiolite.feature.createproject.DSL_KTS
import com.ahmadkharfan.androidstudiolite.feature.createproject.LANG_JAVA
import com.ahmadkharfan.androidstudiolite.feature.createproject.LANG_KOTLIN
import com.ahmadkharfan.androidstudiolite.feature.createproject.MIN_SDK_OPTIONS
import com.ahmadkharfan.androidstudiolite.feature.createproject.TARGET_SDK_OPTIONS

@Composable
fun ConfigureStep(
    projectName: String,
    packageName: String,
    location: String,
    minSdk: String,
    targetSdk: String,
    language: String,
    buildDsl: String,
    useCpp: Boolean,
    nameError: String?,
    onNameChanged: (String) -> Unit,
    onPackageChanged: (String) -> Unit,
    onLocationChanged: (String) -> Unit,
    onMinSdkChanged: (String) -> Unit,
    onTargetSdkChanged: (String) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onBuildDslChanged: (String) -> Unit,
    onToggleCpp: (Boolean) -> Unit,
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
        LabeledSegmented(
            label = "Language",
            value = language,
            onValueChange = onLanguageChanged,
            options = listOf(
                AslSegmentedOption("Kotlin", LANG_KOTLIN),
                AslSegmentedOption("Java", LANG_JAVA),
            ),
        )
        LabeledSegmented(
            label = "Build script",
            value = buildDsl,
            onValueChange = onBuildDslChanged,
            options = listOf(
                AslSegmentedOption("Kotlin (KTS)", DSL_KTS),
                AslSegmentedOption("Groovy", DSL_GROOVY),
            ),
        )
        AslDropdown(
            label = "Minimum SDK",
            value = minSdk,
            onValueChange = onMinSdkChanged,
            options = MIN_SDK_OPTIONS.map { AslDropdownOption(it.label, it.value) },
        )
        AslDropdown(
            label = "Target SDK",
            value = targetSdk,
            onValueChange = onTargetSdkChanged,
            options = TARGET_SDK_OPTIONS.map { AslDropdownOption(it.label, it.value) },
        )
        AslSwitch(
            checked = useCpp,
            onCheckedChange = onToggleCpp,
            label = "Include C++ support (CMake / NDK)",
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
