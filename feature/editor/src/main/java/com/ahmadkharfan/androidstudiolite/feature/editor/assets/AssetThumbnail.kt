package com.ahmadkharfan.androidstudiolite.feature.editor.assets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AssetThumbnail(
    entry: AssetEntry,
    modifier: Modifier = Modifier,
    sizeDp: androidx.compose.ui.unit.Dp = 44.dp,
) {
    val colors = AslTheme.colors
    Box(
        modifier = modifier
            .size(sizeDp)
            .background(colors.bgSunken, AslShape.md)
            .border(1.dp, colors.borderSubtle, AslShape.md),
        contentAlignment = Alignment.Center,
    ) {
        when (entry.kind) {
            AssetKind.RasterImage -> RasterThumbnail(entry.absolutePath)
            AssetKind.XmlDrawable -> XmlDrawableThumbnail(entry.absolutePath)
            AssetKind.Font -> FontThumbnail(entry.absolutePath)
            AssetKind.Raw -> FallbackIcon(icon = "file", tint = colors.textTertiary)
        }
    }
}

@Composable
private fun RasterThumbnail(path: String) {
    var bitmap by remember(path) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            AssetPreview.decodeThumbnail(path)?.asImageBitmap()
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    } else {
        FallbackIcon(icon = "image")
    }
}

@Composable
private fun XmlDrawableThumbnail(path: String) {
    var tint by remember(path) { mutableStateOf<Color?>(null) }
    LaunchedEffect(path) {
        tint = withContext(Dispatchers.IO) { AssetPreview.xmlDrawableTint(path) }
    }
    if (tint != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tint!!.copy(alpha = 0.85f)),
        )
    } else {
        FallbackIcon(icon = "shapes")
    }
}

@Composable
private fun FontThumbnail(path: String) {
    var typeface by remember(path) { mutableStateOf<android.graphics.Typeface?>(null) }
    LaunchedEffect(path) {
        typeface = withContext(Dispatchers.IO) { AssetPreview.loadTypeface(path) }
    }
    Text(
        text = "Aa",
        style = MaterialTheme.typography.titleMedium,
        fontFamily = typeface?.let { FontFamily(it) },
        color = AslTheme.colors.textPrimary,
    )
}

@Composable
private fun FallbackIcon(icon: String, tint: Color = AslTheme.colors.textTertiary) {
    AslIcon(name = icon, size = 18.dp, tint = tint)
}
