// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * `type` and `iconStyle` are stored as plain strings rather than via a Room type
 * converter. That keeps the column format byte-identical to what the backup archive
 * writes, so exports stay interchangeable with Smart Toolkit's.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val type: String = "TEXT",
    val category: String? = null,
    val colorLabel: String? = null,
    val isPinned: Boolean = false,
    val iconStyle: String = "CHECKBOX",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
