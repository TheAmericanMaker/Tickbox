// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ui.edit

/**
 * Converting between a text note and a checklist, as pure functions.
 *
 * Pulled out of the ViewModel because this is the fiddliest logic in the editor and
 * the easiest thing to break: it runs on real user content, in both directions, and a
 * mistake silently loses text.
 *
 * **Liberal reading, plain writing.** Text arrives from places the app does not control — a note
 * someone typed, a paste from another app, OCR of a photographed list — so [parseLine] recognises
 * the bullet and task-list forms those actually produce, markdown's included. Nothing writes them
 * back out. Flipping a list to a note is something people do *in order to read it*, and a body
 * full of `- [ ]` would defeat the reason for the flip.
 *
 * The one exception is indentation, which is written as leading spaces: two spaces is markdown's
 * own convention for a nested item, it reads correctly to a human, and it survives editing,
 * copy/paste and a restart. Ticks cannot survive plain text and are restored by the caller
 * instead — see `NoteEditViewModel.onToggleType`.
 */
object ChecklistConversion {

    /** Indentation is capped at one level for 1.0, matching the editor. */
    private const val MAX_INDENT_LEVEL = 1

    /** How an indented item is written into a note body. Markdown's nested-list convention. */
    private const val INDENT_PREFIX = "  "

    /**
     * `-`, `*`, `+`, and the bullet and dash characters a printed list is likely to use.
     *
     * Matches at end of line too, so a stray `-` on its own is a marker rather than an item.
     * It still needs whitespace *or* the end after it, which is what keeps `---` a line of text
     * and `re-order` a word.
     */
    private val BULLET = Regex("""^[-*+•·–—](\s+|$)""")

    /** `1.` or `12)` — a list marker, not a quantity: it has to be followed by whitespace. */
    private val NUMBERED = Regex("""^\d{1,3}[.)]\s+""")

    /** `[x]`, `[X]`, `[ ]`, or the glyphs this app's own share format writes. */
    private val TASK_MARKER = Regex("""^(\[[ xX]?]|☑|☐)\s*""")

    /** One line of text, read as a checklist item. */
    data class ParsedLine(val text: String, val isChecked: Boolean, val indentLevel: Int)

    /**
     * Reads one line, or null when there is nothing on it.
     *
     * Null for blanks *and* for lines that are only a marker: a stray `-` in a pasted list should
     * not become an empty item.
     */
    fun parseLine(line: String): ParsedLine? {
        if (line.isBlank()) return null
        // Whitespace before anything else is the indent, however it was produced — spaces from
        // this app, a tab from a keyboard, four spaces from somewhere else.
        val indentLevel = if (line.first().isWhitespace()) MAX_INDENT_LEVEL else 0

        var rest = line.trim()
        rest = rest.replaceFirst(BULLET, "").replaceFirst(NUMBERED, "").trimStart()

        val marker = TASK_MARKER.find(rest)
        val isChecked = marker?.value?.any { it == 'x' || it == 'X' || it == '☑' } == true
        if (marker != null) rest = rest.removeRange(marker.range).trimStart()

        val text = rest.trim()
        return if (text.isEmpty()) null else ParsedLine(text, isChecked, indentLevel)
    }

    /** One checklist item per meaningful line. Never returns an empty list. */
    fun textToItems(content: String): List<ChecklistItemUiState> =
        content.lines()
            .mapNotNull(::parseLine)
            .map {
                ChecklistItemUiState(
                    text = it.text,
                    isChecked = it.isChecked,
                    indentLevel = it.indentLevel,
                )
            }
            .ifEmpty { listOf(ChecklistItemUiState()) }

    /**
     * Non-blank item text, one per line, indented items prefixed with spaces.
     *
     * Checked state is not represented; the caller restores it when the body comes back unedited.
     */
    fun itemsToText(items: List<ChecklistItemUiState>): String =
        items.filter { it.text.isNotBlank() }
            .joinToString("\n") { item ->
                if (item.indentLevel > 0) INDENT_PREFIX + item.text else item.text
            }
}
