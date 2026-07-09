package com.example.androidstudiolite.feature.folderpicker
import androidx.compose.runtime.Immutable

import com.example.androidstudiolite.designsystem.component.content.AslFileTreeNode

@Immutable
data class FolderPickerUiState(
    val breadcrumb: List<String> = emptyList(),
    val items: List<AslFileTreeNode> = emptyList(),
    val expandedIds: Set<String> = emptySet(),
    val selectedId: String? = null,
    val selectedPath: String? = null,
)
