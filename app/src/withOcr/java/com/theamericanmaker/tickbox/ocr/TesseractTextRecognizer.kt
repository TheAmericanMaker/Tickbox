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

                    if (preferSauvola(otsu.second, sauvola?.second)) sauvola!!.first else otsu.first
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
        return text to scoreOf(text)
    }

    /**
     * How many words this pass read *and was sure of*.
     *
     * `wordConfidences()` has to be read after [TessBaseAPI.getUTF8Text] has forced recognition,
     * and it can report more entries than the text has tokens — it includes candidate blobs. That
     * over-count is what made it useless as a raw total, and is exactly what the threshold
     * discards, since blobs that never reached the text score low.
     *
     * Falls back to the text-only heuristic if the native call is unavailable, so a missing
     * signal degrades to the previous behaviour instead of scoring everything zero.
     */
    private fun TessBaseAPI.scoreOf(text: String): Int {
        if (text.isBlank()) return 0
        return runCatching { wordConfidences().confidentWordCount() }
            .getOrElse { text.readableWordCount() }
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
 * How many recognised words cleared [MIN_WORD_CONFIDENCE] — the score for choosing between passes.
 *
 * Both of Tesseract's obvious signals fail here, in opposite directions, and both failures are
 * measured rather than argued:
 *
 * `meanConfidence()` rewards reading *less*. On a half-shadowed packing slip, Otsu returned
 * 11 characters at confidence **93** while Sauvola returned the whole address block at **85** — so
 * averaging picks the pass that gave up.
 *
 * Counting word-shaped tokens in the output rewards reading *more*, including nonsense. On a
 * screenshot of this app, Otsu read the screen almost perfectly and scored **8**, while Sauvola
 * shredded the anti-aliased glyphs into 25 word-shaped fragments — `itesseractitest}`,
 * `Marrowiscoallll` — and scored **25**. Noise generates more tokens than clean text does, so that
 * metric rewarded the failure it existed to catch.
 *
 * Counting *confident* words is the combination that survives both, because it rewards reading more
 * only when Tesseract believes what it read:
 *
 * | | screenshot | packing slip |
 * | --- | --- | --- |
 * | Otsu | **14** | 3 |
 * | Sauvola | 5 | **13** |
 *
 * The threshold is not knife-edge — 60, 70 and 80 all separate both images the same way.
 */
internal fun IntArray.confidentWordCount(): Int = count { it >= MIN_WORD_CONFIDENCE }

/**
 * Whether the Sauvola pass should be preferred, given both scores.
 *
 * Split out from the recogniser so the decision can be tested without Tesseract: the numbers in
 * `PassScoringTest` are the ones measured on real images.
 */
internal fun preferSauvola(otsuScore: Int, sauvolaScore: Int?): Boolean =
    sauvolaScore != null && sauvolaScore > otsuScore

/**
 * How much of [this] looks like actual words.
 *
 * No longer the primary score — see [confidentWordCount] for why it was replaced and what it got
 * wrong. Kept as the fallback for when `wordConfidences()` is unavailable, where behaving as the
 * app did before beats scoring every pass zero.
 *
 * A token counts when it holds at least three alphanumerics and is not mostly punctuation. That
 * tracks real reading while discarding the `| : ' ee | S : |` debris a bad binarisation throws off.
 */
internal fun String.readableWordCount(): Int = split(WHITESPACE).count { token ->
    val alphanumerics = token.count(Char::isLetterOrDigit)
    alphanumerics >= MIN_WORD_ALPHANUMERICS && alphanumerics >= token.length - 2
}

private val WHITESPACE = Regex("\\s+")

/** Below this, a token is as likely to be binarisation debris as a word. */
private const val MIN_WORD_ALPHANUMERICS = 3

/**
 * Tesseract's per-word confidence, 0-100, below which a word is not counted.
 *
 * Chosen from the measurements in [confidentWordCount]: garbage from a mis-binarised screenshot
 * sat around 32 mean, correct reads around 79-93.
 */
private const val MIN_WORD_CONFIDENCE = 70
