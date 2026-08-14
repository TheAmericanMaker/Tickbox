// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ui.edit.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.theamericanmaker.tickbox.data.model.NoteType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePickerBottomSheet(
    onDismiss: () -> Unit,
    onTemplateSelected: (NoteTemplate) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Text(
            "Choose a template",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            items(TemplateProvider.templates) { template ->
                ListItem(
                    headlineContent = { Text(template.name) },
                    supportingContent = {
                        val typeLabel = if (template.type == NoteType.CHECKLIST) "Checklist" else "Note"
                        Text("$typeLabel · ${template.items.size} items")
                    },
                    leadingContent = {
                        Icon(template.icon, contentDescription = template.name)
                    },
                    modifier = Modifier.clickable { onTemplateSelected(template) }
                )
            }
        }
    }
}
