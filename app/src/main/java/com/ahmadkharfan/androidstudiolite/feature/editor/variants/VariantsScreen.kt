package com.ahmadkharfan.androidstudiolite.feature.editor.variants
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslDropdown
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslDropdownOption
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslToolWindowPanel
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.rememberAslToolWindowWidth
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

private val VARIANT_OPTIONS = listOf(
    AslDropdownOption("debug", "debug"),
    AslDropdownOption("release", "release"),
)

/**
 * Build-variant picker. The selected variant is owned by the editor (it drives Run), so this panel is
 * stateless: it reflects [selectedVariant] and reports changes through [onSelectVariant].
 */
@Composable
fun VariantsRoute(
    selectedVariant: String,
    onSelectVariant: (String) -> Unit,
    onClose: () -> Unit,
    module: String = "app",
) {
    val colors = AslTheme.colors
    AslToolWindowPanel(title = "Build Variants", width = rememberAslToolWindowWidth(), onClose = onClose) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            AslDropdown(
                label = module,
                value = selectedVariant,
                options = VARIANT_OPTIONS,
                onValueChange = onSelectVariant,
            )
            Text(
                text = if (selectedVariant == "debug") {
                    "Debug builds keep debugging symbols and are not optimized. Run installs this variant."
                } else {
                    "Release builds are minified and optimized for distribution. Run installs this variant (release signing required)."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
