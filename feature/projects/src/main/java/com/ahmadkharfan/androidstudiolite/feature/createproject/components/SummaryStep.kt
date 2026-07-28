package com.ahmadkharfan.androidstudiolite.feature.createproject.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslListItem
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslLinearProgress
import com.ahmadkharfan.androidstudiolite.designsystem.modifier.aslCard
import com.ahmadkharfan.androidstudiolite.feature.createproject.CreateProjectUiState
import com.ahmadkharfan.androidstudiolite.feature.projects.R

@Composable
fun SummaryStep(uiState: CreateProjectUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aslCard(),
        ) {
            AslListItem(
                title = stringResource(R.string.projects_summary_template),
                subtitle = uiState.selectedTemplate?.name,
                icon = "layout-template",
            )
            AslListItem(
                title = uiState.projectName,
                subtitle = uiState.packageName,
                icon = "smartphone",
            )
            AslListItem(
                title = stringResource(R.string.projects_summary_location),
                subtitle = uiState.location,
                icon = "folder",
            )
            AslListItem(
                title = stringResource(R.string.projects_summary_language),
                subtitle = uiState.languageLabel,
                icon = "code",
            )
            AslListItem(
                title = stringResource(R.string.projects_summary_sdk),
                subtitle = stringResource(R.string.projects_summary_min_sdk, uiState.minSdkLabel),
                icon = "cpu",
                divider = false,
            )
        }
        if (uiState.creating) {
            AslLinearProgress(
                label = stringResource(R.string.projects_creating),
                detail = stringResource(R.string.projects_creating_detail),
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
