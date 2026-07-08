package com.example.androidstudiolite.feature.uidesigner.uiState

import androidx.compose.runtime.Immutable

enum class DesignerTab { Palette, Canvas, Properties }

@Immutable
data class PaletteWidget(val id: String, val icon: String, val label: String)

@Immutable
data class WidgetProperties(
    val id: String,
    val text: String,
    val layoutWidth: String,
    val layoutHeight: String,
)

val DESIGNER_PALETTE = listOf(
    PaletteWidget("button", "square", "Button"),
    PaletteWidget("text_view", "type", "TextView"),
    PaletteWidget("edit_text", "text-cursor-input", "EditText"),
    PaletteWidget("image_view", "image", "ImageView"),
    PaletteWidget("recycler_view", "list", "RecyclerView"),
    PaletteWidget("switch", "toggle-left", "Switch"),
)

@Immutable
data class DesignerUiState(
    val fileName: String = "activity_main.xml",
    val activeTab: DesignerTab = DesignerTab.Canvas,
    val palette: List<PaletteWidget> = DESIGNER_PALETTE,
    val properties: WidgetProperties = WidgetProperties(
        id = "@+id/submit_button",
        text = "Submit",
        layoutWidth = "match_parent",
        layoutHeight = "wrap_content",
    ),
)
