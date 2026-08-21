// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Checked/total counts for one note's checklist, for the list screen's progress line. */
data class ChecklistProgress(
    val noteId: Long,
    val total: Int,
    val checked: Int,
)

@Dao
interface ChecklistItemDao {
    @Query("SELECT * FROM checklist_items WHERE noteId = :noteId ORDER BY position ASC")
    suspend fun getItemsForNoteOnce(noteId: Long): List<ChecklistItemEntity>

    // SUM over a 0/1 integer column; GROUP BY guarantees at least one row per group,
    // so the sum can never be null.
    //
    // Blank rows are excluded deliberately (#41). The editor keeps an empty row as the
    // "type the next item" affordance, and pressing Enter after the last item leaves one
    // behind, so counting every row made a fully ticked list read "3 of 4 done" forever —
    // an empty row cannot be ticked, so "All N done" was unreachable. A blank row is an
    // editing affordance, not outstanding work.
    //
    // A checklist of nothing but blank rows drops out of this result entirely, which the
    // list screen already handles: no row means `progress == null` and the card says
    // "Checklist" rather than a count of nothing.
    @Query(
        "SELECT noteId, COUNT(*) AS total, SUM(isChecked) AS checked " +
            "FROM checklist_items WHERE TRIM(text) != '' GROUP BY noteId",
    )
    fun getProgressByNote(): Flow<List<ChecklistProgress>>

    @Insert
    suspend fun insert(item: ChecklistItemEntity): Long

    @Update
    suspend fun update(item: ChecklistItemEntity)

    @Query("DELETE FROM checklist_items WHERE id = :itemId")
    suspend fun deleteById(itemId: Long)

    @Query("DELETE FROM checklist_items WHERE noteId = :noteId")
    suspend fun deleteAllForNote(noteId: Long)
}
