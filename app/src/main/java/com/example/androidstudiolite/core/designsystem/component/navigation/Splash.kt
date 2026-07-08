package com.example.androidstudiolite.core.designsystem.component.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.example.androidstudiolite.core.designsystem.theme.AslCode
import com.example.androidstudiolite.core.designsystem.theme.AslTheme

/** Splash.jsx — logo mark + wordmark, minimal, fills its container. */
@Composable
fun AslSplash(
    modifier: Modifier = Modifier,
    tagline: String = "Simply, an IDE for Android",
    version: String? = null,
) {
    val colors = AslTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgBase),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFF1E1E2E), RoundedCornerShape(20.dp))
                    .border(1.dp, colors.borderDefault, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "{ }",
                    color = Color(0xFF34D399),
                    fontFamily = AslCode.codeBody.fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                )
            }
            Spacer(Modifier.height(20.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = buildAnnotatedWordmark(),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        if (version != null) {
            Text(
                text = version,
                style = AslCode.codeTiny,
                color = colors.textTertiary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun buildAnnotatedWordmark() = buildAnnotatedString {
    val colors = AslTheme.colors
    withStyle(SpanStyle(color = colors.textPrimary)) { append("Android Studio ") }
    withStyle(SpanStyle(color = colors.textSecondary, fontWeight = FontWeight.Normal)) { append("Lite") }
}
