// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitIntoItemsTest {

    @Test
    fun splitsLinesTrimsAndDropsBlanks() {
        val items = TextRecognizer.splitIntoItems("  Milk \n\n Eggs\n\t\nBread ")
        assertEquals(listOf("Milk", "Eggs", "Bread"), items)
    }

    @Test
    fun emptyInputYieldsNoItems() {
        assertEquals(emptyList<String>(), TextRecognizer.splitIntoItems("\n \n"))
    }
}
