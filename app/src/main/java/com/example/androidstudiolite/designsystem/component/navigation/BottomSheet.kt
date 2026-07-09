package com.example.androidstudiolite.designsystem.component.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.designsystem.theme.AslShape
import com.example.androidstudiolite.designsystem.theme.AslTheme

enum class AslBottomSheetSize { Peek, Half, Full }

/** BottomSheet.jsx — modal bottom sheet with drag pill, optional title, scrim dismiss. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AslBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    size: AslBottomSheetSize = AslBottomSheetSize.Half,
    content: @Composable () -> Unit,
) {
    val colors = AslTheme.colors
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = size != AslBottomSheetSize.Peek,
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (size == AslBottomSheetSize.Full) 600.dp else 0.dp),
        ) {
            androidx.compose.foundation.layout.Column {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    content()
                }
            }
        }
    }
}
