// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ui.share

import com.theamericanmaker.tickbox.data.model.ChecklistItem
import com.theamericanmaker.tickbox.data.model.NoteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteShareFormatterTest {

    @Test
    fun textNoteSharesTitleAndContent() {
        val result = NoteShareFormatter.formatForSharing(
            title = "Trip",
            content = "Pack the charger",
            type = NoteType.TEXT,
            checklistItems = emptyList(),
        )
        assertEquals("Trip\n\nPack the charger", result)
    }

    @Test
    fun blankTitleIsOmitted() {
        val result = NoteShareFormatter.formatForSharing(
            title = "  ",
            content = "Body only",
            type = NoteType.TEXT,
            checklistItems = emptyList(),
        )
        assertEquals("Body only", result)
    }

    @Test
    fun checklistNumbersOnlyTopLevelItems() {
        val result = NoteShareFormatter.formatForSharing(
            title = "",
            content = "",
            type = NoteType.CHECKLIST,
            checklistItems = listOf(
                ChecklistItem(text = "Milk"),
                ChecklistItem(text = "Whole", indentLevel = 1),
                ChecklistItem(text = "Eggs", isChecked = true),
            ),
        )
        val lines = result.lines()
        assertEquals("1. ☐ Milk", lines[0])
        assertEquals("   ☐ Whole", lines[1])
        assertEquals("2. ☑ Eggs", lines[2])
    }

    @Test
    fun blankChecklistItemsAreDropped() {
        val result = NoteShareFormatter.formatForSharing(
            title = "",
            content = "",
            type = NoteType.CHECKLIST,
            checklistItems = listOf(
                ChecklistItem(text = "Milk"),
                ChecklistItem(text = "   "),
                ChecklistItem(text = "Eggs"),
            ),
        )
        assertEquals(2, result.lines().size)
        assertEquals("2. ☐ Eggs", result.lines()[1])
    }

    @Test
    fun htmlEscapesMarkupInTitleContentAndItems() {
        val html = NoteShareFormatter.formatAsHtml(
            title = "A & B <3",
            content = "x < y > z & \"q\"",
            type = NoteType.TEXT,
            checklistItems = emptyList(),
        )
        assertTrue(html.contains("A &amp; B &lt;3"))
        assertTrue(html.contains("x &lt; y &gt; z &amp; &quot;q&quot;"))
        assertFalse(html.contains("<3"))
    }

    @Test
    fun htmlChecklistEscapesItemText() {
        val html = NoteShareFormatter.formatAsHtml(
            title = "",
            content = "",
            type = NoteType.CHECKLIST,
            checklistItems = listOf(ChecklistItem(text = "buy <thing> & more")),
        )
        assertTrue(html.contains("buy &lt;thing&gt; &amp; more"))
    }

    @Test
    fun htmlStrikesThroughCheckedItems() {
        val html = NoteShareFormatter.formatAsHtml(
            title = "",
            content = "",
            type = NoteType.CHECKLIST,
            checklistItems = listOf(
                ChecklistItem(text = "Done", isChecked = true),
                ChecklistItem(text = "Open"),
            ),
        )
        assertTrue(html.contains("line-through"))
        assertTrue(html.contains("☑ Done"))
        assertTrue(html.contains("☐ Open"))
    }

    @Test
    fun htmlIndentsNestedItemsWithoutNumberingThem() {
        val html = NoteShareFormatter.formatAsHtml(
            title = "",
            content = "",
            type = NoteType.CHECKLIST,
            checklistItems = listOf(
                ChecklistItem(text = "Top"),
                ChecklistItem(text = "Nested", indentLevel = 1),
            ),
        )
        assertTrue(html.contains("1. ☐ Top"))
        assertTrue(html.contains("padding-left: 24px;"))
        assertFalse(html.contains("2. ☐ Nested"))
    }
}
