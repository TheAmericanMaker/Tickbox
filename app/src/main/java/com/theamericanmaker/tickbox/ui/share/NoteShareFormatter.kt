// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ui.share

import com.theamericanmaker.tickbox.data.model.ChecklistItem
import com.theamericanmaker.tickbox.data.model.NoteType

/**
 * Renders a note for an ACTION_SEND share, as plain text and as HTML.
 *
 * Takes domain [ChecklistItem]s rather than UI state, so it can be tested without
 * pulling in anything from Compose.
 */
object NoteShareFormatter {

    private const val UNCHECKED_BOX = "☐"
    private const val CHECKED_BOX = "☑"

    fun formatForSharing(
        title: String,
        content: String,
        type: NoteType,
        checklistItems: List<ChecklistItem>,
    ): String = buildString {
        if (title.isNotBlank()) {
            appendLine(title)
            appendLine()
        }
        when (type) {
            NoteType.TEXT -> append(content)
            NoteType.CHECKLIST -> {
                var topLevelNumber = 0
                checklistItems
                    .filter { it.text.isNotBlank() }
                    .forEach { item ->
                        val box = if (item.isChecked) CHECKED_BOX else UNCHECKED_BOX
                        if (item.indentLevel == 0) {
                            topLevelNumber++
                            appendLine("$topLevelNumber. $box ${item.text}")
                        } else {
                            appendLine("   $box ${item.text}")
                        }
                    }
            }
        }
    }.trimEnd()

    fun formatAsHtml(
        title: String,
        content: String,
        type: NoteType,
        checklistItems: List<ChecklistItem>,
    ): String = buildString {
        if (title.isNotBlank()) {
            append("<h3>${title.escapeHtml()}</h3>")
        }
        when (type) {
            NoteType.TEXT -> {
                content.lines().forEach { line ->
                    append("<p>${line.escapeHtml()}</p>")
                }
            }
            NoteType.CHECKLIST -> {
                append("<ul style=\"list-style-type: none; padding: 0;\">")
                var topLevelNumber = 0
                checklistItems
                    .filter { it.text.isNotBlank() }
                    .forEach { item ->
                        val decoration =
                            if (item.isChecked) "text-decoration: line-through; color: #888;" else ""
                        val check = if (item.isChecked) CHECKED_BOX else UNCHECKED_BOX
                        val indent = if (item.indentLevel > 0) "padding-left: 24px;" else ""
                        val prefix = if (item.indentLevel == 0) {
                            topLevelNumber++
                            "$topLevelNumber. "
                        } else {
                            ""
                        }
                        append("<li style=\"$decoration$indent\">$prefix$check ${item.text.escapeHtml()}</li>")
                    }
                append("</ul>")
            }
        }
    }

    /**
     * Note text is user content, not markup. Without this a note titled `A & B <3`
     * produces broken HTML in the receiving app.
     */
    private fun String.escapeHtml(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
