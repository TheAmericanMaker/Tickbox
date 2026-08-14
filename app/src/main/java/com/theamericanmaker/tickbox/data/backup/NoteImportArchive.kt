// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.data.backup

import com.theamericanmaker.tickbox.data.model.ChecklistIconStyle
import com.theamericanmaker.tickbox.data.model.ChecklistItem
import com.theamericanmaker.tickbox.data.model.Note
import com.theamericanmaker.tickbox.data.model.NoteType
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Parses and validates a backup archive.
 *
 * This is the app's only untrusted-input surface, so it is deliberately paranoid:
 * it rejects directory entries, absolute paths, backslashes and `..`, allows only
 * `notes.json` and flat `images/<name>` entries, bounds every entry and the archive
 * as a whole, and stages to a scratch directory so nothing touches real storage
 * until the whole archive has been validated.
 *
 * The archive format is shared with Smart Toolkit's notepad — that is how existing
 * notes migrate — so changes here must stay backward compatible. Every field is read
 * with an `opt*` accessor and a default, which is what makes added fields harmless in
 * both directions.
 */

internal const val MAX_IMPORT_NOTES_JSON_BYTES = 2L * 1024 * 1024
internal const val MAX_IMPORT_IMAGE_BYTES = 10L * 1024 * 1024
internal const val MAX_IMPORT_TOTAL_BYTES = 64L * 1024 * 1024
internal const val MAX_IMPORT_IMAGE_COUNT = 200
private const val MAX_IMAGES_PER_NOTE = 5

/**
 * Archives written by this app. Version 1 archives came from Smart Toolkit and lack
 * `colorLabel`; version 2 adds it. Reading stays tolerant of both.
 */
internal const val CURRENT_ARCHIVE_VERSION = 2

private val SAFE_IMAGE_NAME_REGEX = Regex("[A-Za-z0-9._-]{1,128}")

internal data class StagedImportImage(
    val archiveName: String,
    val stagedFile: File,
    val sizeBytes: Long,
)

internal data class ImportedImageReference(
    val archiveName: String,
    val position: Int,
)

internal data class ImportedNoteBundle(
    val note: Note,
    val images: List<ImportedImageReference>,
)

internal data class ValidatedNoteImportArchive(
    val notes: List<ImportedNoteBundle>,
    val stagedImages: Map<String, StagedImportImage>,
)

internal class ImportValidationException(message: String) : IllegalArgumentException(message)

internal object NoteImportArchive {
    fun stageValidatedArchive(inputStream: InputStream, stagingDir: File): ValidatedNoteImportArchive {
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()

        var totalBytesRead = 0L
        var notesJson: String? = null
        val stagedImages = linkedMapOf<String, StagedImportImage>()

        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.isDirectory) {
                    throw ImportValidationException("Backup contains unsupported folders.")
                }

                when (val entryType = classifyEntry(entry.name)) {
                    ArchiveEntryType.NotesJson -> {
                        if (notesJson != null) {
                            throw ImportValidationException("Backup contains duplicate notes data.")
                        }
                        val output = ByteArrayOutputStream()
                        copyEntry(
                            input = zip,
                            output = output,
                            entryLimitBytes = MAX_IMPORT_NOTES_JSON_BYTES,
                            onBytesRead = { totalBytesRead = updateTotalBytes(totalBytesRead, it) },
                            limitMessage = "Backup notes data is too large.",
                        )
                        notesJson = output.toString(Charsets.UTF_8.name())
                    }

                    is ArchiveEntryType.Image -> {
                        if (stagedImages.size >= MAX_IMPORT_IMAGE_COUNT) {
                            throw ImportValidationException("Backup contains too many images.")
                        }
                        if (stagedImages.containsKey(entryType.fileName)) {
                            throw ImportValidationException("Backup contains duplicate images.")
                        }

                        val extension = entryType.fileName.substringAfterLast('.', "bin")
                        val stagedFile = File(stagingDir, "${UUID.randomUUID()}.$extension")
                        FileOutputStream(stagedFile).use { output ->
                            val writtenBytes = copyEntry(
                                input = zip,
                                output = output,
                                entryLimitBytes = MAX_IMPORT_IMAGE_BYTES,
                                onBytesRead = { totalBytesRead = updateTotalBytes(totalBytesRead, it) },
                                limitMessage = "Backup image is too large.",
                            )
                            stagedImages[entryType.fileName] = StagedImportImage(
                                archiveName = entryType.fileName,
                                stagedFile = stagedFile,
                                sizeBytes = writtenBytes,
                            )
                        }
                    }
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val manifest = notesJson ?: throw ImportValidationException("Backup is missing notes.json.")

        val notes = parseNotesManifest(
            jsonContent = manifest,
            availableImages = stagedImages.keys,
        )

        return ValidatedNoteImportArchive(
            notes = notes,
            stagedImages = stagedImages,
        )
    }

    fun generateImportedFileName(originalName: String): String {
        val extension = originalName.substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{1,10}")) }
            ?: "bin"
        return "${UUID.randomUUID()}.$extension"
    }

    private fun parseNotesManifest(
        jsonContent: String,
        availableImages: Set<String>,
    ): List<ImportedNoteBundle> {
        val root = try {
            JSONObject(jsonContent)
        } catch (_: Exception) {
            throw ImportValidationException("Backup notes data is invalid.")
        }

        // A clear message beats silently dropping fields we do not understand yet.
        if (root.optInt("version", 1) > CURRENT_ARCHIVE_VERSION) {
            throw ImportValidationException("This backup was made by a newer version of Tickbox.")
        }

        val notesArray = root.optJSONArray("notes")
            ?: throw ImportValidationException("Backup notes data is invalid.")

        val notes = mutableListOf<ImportedNoteBundle>()
        for (i in 0 until notesArray.length()) {
            val noteJson = try {
                notesArray.getJSONObject(i)
            } catch (_: Exception) {
                throw ImportValidationException("Backup notes data is invalid.")
            }

            val checklistItems = mutableListOf<ChecklistItem>()
            if (noteJson.has("checklistItems")) {
                val itemsArray = try {
                    noteJson.getJSONArray("checklistItems")
                } catch (_: Exception) {
                    throw ImportValidationException("Backup notes data is invalid.")
                }

                for (j in 0 until itemsArray.length()) {
                    val itemJson = try {
                        itemsArray.getJSONObject(j)
                    } catch (_: Exception) {
                        throw ImportValidationException("Backup notes data is invalid.")
                    }
                    checklistItems.add(
                        ChecklistItem(
                            text = itemJson.optString("text", ""),
                            isChecked = itemJson.optBoolean("isChecked", false),
                            position = itemJson.optInt("position", j),
                            indentLevel = itemJson.optInt("indentLevel", 0),
                        ),
                    )
                }
            }

            val images = mutableListOf<ImportedImageReference>()
            if (noteJson.has("images")) {
                val imagesArray = try {
                    noteJson.getJSONArray("images")
                } catch (_: Exception) {
                    throw ImportValidationException("Backup notes data is invalid.")
                }

                if (imagesArray.length() > MAX_IMAGES_PER_NOTE) {
                    throw ImportValidationException("Backup note has too many images.")
                }

                for (j in 0 until imagesArray.length()) {
                    val imageJson = try {
                        imagesArray.getJSONObject(j)
                    } catch (_: Exception) {
                        throw ImportValidationException("Backup notes data is invalid.")
                    }
                    val fileName = imageJson.optString("filename", "")
                    validateImageFileName(fileName)
                    if (fileName !in availableImages) {
                        throw ImportValidationException("Backup is missing an attached image.")
                    }
                    images.add(
                        ImportedImageReference(
                            archiveName = fileName,
                            position = imageJson.optInt("position", j),
                        ),
                    )
                }
            }

            notes.add(
                ImportedNoteBundle(
                    note = Note(
                        title = noteJson.optString("title", ""),
                        content = noteJson.optString("content", ""),
                        type = NoteType.fromName(noteJson.optString("type")),
                        category = noteJson.optionalString("category"),
                        // Absent in every Smart Toolkit archive, which never exported it.
                        // JSONObject.isNull() is true for a missing key as well as an
                        // explicit null, so both land on null without special-casing.
                        colorLabel = noteJson.optionalString("colorLabel"),
                        isPinned = noteJson.optBoolean("isPinned", false),
                        iconStyle = ChecklistIconStyle.fromName(noteJson.optString("iconStyle")),
                        checklistItems = checklistItems,
                        createdAt = noteJson.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = noteJson.optLong("updatedAt", System.currentTimeMillis()),
                    ),
                    images = images,
                ),
            )
        }

        return notes
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun classifyEntry(entryName: String): ArchiveEntryType {
        if (
            entryName.isBlank() ||
            entryName.startsWith("/") ||
            entryName.startsWith("\\") ||
            entryName.contains('\\') ||
            entryName.contains("..")
        ) {
            throw ImportValidationException("Backup contains invalid file paths.")
        }

        if (entryName == "notes.json") {
            return ArchiveEntryType.NotesJson
        }

        if (!entryName.startsWith("images/")) {
            throw ImportValidationException("Backup contains unsupported files.")
        }

        val fileName = entryName.removePrefix("images/")
        if (fileName.isBlank() || fileName.contains('/')) {
            throw ImportValidationException("Backup contains invalid image paths.")
        }
        validateImageFileName(fileName)

        return ArchiveEntryType.Image(fileName)
    }

    private fun validateImageFileName(fileName: String) {
        if (!SAFE_IMAGE_NAME_REGEX.matches(fileName)) {
            throw ImportValidationException("Backup contains invalid image names.")
        }
    }

    private fun updateTotalBytes(current: Long, delta: Long): Long {
        val next = current + delta
        if (next > MAX_IMPORT_TOTAL_BYTES) {
            throw ImportValidationException("Backup file is too large.")
        }
        return next
    }

    private fun copyEntry(
        input: ZipInputStream,
        output: OutputStream,
        entryLimitBytes: Long,
        onBytesRead: (Long) -> Unit,
        limitMessage: String,
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L

        while (true) {
            val read = input.read(buffer)
            if (read == -1) break

            total += read
            if (total > entryLimitBytes) {
                throw ImportValidationException(limitMessage)
            }

            onBytesRead(read.toLong())
            output.write(buffer, 0, read)
        }

        output.flush()
        return total
    }

    private sealed class ArchiveEntryType {
        data object NotesJson : ArchiveEntryType()

        data class Image(val fileName: String) : ArchiveEntryType()
    }
}
