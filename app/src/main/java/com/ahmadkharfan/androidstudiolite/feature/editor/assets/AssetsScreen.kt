package com.ahmadkharfan.androidstudiolite.feature.editor.assets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslListItem
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslSkeleton
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslSkeletonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslToolWindowPanel
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.rememberAslToolWindowWidth
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AssetsRoute(projectId: String, onClose: () -> Unit) {
    val viewModel: AssetsViewModel = koinViewModel { parametersOf(projectId) }
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    AssetsScreen(uiState = uiState, onClose = onClose)
}

@Composable
private fun AssetsScreen(uiState: AssetsUiState, onClose: () -> Unit) {
    AslToolWindowPanel(title = "Assets", width = rememberAslToolWindowWidth(), onClose = onClose, scrollable = false) {
        when {
            uiState.loading -> AslSkeleton(variant = AslSkeletonVariant.List, rows = 6)
            uiState.assets.isEmpty() -> EmptyAssets()
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.assets, key = { it.absolutePath }) { asset ->
                    AslListItem(title = asset.name, subtitle = asset.subtitle, icon = asset.icon)
                }
            }
        }
    }
}

@Composable
private fun EmptyAssets() {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "No resources or assets found in this project.",
            style = MaterialTheme.typography.bodySmall,
            color = AslTheme.colors.textTertiary,
        )
    }
}
