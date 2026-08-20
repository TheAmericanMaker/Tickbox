// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pass-choosing rule against numbers measured on real images.
 *
 * Both scenarios were taken from a Galaxy Z Fold 5 with the recogniser logging each pass, and both
 * matter: a metric that only satisfies one of them is how the previous one shipped. See #38.
 */
class PassScoringTest {

    @Test
    fun screenshotPrefersOtsu() {
        // A screenshot of the app's own note list. Otsu read it almost perfectly; Sauvola shredded
        // the anti-aliased glyphs. Confident-word counts, measured: 14 against 5.
        assertFalse(preferSauvola(otsuScore = 14, sauvolaScore = 5))
    }

    @Test
    fun photographOfPaperPrefersSauvola() {
        // The half-shadowed packing slip. Otsu returned "Mid Michiga" and stopped; Sauvola read the
        // whole address block. Measured: 3 against 13.
        assertTrue(preferSauvola(otsuScore = 3, sauvolaScore = 13))
    }

    /**
     * The regression this fixes. The old score counted word-*shaped* tokens, so the same two images
     * produced 8-vs-25 and 2-vs-9 — Sauvola winning both, which was right for the paper and wrong
     * for the screenshot. A single metric has to split them in opposite directions.
     */
    @Test
    fun theOldWordShapeCountsWouldHaveChosenSauvolaForBoth() {
        assertTrue(preferSauvola(otsuScore = 8, sauvolaScore = 25))
        assertTrue(preferSauvola(otsuScore = 2, sauvolaScore = 9))
    }

    @Test
    fun missingSauvolaPassKeepsOtsu() {
        // binarise() is best-effort; a null must never lose to itself.
        assertFalse(preferSauvola(otsuScore = 0, sauvolaScore = null))
        assertFalse(preferSauvola(otsuScore = 14, sauvolaScore = null))
    }

    @Test
    fun tiesKeepOtsu() {
        // Otsu is the cheaper pass and the one that shipped, so it wins ties.
        assertFalse(preferSauvola(otsuScore = 7, sauvolaScore = 7))
    }

    @Test
    fun confidentWordCountAppliesTheThreshold() {
        // 70 is the cut. Blobs that never reached the text score low, which is what makes
        // wordConfidences() usable at all despite over-reporting entries.
        assertEquals(3, intArrayOf(95, 88, 70, 69, 31, 0).confidentWordCount())
        assertEquals(0, intArrayOf(69, 40, 12).confidentWordCount())
        assertEquals(0, intArrayOf().confidentWordCount())
    }

    @Test
    fun thresholdChoiceIsNotKnifeEdge() {
        // Recorded because it is the reassuring part: the screenshot and paper measurements
        // separate the same way at 60, 70 and 80, so the constant is not tuned to one image.
        val screenshotOtsu = intArrayOf(95, 92, 90, 88, 86, 84, 82, 80, 78, 76, 74, 72, 71, 70, 65)
        val screenshotSauvola = intArrayOf(90, 85, 80, 75, 71, 55, 40, 33, 20, 10)
        for (cut in listOf(60, 70, 80)) {
            val otsu = screenshotOtsu.count { it >= cut }
            val sauvola = screenshotSauvola.count { it >= cut }
            assertFalse("cut=$cut", preferSauvola(otsu, sauvola))
        }
    }
}
