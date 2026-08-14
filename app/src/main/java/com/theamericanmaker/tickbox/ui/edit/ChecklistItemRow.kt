// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ui.edit

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatIndentDecrease
import androidx.compose.material.icons.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.theamericanmaker.tickbox.data.model.ChecklistIconStyle

@Composable
fun ChecklistItemRow(
    text: String,
    isChecked: Boolean,
    onTextChange: (String) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onEnterPressed: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
    focusRequester: FocusRequester,
    indentLevel: Int = 0,
    itemNumber: Int? = null,
    iconStyle: ChecklistIconStyle = ChecklistIconStyle.CHECKBOX,
    onIndent: (() -> Unit)? = null,
    onOutdent: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val textColor by animateColorAsState(
        targetValue = if (isChecked) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        label = "checklistTextColor",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = (indentLevel * 24).dp)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onOutdent != null && indentLevel > 0) {
            IconButton(onClick = onOutdent, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Filled.FormatIndentDecrease,
                    contentDescription = "Outdent",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
        if (onIndent != null && indentLevel < 1) {
            IconButton(onClick = onIndent, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Filled.FormatIndentIncrease,
                    contentDescription = "Indent",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }

        if (itemNumber != null) {
            Text(
                text = "$itemNumber.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp),
            )
        }

        ChecklistIcon(isChecked = isChecked, onCheckedChange = onCheckedChange, iconStyle = iconStyle)

        var fieldValue by remember(focusRequester) {
            mutableStateOf(TextFieldValue(text, TextRange(text.length)))
        }
        // Syncs only when the parent changes the text out from under the field —
        // a template insert, extracted text, a mode switch. Doing it on every
        // keystroke would drag the caret back to the end as you type.
        LaunchedEffect(text) {
            if (fieldValue.text != text) {
                fieldValue = TextFieldValue(text, TextRange(text.length))
            }
        }
        BasicTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                val textChanged = newValue.text != fieldValue.text
                fieldValue = newValue
                if (textChanged) onTextChange(newValue.text)
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .padding(horizontal = 4.dp),
            textStyle = TextStyle(
                color = textColor,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                capitalization = KeyboardCapitalization.Sentences,
            ),
            keyboardActions = KeyboardActions(onNext = { onEnterPressed() }),
            singleLine = true,
        )

        if (canDelete) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Delete item",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun ChecklistIcon(
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconStyle: ChecklistIconStyle,
) {
    if (iconStyle == ChecklistIconStyle.CHECKBOX) {
        Checkbox(checked = isChecked, onCheckedChange = onCheckedChange)
        return
    }

    val (checkedIcon, uncheckedIcon) = when (iconStyle) {
        ChecklistIconStyle.CIRCLE -> Icons.Filled.Circle to Icons.Filled.RadioButtonUnchecked
        ChecklistIconStyle.STAR -> Icons.Filled.Star to Icons.Filled.StarOutline
        ChecklistIconStyle.HEART -> Icons.Filled.Favorite to Icons.Filled.FavoriteBorder
        ChecklistIconStyle.SQUARE -> Icons.Filled.CheckBox to Icons.Filled.CheckBoxOutlineBlank
        ChecklistIconStyle.CHECKBOX -> Icons.Filled.CheckBox to Icons.Filled.CheckBoxOutlineBlank
    }

    IconButton(onClick = { onCheckedChange(!isChecked) }, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = if (isChecked) checkedIcon else uncheckedIcon,
            contentDescription = if (isChecked) "Checked" else "Unchecked",
            modifier = Modifier.size(22.dp),
            tint = if (isChecked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
