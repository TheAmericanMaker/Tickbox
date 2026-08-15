// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.theamericanmaker.tickbox.data.NoteDatabase
import com.theamericanmaker.tickbox.data.NoteImageStore
import com.theamericanmaker.tickbox.data.NoteRepository
import com.theamericanmaker.tickbox.data.model.ChecklistIconStyle
import com.theamericanmaker.tickbox.data.model.ChecklistItem
import com.theamericanmaker.tickbox.data.model.Note
import com.theamericanmaker.tickbox.data.model.NoteType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

/**
 * Seed → export → wipe → import → deep compare. Proves the archive format is lossless
 * in both directions — including `colorLabel`, the field Smart Toolkit's exporter
 * silently dropped, which is the bug this format's version 2 exists to fix.
 */
@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {

    private lateinit var db: NoteDatabase
    private lateinit var repository: NoteRepository
    private lateinit var imageStore: NoteImageStore
    private lateinit var manager: NoteBackupManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = NoteRepository(db, db.noteDao(), db.checklistItemDao(), db.noteImageDao())
        imageStore = NoteImageStore(context)
        manager = NoteBackupManager(context, repository, db, imageStore)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun everyFieldSurvivesTheRoundTrip() = runBlocking {
        // A text note exercising colour, pin, and non-ASCII text.
        repository.importNote(
            Note(
                title = "Grocery run 🛒 & more",
                content = "Don't forget the <good> bread",
                type = NoteType.TEXT,
                category = "Shopping",
                colorLabel = "Green",
                isPinned = true,
                createdAt = 1_000,
                updatedAt = 2_000,
            ),
        )
        // A checklist exercising items, indent, checked state and icon style.
        val checklistId = repository.importNote(
            Note(
                title = "Packing",
                type = NoteType.CHECKLIST,
                iconStyle = ChecklistIconStyle.STAR,
                checklistItems = listOf(
                    ChecklistItem(text = "Passport", isChecked = true, position = 0),
                    ChecklistItem(text = "Charger", position = 1, indentLevel = 1),
                ),
                createdAt = 3_000,
                updatedAt = 4_000,
            ),
        )
        // An attachment: the backup path never decodes images, so any bytes will do.
        val imageBytes = byteArrayOf(9, 8, 7, 6, 5)
        imageStore.ensureDirectory().resolve("test.jpg").writeBytes(imageBytes)
        repository.addImage(checklistId, "test.jpg", 0)

        val archive = ByteArrayOutputStream()
        manager.exportTo(archive)

        db.clearAllTables()
        assertEquals(0, repository.getAllNotesWithItems().size)

        val result = manager.importFrom(archive.toByteArray().inputStream())
        assertEquals(2, result.notesImported)
        assertEquals(1, result.imagesImported)

        val notes = repository.getAllNotesWithItems().sortedBy { it.title }

        val list = notes[0]
        assertEquals("Packing", list.title)
        assertEquals(NoteType.CHECKLIST, list.type)
        assertEquals(ChecklistIconStyle.STAR, list.iconStyle)
        assertEquals(3_000, list.createdAt)
        assertEquals(4_000, list.updatedAt)
        assertEquals(listOf("Passport", "Charger"), list.checklistItems.map { it.text })
        assertEquals(listOf(true, false), list.checklistItems.map { it.isChecked })
        assertEquals(listOf(0, 1), list.checklistItems.map { it.indentLevel })
        assertEquals(1, list.images.size)
        // Imported files get fresh names by design; the content must be identical.
        assertNotEquals("test.jpg", list.images.single().filePath)
        assertArrayEquals(imageBytes, imageStore.fileFor(list.images.single().filePath).readBytes())

        val text = notes[1]
        assertEquals("Grocery run 🛒 & more", text.title)
        assertEquals("Don't forget the <good> bread", text.content)
        assertEquals("Shopping", text.category)
        assertEquals("Green", text.colorLabel)
        assertTrue(text.isPinned)
        assertEquals(1_000, text.createdAt)
        assertEquals(2_000, text.updatedAt)
    }

    @Test
    fun exportWritesVersionTwo() = runBlocking {
        repository.importNote(Note(title = "t"))
        val archive = ByteArrayOutputStream()
        manager.exportTo(archive)

        val json = readNotesJson(archive.toByteArray())
        assertTrue(json.contains("\"version\": 2"))
    }

    @Test
    fun importingTheSameArchiveTwiceDuplicates() = runBlocking {
        repository.importNote(Note(title = "once"))
        val archive = ByteArrayOutputStream()
        manager.exportTo(archive)

        manager.importFrom(archive.toByteArray().inputStream())
        manager.importFrom(archive.toByteArray().inputStream())

        // Documented 1.0 behaviour, not a bug: there is no dedup key in the format.
        assertEquals(3, repository.getAllNotesWithItems().size)
    }

    private fun readNotesJson(archiveBytes: ByteArray): String {
        java.util.zip.ZipInputStream(archiveBytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "notes.json") return zip.readBytes().decodeToString()
                entry = zip.nextEntry
            }
        }
        error("archive has no notes.json")
    }
}
