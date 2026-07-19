package com.ahmadkharfan.androidstudiolite.feature.editor.assets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslListItem
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslSkeleton
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslSkeletonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslToolWindowPanel
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.rememberAslToolWindowWidth
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AssetsRoute(
    projectId: String,
    onClose: () -> Unit,
    onOpenFile: (path: String, name: String) -> Unit,
) {
    val viewModel: AssetsViewModel = koinViewModel { parametersOf(projectId) }
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    AssetsScreen(
        uiState = uiState,
        interactionListener = viewModel,
        onClose = onClose,
        onOpenFile = onOpenFile,
    )
}

@Composable
private fun AssetsScreen(
    uiState: AssetsUiState,
    interactionListener: AssetsInteractionListener,
    onClose: () -> Unit,
    onOpenFile: (path: String, name: String) -> Unit,
) {
    AslToolWindowPanel(title = "Assets", width = rememberAslToolWindowWidth(), onClose = onClose, scrollable = false) {
        when {
            uiState.loading -> AslSkeleton(variant = AslSkeletonVariant.List, rows = 6)
            uiState.selectedAsset != null -> AssetDetailView(
                asset = uiState.selectedAsset,
                onBack = { interactionListener.onDismissAssetDetail() },
                onOpenInEditor = { onOpenFile(uiState.selectedAsset.absolutePath, uiState.selectedAsset.name) },
            )
            uiState.assets.isEmpty() -> EmptyAssets()
            else -> AssetListView(uiState = uiState, interactionListener = interactionListener)
        }
    }
}

@Composable
private fun AssetListView(
    uiState: AssetsUiState,
    interactionListener: AssetsInteractionListener,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(uiState.assets, key = { it.absolutePath }) { asset ->
            AslListItem(
                title = asset.name,
                subtitle = asset.subtitle,
                leading = { AssetThumbnail(entry = asset) },
                divider = true,
                onClick = { interactionListener.onSelectAsset(asset) },
            )
        }
    }
}

@Composable
private fun AssetDetailView(
    asset: AssetEntry,
    onBack: () -> Unit,
    onOpenInEditor: () -> Unit,
) {
    val colors = AslTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AslIconButton(icon = "arrow-left", contentDescription = "Back", onClick = onBack, size = 32.dp, iconSize = 16.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(text = asset.name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                Text(text = asset.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
            }
        }
        AssetDetailPreview(asset = asset)
        if (asset.kind != AssetKind.RasterImage) {
            AslButton(
                label = "Open in editor",
                icon = "file-code",
                onClick = onOpenInEditor,
                variant = AslButtonVariant.Secondary,
                fullWidth = true,
            )
        }
    }
}

@Composable
private fun AssetDetailPreview(asset: AssetEntry) {
    val colors = AslTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 320.dp)
            .background(colors.bgSunken, AslShape.lg)
            .border(1.dp, colors.borderSubtle, AslShape.lg)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (asset.kind) {
            AssetKind.RasterImage -> AssetDetailImage(asset.absolutePath)
            AssetKind.XmlDrawable -> AssetDetailXmlDrawable(asset.absolutePath)
            AssetKind.Font -> AssetDetailFont(asset.absolutePath)
            AssetKind.Raw -> AssetDetailText(asset.absolutePath)
        }
    }
}

@Composable
private fun AssetDetailImage(path: String) {
    var bitmap by remember(path) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            AssetPreview.decodeThumbnail(path, maxSidePx = 512)?.asImageBitmap()
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun AssetDetailFont(path: String) {
    var typeface by remember(path) { mutableStateOf<android.graphics.Typeface?>(null) }
    LaunchedEffect(path) {
        typeface = withContext(Dispatchers.IO) { AssetPreview.loadTypeface(path) }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "The quick brown fox",
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = typeface?.let { FontFamily(it) },
            color = AslTheme.colors.textPrimary,
        )
        Text(
            text = "0123456789 AaBbCc",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = typeface?.let { FontFamily(it) },
            color = AslTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun AssetDetailXmlDrawable(path: String) {
    var tint by remember(path) { mutableStateOf<androidx.compose.ui.graphics.Color?>(null) }
    LaunchedEffect(path) {
        tint = withContext(Dispatchers.IO) { AssetPreview.xmlDrawableTint(path) }
    }
    if (tint != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp)
                .background(tint!!.copy(alpha = 0.85f), AslShape.md),
        )
    } else {
        AssetDetailText(path)
    }
}

@Composable
private fun AssetDetailText(path: String) {
    var text by remember(path) { mutableStateOf<String?>(null) }
    LaunchedEffect(path) {
        text = withContext(Dispatchers.IO) { AssetPreview.readText(path) }
    }
    Text(
        text = text ?: "Loading…",
        style = AslCode.codeBody.copy(fontSize = 11.sp, lineHeight = 15.sp),
        color = AslTheme.colors.textSecondary,
        modifier = Modifier.fillMaxWidth(),
    )
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
