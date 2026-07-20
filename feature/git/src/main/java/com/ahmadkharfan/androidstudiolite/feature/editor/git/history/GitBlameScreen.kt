package com.ahmadkharfan.androidstudiolite.feature.editor.git.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslEmptyState
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslLinearProgress
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.ahmadkharfan.androidstudiolite.feature.git.middleEllipsis

@Composable
fun GitBlameRoute(
    projectId: String,
    path: String,
    onBack: () -> Unit,
    viewModel: GitBlameViewModel = koinViewModel { parametersOf(projectId, path) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { AslTopAppBar("Blame", subtitle = state.path.ifBlank { path }.middleEllipsis(), onBack = onBack, applyStatusBarInset = true) }) { padding ->
        when {
            state.loading -> AslLinearProgress(label = "Computing blame", modifier = Modifier.padding(padding).padding(16.dp))
            state.error != null -> AslEmptyState(
                title = "Couldn't show blame",
                icon = "triangle-alert",
                subtitle = state.error,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            else -> {
                val scroll = rememberScrollState()
                LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                    items(state.lines, key = { it.lineNo }) { line ->
                        Row(Modifier.horizontalScroll(scroll).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("${line.lineNo.toString().padStart(4)}  ${line.shortId.padEnd(7)}  ${line.authorName.take(12).padEnd(12)}  ", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            Text(line.lineText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
