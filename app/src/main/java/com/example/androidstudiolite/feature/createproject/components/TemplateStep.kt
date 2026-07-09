package com.example.androidstudiolite.feature.createproject.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.designsystem.component.content.AslTemplateCard
import com.example.androidstudiolite.feature.createproject.CreateProjectTemplateUiModel

@Composable
fun TemplateStep(
    templates: List<CreateProjectTemplateUiModel>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
    ) {
        items(templates, key = { it.id }) { template ->
            AslTemplateCard(
                name = template.name,
                description = template.description,
                icon = template.icon,
                chips = template.tags,
                selected = template.id == selectedId,
                onClick = { onSelect(template.id) },
            )
        }
    }
}
