package com.example.androidstudiolite.feature.folderpicker.uiState

import com.example.androidstudiolite.core.designsystem.component.content.AslFileTreeNode

data class FolderPickerUiState(
    val breadcrumb: List<String> = emptyList(),
    val items: List<AslFileTreeNode> = emptyList(),
    val expandedIds: Set<String> = emptySet(),
    val selectedId: String? = null,
    val selectedPath: String? = null,
)
