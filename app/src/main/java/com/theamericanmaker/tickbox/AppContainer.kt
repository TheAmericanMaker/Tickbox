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
import com.theamericanmaker.tickbox.ocr.OcrBuild
import com.theamericanmaker.tickbox.ocr.TextRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    /**
     * Null in the `noOcr` variant, which ships no engine; the UI hides the affordance.
     * See `OcrBuild`, which exists once per flavour.
     */
    val textRecognizer: TextRecognizer?

    /**
     * For work that must finish even though the thing that started it is going away.
     *
     * A ViewModel's own scope is cancelled when its `NavBackStackEntry` is destroyed, which is
     * the same moment a back press navigates — so a write launched there races its own teardown.
     * This scope is tied to the process instead. Use it only for that: anything the user should
     * still see the effect of after leaving the screen.
     *
     * [SupervisorJob] so one failed write cannot take the scope down with it.
     */
    val applicationScope: CoroutineScope
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

    override val textRecognizer: TextRecognizer? by lazy { OcrBuild.createTextRecognizer(context) }

    override val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
