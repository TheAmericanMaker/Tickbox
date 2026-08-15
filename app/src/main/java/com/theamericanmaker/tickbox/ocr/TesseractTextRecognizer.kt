// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ocr

import android.content.Context
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
 * printed pages. `tessdata_fast` trades some accuracy for size and speed; if device
 * testing finds it wanting, the standard `tessdata` model (~15 MB) is a drop-in.
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
                    api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                    api.setImage(image)
                    api.getUTF8Text().orEmpty()
                } finally {
                    api.recycle()
                }
            }
        }
    }

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
