// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.theamericanmaker.tickbox.data.NoteDatabase
import com.theamericanmaker.tickbox.data.NoteImageEntity
import com.theamericanmaker.tickbox.data.NoteImageStore
import com.theamericanmaker.tickbox.data.NoteRepository
import com.theamericanmaker.tickbox.data.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ImportResult(val notesImported: Int, val imagesImported: Int)

/**
 * Reads and writes the ZIP backup: `notes.json` at the archive root, attachments
 * under `images/`.
 *
 * This format is the migration path from Smart Toolkit, so its shape is effectively
 * frozen. Fields may be added — readers use defaulting accessors — but nothing may be
 * renamed, moved or restructured.
 */
class NoteBackupManager(
    private val context: Context,
    private val repository: NoteRepository,
    private val database: NoteDatabase,
    private val imageStore: NoteImageStore,
) {

    suspend fun exportNotes(uri: Uri): Unit = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            exportTo(outputStream)
            // A null stream used to return silently, which the list screen then
            // reported as a successful export.
        } ?: throw IOException("Could not open the selected location for writing.")
    }

    suspend fun importNotes(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            importFrom(inputStream)
        } ?: throw ImportValidationException("Could not read the selected backup file.")
    }

    /** The whole export, minus the ContentResolver — which is what makes it testable. */
    internal suspend fun exportTo(outputStream: OutputStream) {
        val notes = repository.getAllNotesWithItems()
        val allImages = repository.getAllImages()

        ZipOutputStream(BufferedOutputStream(outputStream)).use { zip ->
            val json = buildExportJson(notes, allImages)
            zip.putNextEntry(ZipEntry("notes.json"))
            zip.write(json.toString(2).toByteArray())
            zip.closeEntry()

            for (image in allImages) {
                val imageFile = imageStore.fileFor(image.filePath)
                if (imageFile.exists()) {
                    zip.putNextEntry(ZipEntry("images/${image.filePath}"))
                    imageFile.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    internal suspend fun importFrom(inputStream: InputStream): ImportResult {
        val stagingDir = File(context.cacheDir, "note_import_${System.currentTimeMillis()}")
        return try {
            val validatedArchive = try {
                NoteImportArchive.stageValidatedArchive(inputStream, stagingDir)
            } catch (e: ImportValidationException) {
                throw e
            } catch (_: IOException) {
                throw ImportValidationException("Could not read the selected backup file.")
            } catch (_: Exception) {
                throw ImportValidationException("Could not read the selected backup file.")
            }
            persistValidatedArchive(validatedArchive)
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private fun buildExportJson(notes: List<Note>, allImages: List<NoteImageEntity>): JSONObject {
        val root = JSONObject()
        root.put("version", CURRENT_ARCHIVE_VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        val notesArray = JSONArray()
        for (note in notes) {
            val noteJson = JSONObject()
            noteJson.put("title", note.title)
            noteJson.put("content", note.content)
            noteJson.put("type", note.type.name)
            noteJson.put("category", note.category ?: JSONObject.NULL)
            // Smart Toolkit's exporter omitted this, so colour labels were silently
            // dropped on every export/import round trip.
            noteJson.put("colorLabel", note.colorLabel ?: JSONObject.NULL)
            noteJson.put("isPinned", note.isPinned)
            noteJson.put("iconStyle", note.iconStyle.name)
            noteJson.put("createdAt", note.createdAt)
            noteJson.put("updatedAt", note.updatedAt)

            if (note.checklistItems.isNotEmpty()) {
                val itemsArray = JSONArray()
                for (item in note.checklistItems) {
                    val itemJson = JSONObject()
                    itemJson.put("text", item.text)
                    itemJson.put("isChecked", item.isChecked)
                    itemJson.put("position", item.position)
                    itemJson.put("indentLevel", item.indentLevel)
                    itemsArray.put(itemJson)
                }
                noteJson.put("checklistItems", itemsArray)
            }

            val noteImages = allImages.filter { it.noteId == note.id }
            if (noteImages.isNotEmpty()) {
                val imagesArray = JSONArray()
                for (image in noteImages) {
                    val imageJson = JSONObject()
                    imageJson.put("filename", image.filePath)
                    imageJson.put("position", image.position)
                    imagesArray.put(imageJson)
                }
                noteJson.put("images", imagesArray)
            }

            notesArray.put(noteJson)
        }

        root.put("notes", notesArray)
        return root
    }

    private suspend fun persistValidatedArchive(archive: ValidatedNoteImportArchive): ImportResult {
        val imageDir = imageStore.ensureDirectory()
        val createdFiles = mutableListOf<File>()

        return try {
            database.withTransaction {
                var notesImported = 0
                var imagesImported = 0

                for (bundle in archive.notes) {
                    val noteId = repository.importNote(bundle.note)
                    notesImported++

                    for (imageRef in bundle.images) {
                        val stagedImage = archive.stagedImages[imageRef.archiveName]
                            ?: throw ImportValidationException("Backup is missing an attached image.")
                        val fileName = NoteImportArchive.generateImportedFileName(imageRef.archiveName)
                        val outFile = File(imageDir, fileName)
                        stagedImage.stagedFile.copyTo(outFile, overwrite = false)
                        createdFiles.add(outFile)
                        repository.insertImageEntity(
                            NoteImageEntity(
                                noteId = noteId,
                                filePath = fileName,
                                position = imageRef.position,
                            ),
                        )
                        imagesImported++
                    }
                }

                ImportResult(notesImported = notesImported, imagesImported = imagesImported)
            }
        } catch (e: Exception) {
            createdFiles.forEach { it.delete() }
            throw e
        }
    }
}
