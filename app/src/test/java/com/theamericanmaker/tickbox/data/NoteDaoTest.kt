// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteDaoTest {

    private lateinit var db: NoteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun pinnedNotesSortFirstThenByUpdatedAt() = runBlocking {
        db.noteDao().insert(NoteEntity(title = "old", updatedAt = 100))
        db.noteDao().insert(NoteEntity(title = "new", updatedAt = 300))
        db.noteDao().insert(NoteEntity(title = "pinned-old", isPinned = true, updatedAt = 50))

        val titles = db.noteDao().getAllNotes().first().map { it.title }
        assertEquals(listOf("pinned-old", "new", "old"), titles)
    }

    @Test
    fun typeFilterReturnsOnlyThatType() = runBlocking {
        db.noteDao().insert(NoteEntity(title = "t", type = "TEXT"))
        db.noteDao().insert(NoteEntity(title = "c", type = "CHECKLIST"))

        val checklists = db.noteDao().getNotesByType("CHECKLIST").first()
        assertEquals(listOf("c"), checklists.map { it.title })
    }

    @Test
    fun searchMatchesTitleAndContentCaseInsensitively() = runBlocking {
        db.noteDao().insert(NoteEntity(title = "Groceries", content = ""))
        db.noteDao().insert(NoteEntity(title = "other", content = "buy groceries tomorrow"))
        db.noteDao().insert(NoteEntity(title = "unrelated", content = "nothing"))

        val hits = db.noteDao().searchNotes("groc").first()
        assertEquals(2, hits.size)
    }

    @Test
    fun deletingANoteCascadesToItemsAndImages() = runBlocking {
        val noteId = db.noteDao().insert(NoteEntity(title = "n", type = "CHECKLIST"))
        db.checklistItemDao().insert(ChecklistItemEntity(noteId = noteId, text = "a"))
        db.noteImageDao().insert(NoteImageEntity(noteId = noteId, filePath = "x.jpg"))

        db.noteDao().deleteById(noteId)

        assertTrue(db.checklistItemDao().getItemsForNoteOnce(noteId).isEmpty())
        assertTrue(db.noteImageDao().getImagesForNoteOnce(noteId).isEmpty())
    }

    @Test
    fun progressCountsCheckedAndTotalPerNote() = runBlocking {
        val a = db.noteDao().insert(NoteEntity(title = "a", type = "CHECKLIST"))
        val b = db.noteDao().insert(NoteEntity(title = "b", type = "CHECKLIST"))
        db.checklistItemDao().insert(ChecklistItemEntity(noteId = a, text = "1", isChecked = true))
        db.checklistItemDao().insert(ChecklistItemEntity(noteId = a, text = "2", isChecked = false))
        db.checklistItemDao().insert(ChecklistItemEntity(noteId = a, text = "3", isChecked = true))
        db.checklistItemDao().insert(ChecklistItemEntity(noteId = b, text = "1", isChecked = false))

        val progress = db.checklistItemDao().getProgressByNote().first().associateBy { it.noteId }
        assertEquals(3, progress.getValue(a).total)
        assertEquals(2, progress.getValue(a).checked)
        assertEquals(1, progress.getValue(b).total)
        assertEquals(0, progress.getValue(b).checked)
    }
}
