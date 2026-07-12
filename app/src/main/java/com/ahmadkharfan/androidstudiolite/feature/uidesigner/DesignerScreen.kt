package com.ahmadkharfan.androidstudiolite.feature.uidesigner
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslIconButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSegmentedButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSegmentedOption
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.uidesigner.DesignerTab
import com.ahmadkharfan.androidstudiolite.feature.uidesigner.DesignerUiState
import com.ahmadkharfan.androidstudiolite.feature.uidesigner.PaletteWidget
import com.ahmadkharfan.androidstudiolite.feature.uidesigner.WidgetProperties
import com.ahmadkharfan.androidstudiolite.feature.uidesigner.DesignerViewModel

private val TABLET_BREAKPOINT = 600.dp

@Composable
fun DesignerRoute(onBack: () -> Unit, viewModel: DesignerViewModel = koinViewModel()) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    DesignerScreen(uiState = uiState, interactionListener = viewModel, onBack = onBack)
}

@Composable
private fun DesignerScreen(
    uiState: DesignerUiState,
    interactionListener: DesignerInteractionListener,
    onBack: () -> Unit,
) {
    val colors = AslTheme.colors
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DesignerToolbar(fileName = uiState.fileName, onBack = onBack)
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (maxWidth >= TABLET_BREAKPOINT) {
                    DesignerTabletLayout(uiState = uiState, interactionListener = interactionListener, colors = colors)
                } else {
                    DesignerPhoneLayout(uiState = uiState, interactionListener = interactionListener)
                }
            }
        }
    }
}

@Composable
private fun DesignerTabletLayout(
    uiState: DesignerUiState,
    interactionListener: DesignerInteractionListener,
    colors: AslColorScheme,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(190.dp)
                .fillMaxHeight()
                .background(colors.bgElevated),
        ) {
            PanelHeader("Palette")
            HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
            Palette(items = uiState.palette)
        }
        Canvas(text = uiState.properties.text, modifier = Modifier.weight(1f).fillMaxHeight())
        Column(
            modifier = Modifier
                .width(230.dp)
                .fillMaxHeight()
                .background(colors.bgElevated)
                .verticalScroll(rememberScrollState()),
        ) {
            PanelHeader("Properties · Button")
            HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
            Properties(properties = uiState.properties, interactionListener = interactionListener)
        }
    }
}

@Composable
private fun DesignerPhoneLayout(
    uiState: DesignerUiState,
    interactionListener: DesignerInteractionListener,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AslSegmentedButton(
            value = uiState.activeTab.name.lowercase(),
            onValueChange = { interactionListener.onTabSelected(DesignerTab.valueOf(it.replaceFirstChar(Char::uppercase))) },
            fullWidth = true,
            options = listOf(
                AslSegmentedOption("Palette", "palette"),
                AslSegmentedOption("Canvas", "canvas"),
                AslSegmentedOption("Props", "properties"),
            ),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
        when (uiState.activeTab) {
            DesignerTab.Palette -> Palette(items = uiState.palette, modifier = Modifier.fillMaxSize())
            DesignerTab.Canvas -> Canvas(text = uiState.properties.text, modifier = Modifier.fillMaxSize())
            DesignerTab.Properties -> Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Properties(properties = uiState.properties, interactionListener = interactionListener)
            }
        }
    }
}

@Composable
private fun DesignerToolbar(fileName: String, onBack: () -> Unit) {
    val colors = AslTheme.colors
    Column(modifier = Modifier.background(colors.bgElevated)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AslIconButton(icon = "arrow-left", contentDescription = "Back", onClick = onBack)
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            AslIconButton(icon = "undo-2", contentDescription = "Undo", onClick = {})
            AslIconButton(icon = "redo-2", contentDescription = "Redo", onClick = {})
            AslIconButton(icon = "eye", contentDescription = "Preview", onClick = {})
            AslIconButton(icon = "save", contentDescription = "Save", onClick = {}, variant = AslIconButtonVariant.Filled)
        }
        HorizontalDivider(color = colors.borderDefault, thickness = 1.dp)
    }
}

@Composable
private fun PanelHeader(title: String) {
    val colors = AslTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
    }
}

@Composable
private fun Palette(items: List<PaletteWidget>, modifier: Modifier = Modifier) {
    val colors = AslTheme.colors
    Column(modifier = modifier) {
        items.forEach { widget ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AslIcon(name = widget.icon, size = 16.dp, tint = colors.textSecondary)
                Text(
                    text = widget.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                AslIcon(name = "grip-vertical", size = 14.dp, tint = colors.textTertiary)
            }
        }
    }
}

@Composable
private fun Canvas(text: String, modifier: Modifier = Modifier) {
    val colors = AslTheme.colors
    Box(
        modifier = modifier.background(colors.bgSunken),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(150.dp)
                .height(260.dp)
                .background(colors.editorCanvas, AslShape.md)
                .border(1.dp, colors.borderStrong, AslShape.md)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(colors.surfaceContainerHigh, AslShape.xs),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .border(2.dp, colors.accentPrimary, AslShape.xs),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = text, style = MaterialTheme.typography.labelSmall, color = colors.accentPrimary)
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(colors.surfaceContainerHigh, AslShape.full),
            )
        }
    }
}

@Composable
private fun Properties(properties: WidgetProperties, interactionListener: DesignerInteractionListener) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AslTextField(label = "id", value = properties.id, onValueChange = { interactionListener.onIdChanged(it) })
        AslTextField(label = "text", value = properties.text, onValueChange = { interactionListener.onTextChanged(it) })
        AslTextField(label = "layout_width", value = properties.layoutWidth, onValueChange = { interactionListener.onLayoutWidthChanged(it) })
        AslTextField(label = "layout_height", value = properties.layoutHeight, onValueChange = { interactionListener.onLayoutHeightChanged(it) })
    }
}
