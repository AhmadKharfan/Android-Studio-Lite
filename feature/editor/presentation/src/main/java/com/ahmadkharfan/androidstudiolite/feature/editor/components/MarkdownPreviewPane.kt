package com.ahmadkharfan.androidstudiolite.feature.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslMarkdownText
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

@Composable
fun MarkdownPreviewPane(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val colors = AslTheme.colors
    val clipboard = LocalClipboardManager.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgBase),
    ) {
        AslMarkdownText(
            markdown = markdown,
            onCopyCode = { clipboard.setText(AnnotatedString(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
