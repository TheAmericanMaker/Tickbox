// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ui.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line parser, which is liberal on purpose.
 *
 * Text arrives here from three places that the app does not control: a note the owner typed, a
 * paste from another app, and OCR of a photographed list. Recognising the forms those actually
 * produce is what lets a checklist copied out of anywhere become a checklist here.
 */
class ChecklistLineParsingTest {

    private fun parse(line: String) = ChecklistConversion.parseLine(line)

    @Test
    fun `plain text is left alone`() {
        val parsed = parse("Milk")!!
        assertEquals("Milk", parsed.text)
        assertEquals(0, parsed.indentLevel)
        assertTrue(!parsed.isChecked)
    }

    @Test
    fun `blank lines are dropped`() {
        assertNull(parse(""))
        assertNull(parse("   "))
        assertNull(parse("\t"))
    }

    @Test
    fun `a line that is only a bullet is dropped`() {
        // Otherwise a stray "-" in a pasted list becomes an empty item.
        assertNull(parse("-"))
        assertNull(parse("- [ ]"))
    }

    @Test
    fun `dash, asterisk, plus and bullet markers are stripped`() {
        for (marker in listOf("- ", "* ", "+ ", "• ", "– ", "— ")) {
            assertEquals("Milk", parse("${marker}Milk")!!.text)
        }
    }

    @Test
    fun `numbered markers are stripped`() {
        assertEquals("Milk", parse("1. Milk")!!.text)
        assertEquals("Milk", parse("12) Milk")!!.text)
    }

    @Test
    fun `a number that is part of the text survives`() {
        // "2 gallons" is the item, not item number 2.
        assertEquals("2 gallons of milk", parse("2 gallons of milk")!!.text)
    }

    @Test
    fun `task markers set the checked state`() {
        assertTrue(parse("- [x] Bread")!!.isChecked)
        assertTrue(parse("- [X] Bread")!!.isChecked)
        assertTrue(!parse("- [ ] Bread")!!.isChecked)
        assertEquals("Bread", parse("- [x] Bread")!!.text)
    }

    @Test
    fun `a task marker without a bullet still counts`() {
        assertTrue(parse("[x] Bread")!!.isChecked)
        assertEquals("Bread", parse("[x] Bread")!!.text)
    }

    @Test
    fun `the app's own share glyphs are understood`() {
        // So a shared list pasted back into the app round-trips.
        assertTrue(parse("☑ Bread")!!.isChecked)
        assertTrue(!parse("☐ Bread")!!.isChecked)
        assertEquals("Bread", parse("☑ Bread")!!.text)
    }

    @Test
    fun `leading whitespace means indented`() {
        assertEquals(1, parse("  Sourdough")!!.indentLevel)
        assertEquals(1, parse("\tSourdough")!!.indentLevel)
        assertEquals("Sourdough", parse("  Sourdough")!!.text)
    }

    @Test
    fun `indent is capped at one level, matching the editor`() {
        assertEquals(1, parse("        deeply indented")!!.indentLevel)
    }

    @Test
    fun `indent and marker and tick combine`() {
        val parsed = parse("  - [x] Sourdough")!!
        assertEquals("Sourdough", parsed.text)
        assertEquals(1, parsed.indentLevel)
        assertTrue(parsed.isChecked)
    }

    @Test
    fun `a hyphen inside the text is not a bullet`() {
        assertEquals("re-order the thing", parse("re-order the thing")!!.text)
    }

    @Test
    fun `trailing whitespace goes`() {
        assertEquals("Milk", parse("- Milk   ")!!.text)
    }
}
