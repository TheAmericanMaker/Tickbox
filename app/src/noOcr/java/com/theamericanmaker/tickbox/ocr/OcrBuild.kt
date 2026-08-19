// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ocr

import android.content.Context

/**
 * What this build variant can do about text in images. The `noOcr` half: nothing.
 *
 * Returning null is not a stub — it is the path the app shipped before OCR existed and the
 * one `NoteEditViewModelTest` still exercises. `ocrAvailable` goes false, the extract-text
 * button and its badge are never composed, and Help omits the how-to.
 *
 * This variant carries no Tesseract, no Leptonica and no language model, which is about
 * 31 MB of the 31.5 MB the other one spends. See #31.
 */
object OcrBuild {
    const val AVAILABLE = false

    fun createTextRecognizer(context: Context): TextRecognizer? = null
}
