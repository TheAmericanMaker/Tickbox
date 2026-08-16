// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ui.edit

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.theamericanmaker.tickbox.data.model.ChecklistIconStyle

// Row spacing. These four were tuned by measuring where ink actually lands, because the
// visible gaps are not the gaps in the layout: each control draws a small glyph inside a much
// larger touch box, and it is the leftover padding you see. Measured between glyph edges, the
// original row ran 25px / 68px between handle, number and tick box — lopsided, and the 68px
// read as the row being full of nothing. It is now 40px / 43px.
//
// Changing any of these changes the others, so re-measure rather than reason about it.

/** Between controls. Small, because each control's own padding supplies most of the gap. */
private val ROW_GAP = 2.dp

/** Grip box. The glyph is the six-dot indicator, which is 25px of ink against the tick's 53px. */
private val HANDLE_SIZE = 26.dp

/** Fixed and centred, so numbers stay in a column and neither side gains a hole. */
private val NUMBER_WIDTH = 22.dp

/**
 * Tick box. Below Checkbox's default, which drew a 53px glyph in a 126px box and put 36px of
 * dead space either side of it. Material3 keeps the *interactive* size independent of this, so
 * the touch target still measures ~45dp on device — the padding shrank, the tappability did not.
 */
private val TICK_SIZE = 38.dp

/** How far the tick box must travel sideways before it counts as an indent. */
private val INDENT_DRAG_STEP = 28.dp

/** Enough for a long shopping-list line to read in full; past this it scrolls. */
private const val MAX_ITEM_LINES = 4

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
    /**
     * Drag gesture for reordering, created by the caller inside its reorderable scope.
     * Null hides the handle — checked items are not draggable. The original app drew
     * this handle with no gesture attached; it only comes back now that it works.
     */
    dragHandleModifier: Modifier? = null,
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

    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // hasFocus, not isFocused: it stays true while focus is anywhere inside the row, so a
            // control revealed by focus does not disappear from under the tap that presses it.
            .onFocusChanged { isFocused = it.hasFocus }
            .padding(start = (indentLevel * 24).dp)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        if (dragHandleModifier != null) {
            Icon(
                imageVector = Icons.Filled.DragIndicator,
                contentDescription = "Reorder",
                modifier = dragHandleModifier.size(HANDLE_SIZE),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }

        if (itemNumber != null) {
            Text(
                text = "$itemNumber.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(NUMBER_WIDTH),
            )
        }

        // Indent by dragging the tick box sideways, rather than by buttons on every row.
        //
        // Buttons were tried twice and neither worked. Always visible, they spent a fixed slice of
        // every line on a feature capped at one level. Revealed on focus, they cost worse: the row
        // you tapped into reflowed from 513px of text to 356px, so the line re-wrapped under your
        // finger the moment you started editing it. A gesture occupies no width, so it cannot do
        // that to the layout.
        //
        // On the tick box specifically, not the row: the row is mostly text field, where a
        // horizontal drag already means "move the caret".
        val haptics = LocalHapticFeedback.current
        val indentStepPx = with(LocalDensity.current) { INDENT_DRAG_STEP.toPx() }
        var dragTravel by remember { mutableFloatStateOf(0f) }
        ChecklistIcon(
            isChecked = isChecked,
            onCheckedChange = onCheckedChange,
            iconStyle = iconStyle,
            modifier = Modifier.draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    dragTravel += delta
                    // The ViewModel clamps to one level, so overshooting is harmless; the haptic
                    // is what tells you it took, since there is nothing to see mid-gesture.
                    if (dragTravel >= indentStepPx) {
                        dragTravel = 0f
                        onIndent?.invoke()
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    } else if (dragTravel <= -indentStepPx) {
                        dragTravel = 0f
                        onOutdent?.invoke()
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
                onDragStopped = { dragTravel = 0f },
            ),
        )

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
                // A wrapping field means the IME offers a newline rather than a Next action, so
                // Enter arrives here as text. Intercept it: a newline inside a checklist item is
                // never what was meant — the next item is.
                if (newValue.text.contains('\n')) {
                    val withoutBreak = newValue.text.replace("\n", "")
                    if (withoutBreak != fieldValue.text) {
                        fieldValue = TextFieldValue(withoutBreak, TextRange(withoutBreak.length))
                        onTextChange(withoutBreak)
                    }
                    onEnterPressed()
                    return@BasicTextField
                }
                val textChanged = newValue.text != fieldValue.text
                fieldValue = newValue
                if (textChanged) onTextChange(newValue.text)
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
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
            // Wraps instead of scrolling. A single-line field keeps the caret in view, so a long
            // item showed its *tail* — a shopping list read as "...for both modes" and
            // "...qualitative notes", with the start of every line off the left edge.
            maxLines = MAX_ITEM_LINES,
        )

        // Visible only on the focused row, but always composed and always measured. Adding and
        // removing it changed the row's width, and a control that widens the text as it appears
        // is what made the line re-wrap under the finger that tapped into it. Hidden here means
        // transparent, disabled and out of the semantics tree — not absent from the layout.
        if (canDelete) {
            IconButton(
                onClick = onDelete,
                enabled = isFocused,
                modifier = Modifier
                    .size(32.dp)
                    .alpha(if (isFocused) 1f else 0f)
                    .then(if (isFocused) Modifier else Modifier.clearAndSetSemantics {}),
            ) {
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
    modifier: Modifier = Modifier,
) {
    if (iconStyle == ChecklistIconStyle.CHECKBOX) {
        Checkbox(checked = isChecked, onCheckedChange = onCheckedChange, modifier = modifier.size(TICK_SIZE))
        return
    }

    val (checkedIcon, uncheckedIcon) = when (iconStyle) {
        ChecklistIconStyle.CIRCLE -> Icons.Filled.Circle to Icons.Filled.RadioButtonUnchecked
        ChecklistIconStyle.STAR -> Icons.Filled.Star to Icons.Filled.StarOutline
        ChecklistIconStyle.HEART -> Icons.Filled.Favorite to Icons.Filled.FavoriteBorder
        ChecklistIconStyle.SQUARE -> Icons.Filled.CheckBox to Icons.Filled.CheckBoxOutlineBlank
        ChecklistIconStyle.CHECKBOX -> Icons.Filled.CheckBox to Icons.Filled.CheckBoxOutlineBlank
    }

    IconButton(onClick = { onCheckedChange(!isChecked) }, modifier = modifier.size(TICK_SIZE)) {
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
