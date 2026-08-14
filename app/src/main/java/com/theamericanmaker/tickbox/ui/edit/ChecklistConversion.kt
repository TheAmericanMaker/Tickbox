// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ui.edit

/**
 * Converting between a text note and a checklist, as pure functions.
 *
 * Pulled out of the ViewModel because this is the fiddliest logic in the editor and
 * the easiest thing to break: it runs on real user content, in both directions, and a
 * mistake silently loses text.
 */
object ChecklistConversion {

    /** One checklist item per non-blank line. Never returns an empty list. */
    fun textToItems(content: String): List<ChecklistItemUiState> =
        content.lines()
            .filter { it.isNotBlank() }
            .map { ChecklistItemUiState(text = it) }
            .ifEmpty { listOf(ChecklistItemUiState()) }

    /** Non-blank item text, one per line. Checked state and indentation are dropped. */
    fun itemsToText(items: List<ChecklistItemUiState>): String =
        items.filter { it.text.isNotBlank() }.joinToString("\n") { it.text }
}
