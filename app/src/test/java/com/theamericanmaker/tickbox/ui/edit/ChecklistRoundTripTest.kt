// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ui.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checklist to note and back.
 *
 * The conversion is deliberately asymmetric: markdown-ish forms are *accepted* when reading, but
 * never *written*. Flipping to a note is something people do in order to read the list, so a body
 * full of `- [ ]` would defeat the reason for the flip.
 */
class ChecklistRoundTripTest {

    private fun items(vararg specs: Triple<String, Boolean, Int>) =
        specs.map { (text, checked, indent) ->
            ChecklistItemUiState(text = text, isChecked = checked, indentLevel = indent)
        }

    @Test
    fun `writing a note keeps the words and nothing else`() {
        val text = ChecklistConversion.itemsToText(
            items(
                Triple("Milk", false, 0),
                Triple("Bread", true, 0),
            ),
        )
        assertEquals("Milk\nBread", text)
    }

    @Test
    fun `indentation is written as leading spaces`() {
        val text = ChecklistConversion.itemsToText(
            items(
                Triple("Bread", false, 0),
                Triple("Sourdough", false, 1),
            ),
        )
        assertEquals("Bread\n  Sourdough", text)
    }

    @Test
    fun `indentation survives the round trip`() {
        val original = items(
            Triple("Bread", false, 0),
            Triple("Sourdough", false, 1),
            Triple("Milk", false, 0),
        )
        val back = ChecklistConversion.textToItems(ChecklistConversion.itemsToText(original))
        assertEquals(listOf("Bread", "Sourdough", "Milk"), back.map { it.text })
        assertEquals(listOf(0, 1, 0), back.map { it.indentLevel })
    }

    @Test
    fun `ticks do not survive the text, which is why the caller remembers them`() {
        // Recorded rather than aspired to: the note body carries no tick marks by design, so a
        // round trip through text alone cannot restore them. NoteEditViewModel keeps the items.
        val original = items(Triple("Bread", true, 0))
        val back = ChecklistConversion.textToItems(ChecklistConversion.itemsToText(original))
        assertTrue(!back.single().isChecked)
    }

    @Test
    fun `a plain note is not decorated on the way back out`() {
        // Someone types three lines, converts to a checklist, converts back. They should get
        // their three lines, not three lines with bullets bolted on.
        val typed = "Call the plumber\nBook the car in\nPay the water bill"
        val there = ChecklistConversion.textToItems(typed)
        val andBack = ChecklistConversion.itemsToText(there)
        assertEquals(typed, andBack)
    }

    @Test
    fun `a markdown checklist pasted in becomes a real checklist`() {
        val pasted = """
            - [x] Milk
            - [ ] Bread
              - [ ] Sourdough
            * Eggs
            1. Butter
        """.trimIndent()
        val parsed = ChecklistConversion.textToItems(pasted)
        assertEquals(listOf("Milk", "Bread", "Sourdough", "Eggs", "Butter"), parsed.map { it.text })
        assertEquals(listOf(true, false, false, false, false), parsed.map { it.isChecked })
        assertEquals(listOf(0, 0, 1, 0, 0), parsed.map { it.indentLevel })
    }

    @Test
    fun `blank items are dropped and a checklist is never empty`() {
        assertEquals(1, ChecklistConversion.textToItems("").size)
        assertEquals("", ChecklistConversion.textToItems("").single().text)
        assertEquals("", ChecklistConversion.itemsToText(items(Triple("", false, 0))))
    }

    @Test
    fun `leading whitespace no longer leaks into the item text`() {
        // It used to: textToItems mapped the raw line, so an indented line produced an item whose
        // text began with spaces, and those spaces were then saved.
        val parsed = ChecklistConversion.textToItems("    Sourdough")
        assertEquals("Sourdough", parsed.single().text)
    }
}
