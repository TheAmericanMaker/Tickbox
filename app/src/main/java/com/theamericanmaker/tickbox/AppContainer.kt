// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.theamericanmaker.tickbox.data.NoteDatabase
import com.theamericanmaker.tickbox.data.NoteImageStore
import com.theamericanmaker.tickbox.data.NoteRepository
import com.theamericanmaker.tickbox.data.UserPreferencesRepository
import com.theamericanmaker.tickbox.data.backup.NoteBackupManager
import com.theamericanmaker.tickbox.ocr.TesseractTextRecognizer
import com.theamericanmaker.tickbox.ocr.TextRecognizer

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * The whole dependency graph: one database, one repository, one preferences store,
 * one image store. Small enough that a code generator would cost more than it saves,
 * which is why there is no Hilt here.
 *
 * `by lazy` gives the same construct-once, thread-safe semantics `@Singleton` did.
 */
interface AppContainer {
    val database: NoteDatabase
    val noteRepository: NoteRepository
    val preferences: UserPreferencesRepository
    val imageStore: NoteImageStore
    val backupManager: NoteBackupManager

    /** Null when this build ships without an OCR engine; the UI hides the affordance. */
    val textRecognizer: TextRecognizer?
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val database: NoteDatabase by lazy { NoteDatabase.create(context) }

    override val noteRepository: NoteRepository by lazy {
        NoteRepository(
            database = database,
            noteDao = database.noteDao(),
            checklistItemDao = database.checklistItemDao(),
            noteImageDao = database.noteImageDao(),
        )
    }

    override val preferences: UserPreferencesRepository by lazy {
        UserPreferencesRepository(context.dataStore)
    }

    override val imageStore: NoteImageStore by lazy { NoteImageStore(context) }

    override val backupManager: NoteBackupManager by lazy {
        NoteBackupManager(
            context = context,
            repository = noteRepository,
            database = database,
            imageStore = imageStore,
        )
    }

    override val textRecognizer: TextRecognizer? by lazy { TesseractTextRecognizer(context) }
}

/**
 * Reaches the container from a ViewModel factory.
 *
 * [APPLICATION_KEY] is supplied by the framework, survives process death, and needs no
 * provider at the root of the composable tree — which is why this is a plain extension
 * rather than a CompositionLocal.
 */
val CreationExtras.container: AppContainer
    get() = (this[APPLICATION_KEY] as TickboxApp).container
