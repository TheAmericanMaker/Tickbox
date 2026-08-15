// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.theamericanmaker.tickbox.data.model.ChecklistIconStyle
import com.theamericanmaker.tickbox.data.model.ChecklistItem
import com.theamericanmaker.tickbox.data.model.Note
import com.theamericanmaker.tickbox.data.model.NoteType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteRepositoryTest {

    private lateinit var db: NoteDatabase
    private lateinit var repository: NoteRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = NoteRepository(db, db.noteDao(), db.checklistItemDao(), db.noteImageDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun checklist(vararg texts: String) = Note(
        title = "list",
        type = NoteType.CHECKLIST,
        checklistItems = texts.mapIndexed { i, t -> ChecklistItem(text = t, position = i) },
    )

    /**
     * The regression test for the destructive rewrite this repository replaced: saving
     * repeatedly must keep existing row ids, or every autosave churns the whole table
     * and item identity means nothing.
     */
    @Test
    fun checklistItemIdsSurviveRepeatedSaves() = runBlocking {
        val noteId = repository.saveNote(checklist("a", "b", "c"))
        val idsAfterFirst = db.checklistItemDao().getItemsForNoteOnce(noteId).map { it.id }

        val loaded = repository.getNoteWithItems(noteId)!!
        val edited = loaded.copy(
            checklistItems = loaded.checklistItems.mapIndexed { i, item ->
                if (i == 1) item.copy(text = "b-edited") else item
            },
        )
        repository.saveNote(edited)

        val after = db.checklistItemDao().getItemsForNoteOnce(noteId)
        assertEquals(idsAfterFirst, after.map { it.id })
        assertEquals(listOf("a", "b-edited", "c"), after.map { it.text })
    }

    @Test
    fun reconcileInsertsNewAndDeletesRemovedItems() = runBlocking {
        val noteId = repository.saveNote(checklist("a", "b", "c"))
        val loaded = repository.getNoteWithItems(noteId)!!
        val originalIds = loaded.checklistItems.map { it.id }

        // Drop "b", add "d" — "a" and "c" must keep their ids.
        val edited = loaded.copy(
            checklistItems = listOf(
                loaded.checklistItems[0],
                loaded.checklistItems[2],
                ChecklistItem(text = "d"),
            ),
        )
        repository.saveNote(edited)

        val after = db.checklistItemDao().getItemsForNoteOnce(noteId)
        assertEquals(listOf("a", "c", "d"), after.map { it.text })
        assertEquals(originalIds[0], after[0].id)
        assertEquals(originalIds[2], after[1].id)
        assertTrue(after[2].id !in originalIds)
    }

    @Test
    fun positionsAreRewrittenFromListOrder() = runBlocking {
        val noteId = repository.saveNote(checklist("a", "b", "c"))
        val loaded = repository.getNoteWithItems(noteId)!!

        val reordered = loaded.copy(checklistItems = loaded.checklistItems.reversed())
        repository.saveNote(reordered)

        val after = db.checklistItemDao().getItemsForNoteOnce(noteId)
        assertEquals(listOf("c", "b", "a"), after.map { it.text })
        assertEquals(listOf(0, 1, 2), after.map { it.position })
    }

    @Test
    fun deleteNoteReturnsTheImageFilesItOrphaned() = runBlocking {
        val noteId = repository.saveNote(Note(title = "with images"))
        repository.addImage(noteId, "one.jpg", 0)
        repository.addImage(noteId, "two.jpg", 1)

        val orphaned = repository.deleteNote(noteId)
        assertEquals(setOf("one.jpg", "two.jpg"), orphaned.toSet())
        assertTrue(db.noteImageDao().getAllImages().isEmpty())
    }

    @Test
    fun unknownEnumStringsFallBackToDefaults() = runBlocking {
        val id = db.noteDao().insert(NoteEntity(title = "odd", type = "BOGUS", iconStyle = "WEIRD"))
        val note = repository.getNoteWithItems(id)!!
        assertEquals(NoteType.TEXT, note.type)
        assertEquals(ChecklistIconStyle.CHECKBOX, note.iconStyle)
    }

    @Test
    fun importNoteAlwaysInserts() = runBlocking {
        val note = checklist("a").copy(title = "dup")
        repository.importNote(note)
        repository.importNote(note)
        assertEquals(2, db.noteDao().getAllNotesOnce().size)
    }

    @Test
    fun togglePinFlipsOnlyThatNote() = runBlocking {
        val a = repository.saveNote(Note(title = "a"))
        val b = repository.saveNote(Note(title = "b"))

        repository.togglePin(a, pinned = true)

        assertTrue(db.noteDao().getNoteById(a)!!.isPinned)
        assertTrue(!db.noteDao().getNoteById(b)!!.isPinned)
    }

    @Test
    fun saveNoteStampsUpdatedAtButKeepsCreatedAt() = runBlocking {
        val id = repository.saveNote(Note(title = "t", createdAt = 12345, updatedAt = 12345))
        val stored = db.noteDao().getNoteById(id)!!
        assertEquals(12345, stored.createdAt)
        assertNotEquals(12345, stored.updatedAt)
    }
}
