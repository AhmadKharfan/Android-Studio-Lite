package com.ahmadkharfan.androidstudiolite.feature.editor.variants
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslDropdown
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslDropdownOption
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslToolWindowPanel
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.rememberAslToolWindowWidth
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTypography
import com.ahmadkharfan.androidstudiolite.feature.editor.R

@Composable
fun VariantsRoute(
    selectedVariant: String,
    onSelectVariant: (String) -> Unit,
    onClose: () -> Unit,
    module: String = "app",
    variants: List<String> = emptyList(),
) {
    val colors = AslTheme.colors
    val options = variants.map { AslDropdownOption(it, it) }
    val isDebugish = selectedVariant.contains("debug", ignoreCase = true) &&
        !selectedVariant.contains("release", ignoreCase = true)
    AslToolWindowPanel(title = stringResource(R.string.editor_variants_title), width = rememberAslToolWindowWidth(), onClose = onClose) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            AslDropdown(
                label = module,
                value = selectedVariant,
                options = options,
                onValueChange = onSelectVariant,
            )
            Text(
                text = if (isDebugish) {
                    stringResource(R.string.editor_variants_debug_hint)
                } else {
                    stringResource(R.string.editor_variants_release_hint)
                },
                style = AslTypography.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
