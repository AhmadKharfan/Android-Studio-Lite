package com.ahmadkharfan.androidstudiolite.feature.createproject.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslTemplateCard
import com.ahmadkharfan.androidstudiolite.feature.createproject.CreateProjectTemplateUiModel

@Composable
fun TemplateStep(
    templates: List<CreateProjectTemplateUiModel>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()


    val scrolled = remember { AtomicBoolean(false) }
    LaunchedEffect(templates, selectedId) {
        if (templates.isNotEmpty() && scrolled.compareAndSet(false, true)) {
            val index = templates.indexOfFirst { it.id == selectedId }
            if (index > 0) gridState.scrollToItem(index)
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(templates, key = { it.id }) { template ->
            AslTemplateCard(
                name = template.name,
                thumbnail = templateThumbnailRes(template.thumbnail),
                selected = template.id == selectedId,
                onClick = { onSelect(template.id) },
            )
        }
    }
}
