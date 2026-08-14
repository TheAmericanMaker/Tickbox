// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Starts at version 1. Smart Toolkit's database reached version 5 through four
 * hand-written migrations, but none of them can ever run here: this is a different
 * applicationId, so no older database exists on any device. The schema below is the
 * end state those migrations were building toward, minus the cross-tool history table.
 *
 * Schemas are exported to `app/schemas` and committed, so the first real migration
 * has a baseline to test against.
 */
@Database(
    entities = [NoteEntity::class, ChecklistItemEntity::class, NoteImageEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    abstract fun checklistItemDao(): ChecklistItemDao

    abstract fun noteImageDao(): NoteImageDao

    companion object {
        const val DATABASE_NAME = "tickbox.db"

        fun create(context: Context): NoteDatabase =
            Room.databaseBuilder(context, NoteDatabase::class.java, DATABASE_NAME).build()
    }
}
