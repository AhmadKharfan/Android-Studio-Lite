package com.ahmadkharfan.androidstudiolite.feature.editor.filetree

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslListItem
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSearchField
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.editor.EditorFileNodeUiModel
import com.ahmadkharfan.androidstudiolite.feature.editor.fileIconFor

@Composable
fun FileTreeSearchPanel(
    fileTree: List<EditorFileNodeUiModel>,
    onOpenFile: (id: String, name: String) -> Unit,
    onRevealFolder: (id: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val matches = remember(fileTree, query) { searchFileTree(fileTree, query) }
    val colors = AslTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgElevated),
    ) {
        AslSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search files and folders",
            onClear = { query = "" },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
        when {
            query.isBlank() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                ) {
                    Text(
                        text = "Type to search every file and folder in the project.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                }
            }
            matches.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                ) {
                    Text(
                        text = "No matches for \"$query\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(matches, key = { it.id }) { match ->
                        AslListItem(
                            title = match.name,
                            subtitle = match.relativePath,
                            icon = match.icon
                                ?: if (match.isDirectory) "folder" else fileIconFor(match.name),
                            divider = true,
                            onClick = {
                                if (match.isDirectory) {
                                    onRevealFolder(match.id)
                                } else {
                                    onOpenFile(match.id, match.name)
                                }
                                onClose()
                            },
                            trailing = {
                                AslIcon(
                                    name = if (match.isDirectory) "folder-open" else "arrow-up-right",
                                    size = 14.dp,
                                    tint = colors.textTertiary,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
