// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ui.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistConversionTest {

    @Test
    fun textBecomesOneItemPerNonBlankLine() {
        val items = ChecklistConversion.textToItems("Milk\n\nEggs\n   \nBread")
        assertEquals(listOf("Milk", "Eggs", "Bread"), items.map { it.text })
    }

    @Test
    fun emptyTextStillYieldsOneBlankItem() {
        // The editor always needs at least one row to type into.
        val items = ChecklistConversion.textToItems("")
        assertEquals(1, items.size)
        assertTrue(items.single().text.isBlank())
    }

    @Test
    fun itemsBecomeLinesDroppingBlanks() {
        val text = ChecklistConversion.itemsToText(
            listOf(
                ChecklistItemUiState(text = "Milk"),
                ChecklistItemUiState(text = " "),
                ChecklistItemUiState(text = "Eggs", isChecked = true, indentLevel = 1),
            ),
        )
        assertEquals("Milk\nEggs", text)
    }

    @Test
    fun roundTripPreservesNonBlankText() {
        val original = "Milk\nEggs\nBread"
        val roundTripped = ChecklistConversion.itemsToText(ChecklistConversion.textToItems(original))
        assertEquals(original, roundTripped)
    }
}
