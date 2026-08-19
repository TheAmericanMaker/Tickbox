// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ocr

import android.content.Context

/**
 * What this build variant can do about text in images. The `withOcr` half.
 *
 * The whole flavour difference is this file plus [TesseractTextRecognizer], the model in
 * `assets/`, and the dependency line that pulls the native libraries in. Everything else —
 * the [TextRecognizer] seam, the null handling in `AppContainer`, `ocrAvailable` and the
 * affordances it gates — is shared, and was already the app's behaviour before OCR existed.
 *
 * Keeping [AVAILABLE] and [createTextRecognizer] together is deliberate: they are two views
 * of one fact, and a build that hides the button but ships 31 MB of Tesseract — or advertises
 * extraction it cannot do — is what splitting them would eventually produce.
 */
object OcrBuild {
    const val AVAILABLE = true

    fun createTextRecognizer(context: Context): TextRecognizer? = TesseractTextRecognizer(context)
}
