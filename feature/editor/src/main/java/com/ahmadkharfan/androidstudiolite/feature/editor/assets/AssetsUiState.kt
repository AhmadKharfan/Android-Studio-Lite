package com.ahmadkharfan.androidstudiolite.feature.editor.assets

import androidx.compose.runtime.Immutable

@Immutable
data class AssetEntry(
    val name: String,
    val subtitle: String,
    val absolutePath: String,
    val kind: AssetKind,
)

@Immutable
data class AssetsUiState(
    val loading: Boolean = true,
    val assets: List<AssetEntry> = emptyList(),
    val selectedAsset: AssetEntry? = null,
)
