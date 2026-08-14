// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.data

import androidx.room.withTransaction
import com.theamericanmaker.tickbox.data.model.ChecklistItem
import com.theamericanmaker.tickbox.data.model.ChecklistIconStyle
import com.theamericanmaker.tickbox.data.model.Note
import com.theamericanmaker.tickbox.data.model.NoteImage
import com.theamericanmaker.tickbox.data.model.NoteType
import kotlinx.coroutines.flow.Flow

class NoteRepository(
    private val database: NoteDatabase,
    private val noteDao: NoteDao,
    private val checklistItemDao: ChecklistItemDao,
    private val noteImageDao: NoteImageDao,
) {
    fun getAllNotes(): Flow<List<NoteEntity>> = noteDao.getAllNotes()

    fun getNotesByType(type: String): Flow<List<NoteEntity>> = noteDao.getNotesByType(type)

    fun searchNotes(query: String): Flow<List<NoteEntity>> = noteDao.searchNotes(query)

    suspend fun getNoteWithItems(noteId: Long): Note? {
        val entity = noteDao.getNoteById(noteId) ?: return null
        val checklistItems = if (entity.type == NoteType.CHECKLIST.name) {
            checklistItemDao.getItemsForNoteOnce(noteId).map { it.toDomain() }
        } else {
            emptyList()
        }
        val images = noteImageDao.getImagesForNoteOnce(noteId).map { it.toDomain() }
        return entity.toDomain(checklistItems, images)
    }

    suspend fun saveNote(note: Note): Long = database.withTransaction {
        val entity = note.toEntity(updatedAt = System.currentTimeMillis())

        val noteId = if (note.id == 0L) {
            noteDao.insert(entity)
        } else {
            noteDao.update(entity)
            note.id
        }

        if (note.type == NoteType.CHECKLIST) {
            reconcileChecklistItems(noteId, note.checklistItems)
        }

        noteId
    }

    /**
     * Updates rows that still exist, inserts new ones, and deletes the rest.
     *
     * The obvious implementation — delete every row then re-insert — is what Smart
     * Toolkit did, and because autosave fires every two seconds while typing it churned
     * the whole table constantly and made [ChecklistItem.id] meaningless. Stable ids
     * matter: they are what lets the list key on identity rather than position, which is
     * in turn what makes drag-to-reorder and focus survive a save.
     */
    private suspend fun reconcileChecklistItems(noteId: Long, items: List<ChecklistItem>) {
        val existingIds = checklistItemDao.getItemsForNoteOnce(noteId).map { it.id }.toSet()
        val keptIds = mutableSetOf<Long>()

        items.forEachIndexed { index, item ->
            val row = ChecklistItemEntity(
                id = item.id,
                noteId = noteId,
                text = item.text,
                isChecked = item.isChecked,
                position = index,
                indentLevel = item.indentLevel,
            )
            if (item.id != 0L && item.id in existingIds) {
                checklistItemDao.update(row)
                keptIds += item.id
            } else {
                keptIds += checklistItemDao.insert(row.copy(id = 0))
            }
        }

        (existingIds - keptIds).forEach { checklistItemDao.deleteById(it) }
    }

    /**
     * Deletes the note and returns the filenames of any images it owned.
     *
     * The foreign key cascade removes the `note_images` rows, but nothing removes the
     * files themselves — so the caller is expected to hand these to
     * [NoteImageStore.delete] once any undo window has closed.
     */
    suspend fun deleteNote(noteId: Long): List<String> {
        val filePaths = noteImageDao.getFilePathsForNote(noteId)
        noteDao.deleteById(noteId)
        return filePaths
    }

    suspend fun togglePin(noteId: Long, pinned: Boolean) {
        val note = noteDao.getNoteById(noteId) ?: return
        noteDao.update(note.copy(isPinned = pinned))
    }

    suspend fun getAllNotesWithItems(): List<Note> {
        return noteDao.getAllNotesOnce().map { entity ->
            val items = if (entity.type == NoteType.CHECKLIST.name) {
                checklistItemDao.getItemsForNoteOnce(entity.id).map { it.toDomain() }
            } else {
                emptyList()
            }
            val images = noteImageDao.getImagesForNoteOnce(entity.id).map { it.toDomain() }
            entity.toDomain(items, images)
        }
    }

    /** Always inserts. Importing the same archive twice duplicates its notes. */
    suspend fun importNote(note: Note): Long {
        val noteId = noteDao.insert(note.toEntity(id = 0, updatedAt = note.updatedAt))

        note.checklistItems.forEachIndexed { index, item ->
            checklistItemDao.insert(
                ChecklistItemEntity(
                    noteId = noteId,
                    text = item.text,
                    isChecked = item.isChecked,
                    position = index,
                    indentLevel = item.indentLevel,
                ),
            )
        }

        return noteId
    }

    suspend fun addImage(noteId: Long, filePath: String, position: Int): Long =
        noteImageDao.insert(NoteImageEntity(noteId = noteId, filePath = filePath, position = position))

    suspend fun insertImageEntity(image: NoteImageEntity): Long = noteImageDao.insert(image)

    suspend fun deleteImage(imageId: Long) = noteImageDao.deleteById(imageId)

    suspend fun getAllImages(): List<NoteImageEntity> = noteImageDao.getAllImages()

    suspend fun getAllImageFilePaths(): List<String> = noteImageDao.getAllFilePaths()
}

private fun Note.toEntity(id: Long = this.id, updatedAt: Long): NoteEntity = NoteEntity(
    id = id,
    title = title,
    content = content,
    type = type.name,
    category = category,
    colorLabel = colorLabel,
    isPinned = isPinned,
    iconStyle = iconStyle.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun NoteEntity.toDomain(
    checklistItems: List<ChecklistItem> = emptyList(),
    images: List<NoteImage> = emptyList(),
): Note = Note(
    id = id,
    title = title,
    content = content,
    type = NoteType.fromName(type),
    category = category,
    colorLabel = colorLabel,
    isPinned = isPinned,
    iconStyle = ChecklistIconStyle.fromName(iconStyle),
    checklistItems = checklistItems,
    images = images,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun ChecklistItemEntity.toDomain(): ChecklistItem = ChecklistItem(
    id = id,
    text = text,
    isChecked = isChecked,
    position = position,
    indentLevel = indentLevel,
)

private fun NoteImageEntity.toDomain(): NoteImage = NoteImage(
    id = id,
    filePath = filePath,
    position = position,
    addedAt = addedAt,
)
