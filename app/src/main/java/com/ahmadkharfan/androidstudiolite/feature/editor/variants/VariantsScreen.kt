package com.ahmadkharfan.androidstudiolite.feature.editor.variants
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslDropdown
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslDropdownOption
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslToolWindowPanel
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.rememberAslToolWindowWidth
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.editor.variants.VariantsInteractionListener
import com.ahmadkharfan.androidstudiolite.feature.editor.variants.VariantsUiState
import com.ahmadkharfan.androidstudiolite.feature.editor.variants.VariantsViewModel

private val VARIANT_OPTIONS = listOf(
    AslDropdownOption("debug", "debug"),
    AslDropdownOption("release", "release"),
)

@Composable
fun VariantsRoute(onClose: () -> Unit, viewModel: VariantsViewModel = koinViewModel()) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    VariantsScreen(uiState = uiState, interactionListener = viewModel, onClose = onClose)
}

@Composable
private fun VariantsScreen(
    uiState: VariantsUiState,
    interactionListener: VariantsInteractionListener,
    onClose: () -> Unit,
) {
    val colors = AslTheme.colors
    AslToolWindowPanel(title = "Build Variants", width = rememberAslToolWindowWidth(), onClose = onClose) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            AslDropdown(
                label = uiState.module,
                value = uiState.selectedVariant,
                options = VARIANT_OPTIONS,
                onValueChange = { interactionListener.onSelectVariant(it) },
            )
            Text(
                text = if (uiState.selectedVariant == "debug") {
                    "Debug builds keep debugging symbols and are not optimized."
                } else {
                    "Release builds are minified and optimized for distribution."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
