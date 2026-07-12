package com.ahmadkharfan.androidstudiolite.designsystem.component.content
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.SignatureHelpResult
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslElevation
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.aslBordered
@Composable
fun AslSignatureHelpPopup(
    help: SignatureHelpResult,
    modifier: Modifier = Modifier,
) {
    val colors = AslTheme.colors
    val scroll = rememberScrollState()
    val active = help.parameters.getOrNull(help.activeParameter)
    val signature = buildAnnotatedString {
        if (active == null) {
            append(help.signatureLabel)
        } else {
            append(help.signatureLabel.substring(0, active.start))
            withStyle(SpanStyle(color = colors.accentPrimary, fontWeight = FontWeight.SemiBold)) {
                append(help.signatureLabel.substring(active.start, active.end))
            }
            append(help.signatureLabel.substring(active.end))
        }
    }
    Column(
        modifier = modifier
            .shadow(AslElevation.overlay, AslShape.md)
            .background(colors.surface, AslShape.md)
            .aslBordered(AslShape.md)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = signature,
            style = AslCode.codeSmall,
            color = colors.textPrimary,
            modifier = Modifier.horizontalScroll(scroll),
        )
    }
}
