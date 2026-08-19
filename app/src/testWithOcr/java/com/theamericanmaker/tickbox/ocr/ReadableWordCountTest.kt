// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scorer that decides which of the two OCR passes wins.
 *
 * The pairs below are real output, captured on device from the two images that motivated the
 * two-pass approach. They are the actual decision this function has to get right, so they are
 * asserted as such rather than as invented strings.
 */
class ReadableWordCountTest {

    @Test
    fun `ignores punctuation debris from a bad binarisation`() {
        assertEquals(0, ", | : ' ee | S : | 7 oS A : | q".readableWordCount())
    }

    @Test
    fun `counts ordinary words`() {
        assertEquals(4, "Mid Michigan Mfg LLC".readableWordCount())
    }

    @Test
    fun `does not count fragments shorter than three characters`() {
        // "MI" and "Rd" are real, but two-character tokens are indistinguishable from speckle,
        // and the score only has to rank two passes rather than tally them exactly.
        assertEquals(1, "MI Rd 48756".readableWordCount())
    }

    @Test
    fun `counts tokens carrying some punctuation, like file names`() {
        // "suite", "via", "run_bench.sh", "now" — but not "(think-off)", whose two brackets and
        // hyphen put it one over the two-non-alphanumeric tolerance. Undercounting a real word is
        // acceptable: the score only ranks two passes, and the same rule is applied to both.
        assertEquals(4, "suite (think-off) via run_bench.sh now".readableWordCount())
    }

    @Test
    fun `photograph of paper - Sauvola beats Tesseract's own thresholding`() {
        val otsu = "Mid Michiga"
        val sauvola = "ini i\nMid Michigan Mfg LLC\n9298 Drow Rd\nPrescott MI 48756"
        assertTrue(
            "Sauvola read the whole address and must win",
            sauvola.readableWordCount() > otsu.readableWordCount(),
        )
    }

    @Test
    fun `photograph of a screen - Tesseract's own thresholding beats Sauvola`() {
        val otsu = "5 of 9 todos completed\n\n) Run benchmark suite (think-off) via run_bench.sh\n" +
            "Run benchmark suite (think-on) via run_bench.sh\n" +
            "Capture performance metrics (tok/s, load time, GPU/RAM split) for both modes"
        val sauvola = "Ipture p formance recfhe load time GPU/RAM : split) for both modes\n" +
            "Ru benchmark. suite (think-on), vi run “bench.s\nQualitative notes"
        assertTrue(
            "Screen moire wrecks Sauvola, so the plain pass must win here",
            otsu.readableWordCount() > sauvola.readableWordCount(),
        )
    }

    @Test
    fun `empty and blank score zero`() {
        assertEquals(0, "".readableWordCount())
        assertEquals(0, "   \n  ".readableWordCount())
    }
}
