package com.ahmadkharfan.androidstudiolite.designsystem.component.inputs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** SearchField.jsx — search leading icon, clear trailing button, optional filter-chip row below. */
@Composable
fun AslSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    onClear: () -> Unit = {},
    chips: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AslTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder,
            leadingIcon = "search",
            trailingIcon = if (value.isNotEmpty()) "x" else null,
            onTrailingClick = {
                onClear()
                onValueChange("")
            },
        )
        if (chips != null) {
            Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips()
            }
        }
    }
}
