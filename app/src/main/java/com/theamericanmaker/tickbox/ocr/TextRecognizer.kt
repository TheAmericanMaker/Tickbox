// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ocr

import java.io.File

/**
 * Extracts text from an image on-device.
 *
 * Smart Toolkit used Google's ML Kit here. ML Kit is proprietary, which would make
 * Tickbox ineligible for F-Droid, so this interface exists to keep the engine
 * swappable and the UI independent of it. A Tesseract-backed implementation lands
 * separately; until then [UnavailableTextRecognizer] stands in and the UI hides the
 * extract-text affordance.
 */
interface TextRecognizer {
    /**
     * Returns the recognised text, or a failure. Callers show the error rather than
     * silently doing nothing — a button that appears to do nothing reads as a bug.
     */
    suspend fun recognize(image: File): Result<String>

    companion object {
        /** Splits recognised text into checklist-sized pieces. Engine-independent. */
        fun splitIntoItems(text: String): List<String> =
            text.lines().map { it.trim() }.filter { it.isNotBlank() }
    }
}

/** Stands in while no OCR engine is wired up. */
object UnavailableTextRecognizer : TextRecognizer {
    override suspend fun recognize(image: File): Result<String> =
        Result.failure(UnsupportedOperationException("Text extraction is not available in this build."))
}
