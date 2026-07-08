package com.example.androidstudiolite.feature.uidesigner.interaction

import com.example.androidstudiolite.feature.uidesigner.uiState.DesignerTab

sealed interface DesignerInteraction {
    data class TabSelected(val tab: DesignerTab) : DesignerInteraction
    data class IdChanged(val value: String) : DesignerInteraction
    data class TextChanged(val value: String) : DesignerInteraction
    data class LayoutWidthChanged(val value: String) : DesignerInteraction
    data class LayoutHeightChanged(val value: String) : DesignerInteraction
}
