package com.example.androidstudiolite.feature.createproject.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.core.designsystem.component.content.AslListItem
import com.example.androidstudiolite.core.designsystem.component.feedback.AslLinearProgress
import com.example.androidstudiolite.core.designsystem.theme.AslShape
import com.example.androidstudiolite.core.designsystem.theme.AslTheme
import com.example.androidstudiolite.feature.createproject.uiState.CreateProjectUiState

@Composable
fun SummaryStep(uiState: CreateProjectUiState, modifier: Modifier = Modifier) {
    val colors = AslTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, AslShape.lg)
                .border(1.dp, colors.borderDefault, AslShape.lg),
        ) {
            AslListItem(
                title = "Template",
                subtitle = uiState.selectedTemplate?.name,
                icon = "layout-template",
            )
            AslListItem(
                title = uiState.projectName,
                subtitle = uiState.packageName,
                icon = "smartphone",
            )
            AslListItem(
                title = "Location",
                subtitle = uiState.location,
                icon = "folder",
            )
            AslListItem(
                title = "Minimum SDK",
                subtitle = uiState.minSdkLabel,
                icon = "cpu",
                divider = false,
            )
        }
        if (uiState.creating) {
            AslLinearProgress(
                label = "Creating project…",
                detail = "writing Gradle wrapper",
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
