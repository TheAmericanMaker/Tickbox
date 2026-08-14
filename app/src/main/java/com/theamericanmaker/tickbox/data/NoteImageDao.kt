// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface NoteImageDao {
    @Query("SELECT * FROM note_images WHERE noteId = :noteId ORDER BY position ASC")
    suspend fun getImagesForNoteOnce(noteId: Long): List<NoteImageEntity>

    /** Used when deleting a note, so the caller can clean up the files off disk. */
    @Query("SELECT filePath FROM note_images WHERE noteId = :noteId")
    suspend fun getFilePathsForNote(noteId: Long): List<String>

    @Query("SELECT filePath FROM note_images")
    suspend fun getAllFilePaths(): List<String>

    @Insert
    suspend fun insert(image: NoteImageEntity): Long

    @Query("DELETE FROM note_images WHERE id = :imageId")
    suspend fun deleteById(imageId: Long)

    @Query("SELECT * FROM note_images")
    suspend fun getAllImages(): List<NoteImageEntity>
}
