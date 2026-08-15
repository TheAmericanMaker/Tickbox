// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Owns `filesDir/note_images`, where note attachments live as JPEGs.
 *
 * This exists so the ViewModels never need a [Context]: they take a store instead,
 * which makes them constructible — and therefore testable — without an Activity.
 */
class NoteImageStore(private val context: Context) {

    val directory: File
        get() = File(context.filesDir, DIRECTORY_NAME)

    fun fileFor(fileName: String): File = File(directory, fileName)

    fun ensureDirectory(): File = directory.also { it.mkdirs() }

    /**
     * Copies [uri] into internal storage, downscaled and re-encoded.
     *
     * Returns the generated filename, or null if the image could not be read.
     */
    suspend fun saveFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            directory.mkdirs()
            val fileName = "${UUID.randomUUID()}.jpg"
            val outFile = File(directory, fileName)

            // decodeStream returns null on a bounds-only pass by contract, so the value to
            // null-check here is the stream, never the decode result. Letting the elvis see
            // the decode result makes this function fail for every image ever attached.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsRead = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
                bounds.outWidth > 0 && bounds.outHeight > 0
            } ?: return@withContext null
            if (!boundsRead) return@withContext null

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > MAX_DIMENSION ||
                bounds.outHeight / sampleSize > MAX_DIMENSION
            ) {
                sampleSize *= 2
            }

            // Read the orientation before decoding, from its own stream. BitmapFactory returns
            // pixels in stored order and never consults EXIF, and compress() writes a JPEG with
            // no EXIF at all — so without this the tag is read by nobody and then destroyed, and
            // the photo is sideways for good. Invisible on a screenshot, fatal to OCR on a photo.
            val orientation = readOrientation(uri)

            val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            } ?: return@withContext null

            val bitmap = decoded.withOrientationApplied(orientation)
            try {
                FileOutputStream(outFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
            } finally {
                bitmap.recycle()
            }

            fileName
        } catch (_: Exception) {
            null
        }
    }

    /**
     * The EXIF orientation of [uri], or [ExifInterface.ORIENTATION_NORMAL] when it has none.
     *
     * The framework `ExifInterface` rather than the AndroidX one: it has read from a stream since
     * API 24, which covers this app's `minSdk 26`, and one tag does not justify a dependency. If
     * formats beyond JPEG ever matter, `androidx.exifinterface` is the drop-in.
     *
     * Never throws. An image with unreadable metadata should still attach, just unrotated.
     */
    private fun readOrientation(uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

    /**
     * Bakes [orientation] into the pixels, returning the receiver unchanged when there is nothing
     * to do. Recycles the original once it has been superseded.
     *
     * Baking rather than writing the tag onto the output is deliberate: Compose, Tesseract and the
     * ZIP export then agree without any of them having to interpret EXIF, and an exported archive
     * stays a folder of plainly readable JPEGs.
     */
    private fun Bitmap.withOrientationApplied(orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            // Transpose and transverse are a rotation plus a mirror. Rare from phone cameras,
            // but scanner apps emit them, and half-applying one is as wrong as ignoring it.
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return this
        }
        val reoriented = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        if (reoriented != this) recycle()
        return reoriented
    }

    suspend fun delete(fileNames: Collection<String>) = withContext(Dispatchers.IO) {
        fileNames.forEach { name ->
            if (name.isNotEmpty()) fileFor(name).delete()
        }
    }

    /**
     * Removes image files no longer referenced by any note.
     *
     * Deleting a note cascades away its `note_images` rows but has never removed the
     * files, so installs that have been in use for a while can be carrying a lot of
     * dead JPEGs. Run this once at startup, off the main thread.
     *
     * Files newer than [minAgeMillis] are left alone. The database is the authority on
     * what is referenced, but a file can legitimately exist slightly before its row
     * does — an import writes images then inserts rows, and an attachment is saved to
     * disk before the note is saved. Skipping recent files means this can never race
     * with work that is still in flight, at the cost of deferring a cleanup by a day.
     */
    suspend fun deleteOrphans(
        referencedFileNames: Set<String>,
        minAgeMillis: Long = DEFAULT_ORPHAN_MIN_AGE_MS,
    ): Int = withContext(Dispatchers.IO) {
        val onDisk = directory.listFiles()?.filter { it.isFile } ?: return@withContext 0
        val cutoff = System.currentTimeMillis() - minAgeMillis
        var removed = 0
        onDisk.forEach { file ->
            val isOrphan = file.name !in referencedFileNames
            val isSettled = file.lastModified() < cutoff
            if (isOrphan && isSettled && file.delete()) removed++
        }
        removed
    }

    companion object {
        const val DIRECTORY_NAME = "note_images"

        /**
         * Attachments are downscaled to this on the longest edge. Higher than strictly
         * needed for display, because the same file is what on-device OCR reads.
         */
        private const val MAX_DIMENSION = 2560
        private const val JPEG_QUALITY = 85

        private const val DEFAULT_ORPHAN_MIN_AGE_MS = 24L * 60 * 60 * 1000
    }
}
