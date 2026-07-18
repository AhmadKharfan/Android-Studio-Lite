package com.ahmadkharfan.androidstudiolite.feature.editor.assets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslListItem
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslToolWindowPanel
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.rememberAslToolWindowWidth

private data class AssetEntry(val name: String, val subtitle: String)

private val ASSETS = listOf(
    AssetEntry("ic_launcher.webp", "mipmap-anydpi-v26"),
    AssetEntry("ic_launcher_round.webp", "mipmap-anydpi-v26"),
    AssetEntry("ic_launcher_foreground.xml", "drawable"),
    AssetEntry("ic_launcher_background.xml", "drawable"),
    AssetEntry("colors.xml", "values"),
    AssetEntry("themes.xml", "values"),
)

@Composable
fun AssetsRoute(onClose: () -> Unit) {
    AssetsScreen(onClose = onClose)
}

@Composable
private fun AssetsScreen(onClose: () -> Unit) {
    AslToolWindowPanel(title = "Assets", width = rememberAslToolWindowWidth(), onClose = onClose, scrollable = false) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(ASSETS) { asset ->
                AslListItem(title = asset.name, subtitle = asset.subtitle, icon = "image")
            }
        }
    }
}
