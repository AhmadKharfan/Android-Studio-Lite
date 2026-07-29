package com.ahmadkharfan.androidstudiolite.feature.editor.git.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmadkharfan.androidstudiolite.core.format.middleEllipsis
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslEmptyState
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslLinearProgress
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTypography
import com.ahmadkharfan.androidstudiolite.feature.editor.git.blameGutterText
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.ahmadkharfan.androidstudiolite.feature.git.R

@Composable
fun GitBlameRoute(
    projectId: String,
    path: String,
    onBack: () -> Unit,
    viewModel: GitBlameViewModel = koinViewModel { parametersOf(projectId, path) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { AslTopAppBar(stringResource(R.string.git_blame_title), subtitle = state.path.ifBlank { path }.middleEllipsis(), onBack = onBack, applyStatusBarInset = true) }) { padding ->
        when {
            state.loading -> AslLinearProgress(label = stringResource(R.string.git_blame_computing), modifier = Modifier.padding(padding).padding(16.dp))
            state.error != null -> AslEmptyState(
                title = stringResource(R.string.git_blame_error),
                icon = "triangle-alert",
                subtitle = state.error,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            else -> {
                val scroll = rememberScrollState()
                LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                    items(state.lines, key = { it.lineNo }) { line ->
                        Row(Modifier.horizontalScroll(scroll).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text(blameGutterText(line.lineNo, line.shortId, line.authorName), fontFamily = FontFamily.Monospace, style = AslTypography.bodySmall)
                            Text(line.lineText, fontFamily = FontFamily.Monospace, style = AslTypography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
