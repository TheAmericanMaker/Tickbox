// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ocr

import android.content.Context
import com.googlecode.leptonica.android.Binarize
import com.googlecode.leptonica.android.Convert
import com.googlecode.leptonica.android.Pix
import com.googlecode.leptonica.android.ReadFile
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device OCR via Tesseract 5 (tesseract4android, Apache-2.0).
 *
 * The English model ships in assets as `tessdata_fast/eng` (~4 MB) and is copied to
 * `filesDir/tessdata` on first use — Tesseract wants a real directory it can read, and
 * `init` takes the *parent* of `tessdata`, not the directory itself.
 *
 * Expectations to hold against ML Kit, which this replaces: a recognition takes on the
 * order of seconds rather than ~200 ms, so callers must show progress, and accuracy on
 * handheld photos of curved or poorly lit text is noticeably weaker than on flat
 * printed pages. `tessdata_fast` trades some accuracy for size and speed — but device
 * testing found the model was never the limit. See [binarise]: what broke photographs of
 * paper was Tesseract's own global thresholding, and the 15 MB model measured no better
 * than the 4 MB one once that was fixed.
 *
 * One recognition at a time: TessBaseAPI is not thread-safe, and a second concurrent
 * caller would also double peak memory on what is already the app's heaviest operation.
 * The API instance is created per call rather than cached — init is a fraction of the
 * recognition cost, and it keeps native memory released while OCR is idle, which is
 * almost always.
 */
class TesseractTextRecognizer(private val context: Context) : TextRecognizer {

    private val mutex = Mutex()

    override suspend fun recognize(image: File): Result<String> = withContext(Dispatchers.Default) {
        mutex.withLock {
            runCatching {
                if (!image.exists()) error("The image file no longer exists.")
                val dataParent = ensureTrainedData()
                val api = TessBaseAPI()
                try {
                    if (!api.init(dataParent.absolutePath, LANGUAGE)) {
                        error("The text recognition engine failed to initialise.")
                    }
                    // Two passes, scored, best one wins. Neither thresholding is right for every
                    // image: Tesseract's own global Otsu suits screenshots and flat scans, Sauvola
                    // suits photographs of paper, and each is materially worse than the other on
                    // the wrong input. Guessing which kind of image this is would be a heuristic;
                    // asking the recogniser how well it did is a measurement.
                    val otsu = api.readScored { setImage(image) }

                    val binarised = binarise(image)
                    val sauvola = if (binarised == null) {
                        null
                    } else {
                        try {
                            api.readScored { setImage(binarised) }
                        } finally {
                            binarised.recycle()
                        }
                    }

                    if (sauvola != null && sauvola.second > otsu.second) sauvola.first else otsu.first
                } finally {
                    api.recycle()
                }
            }
        }
    }

    /**
     * Runs one recognition and returns its text alongside a score for comparing passes.
     *
     * `clear()` first, because results otherwise accumulate on a reused instance.
     */
    private fun TessBaseAPI.readScored(setImage: TessBaseAPI.() -> Unit): Pair<String, Int> {
        clear()
        setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
        setImage()
        val text = getUTF8Text().orEmpty()
        return text to text.readableWordCount()
    }

    /**
     * Binarises [image] with Sauvola local thresholding, or null to let Tesseract do its own.
     *
     * This is the difference between the feature working and not. Left to itself Tesseract
     * applies a *global* Otsu threshold, which assumes one exposure across the page. A phone
     * photo of paper rarely has one: half the sheet is lit and half is shadowed, and a single
     * threshold then puts the lit paper on one side of it and the shadowed paper on the other.
     * The result is a page binarised into a black region and a white blob, with the text
     * dissolving wherever it crosses the boundary — measured on a real packing slip, a clean
     * four-line address block came back as `Mid Michiga` and nothing else.
     *
     * Sauvola thresholds against local mean and variance instead, so a lighting gradient stops
     * mattering. Same photo, same model, after this change: the whole address block, correct.
     *
     * Leptonica ships inside tesseract4android already, so this costs no dependency and about
     * 200 ms. Cheap at the price — the alternative on the table was an 11 MB larger model, which
     * measured *worse* than this and fixed only one digit.
     *
     * Best-effort: any failure returns null and the caller falls back to Tesseract's own
     * thresholding, which is what shipped before and is adequate for screenshots and flat scans.
     */
    private fun binarise(image: File): Pix? = runCatching {
        val source = ReadFile.readFile(image) ?: return null
        var grey: Pix? = null
        try {
            grey = Convert.convertTo8(source)
            Binarize.sauvolaBinarizeTiled(grey)
        } finally {
            // These are native allocations, so they have to be released by hand. convertTo8 can
            // hand back the *same* Pix when the input is already 8bpp, and recycling that twice
            // would be a double free.
            grey?.takeIf { it !== source }?.recycle()
            source.recycle()
        }
    }.getOrNull()

    /** Copies the bundled model out of assets once. Returns the parent of `tessdata`. */
    private fun ensureTrainedData(): File {
        val parent = context.filesDir
        val target = File(File(parent, TESSDATA_DIR), TRAINED_DATA_FILE)
        if (!target.exists() || target.length() == 0L) {
            target.parentFile?.mkdirs()
            val staging = File(target.parentFile, "$TRAINED_DATA_FILE.tmp")
            context.assets.open("$TESSDATA_DIR/$TRAINED_DATA_FILE").use { input ->
                staging.outputStream().use { output -> input.copyTo(output) }
            }
            // Rename after a complete copy, so a crash mid-copy can never leave a
            // truncated model that init would then reject on every later launch.
            if (!staging.renameTo(target)) {
                staging.delete()
                error("Could not prepare the text recognition model.")
            }
        }
        return parent
    }

    private companion object {
        const val LANGUAGE = "eng"
        const val TESSDATA_DIR = "tessdata"
        const val TRAINED_DATA_FILE = "eng.traineddata"
    }
}

/**
 * How much of [this] looks like actual words — the score for choosing between OCR passes.
 *
 * Scores the *output*, rather than asking Tesseract, because both of its own signals mislead
 * here. `meanConfidence()` averages over whatever was recognised, so it rewards reading less: the
 * pass that found `Mid Michiga` and stopped out-scored the pass that read the whole address.
 * `wordConfidences()` is worse — it covers internal candidate blobs including ones that never
 * reach the text, and on screen moiré reported 192 words for 134 characters of output, which is
 * not a number that can be true.
 *
 * A token counts when it holds at least three alphanumerics and is not mostly punctuation. That
 * tracks real reading while discarding the `| : ' ee | S : |` debris a bad binarisation throws
 * off, which is what separates the two passes in practice.
 */
internal fun String.readableWordCount(): Int = split(WHITESPACE).count { token ->
    val alphanumerics = token.count(Char::isLetterOrDigit)
    alphanumerics >= MIN_WORD_ALPHANUMERICS && alphanumerics >= token.length - 2
}

private val WHITESPACE = Regex("\\s+")

/** Below this, a token is as likely to be binarisation debris as a word. */
private const val MIN_WORD_ALPHANUMERICS = 3
