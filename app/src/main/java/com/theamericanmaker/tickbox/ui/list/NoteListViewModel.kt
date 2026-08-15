// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ui.list

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.theamericanmaker.tickbox.container
import com.theamericanmaker.tickbox.data.ChecklistProgress
import com.theamericanmaker.tickbox.data.NoteEntity
import com.theamericanmaker.tickbox.data.NoteImageStore
import com.theamericanmaker.tickbox.data.NoteRepository
import com.theamericanmaker.tickbox.data.backup.NoteBackupManager
import com.theamericanmaker.tickbox.data.model.NoteType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NoteListUiState(
    val notes: List<NoteEntity> = emptyList(),
    val searchQuery: String = "",
    val filterType: NoteType? = null,
    val pendingDeleteNote: NoteEntity? = null,
    /** Checked/total per checklist note, so cards can show "3 of 8 done". */
    val checklistProgress: Map<Long, ChecklistProgress> = emptyMap(),
    val isLoaded: Boolean = false,
)

/** How long a deleted note can be brought back before the delete is committed. */
private const val UNDO_WINDOW_MS = 5_000L

class NoteListViewModel(
    private val repository: NoteRepository,
    private val backupManager: NoteBackupManager,
    private val imageStore: NoteImageStore,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val filterType = MutableStateFlow<NoteType?>(null)
    private val pendingDelete = MutableStateFlow<NoteEntity?>(null)
    private var deleteJob: Job? = null

    private val _message = MutableSharedFlow<String>()
    val message = _message.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<NoteListUiState> =
        combine(searchQuery, filterType, pendingDelete) { query, filter, pending ->
            Triple(query, filter, pending)
        }.flatMapLatest { (query, filter, pending) ->
            val notesFlow = when {
                query.isNotBlank() -> repository.searchNotes(query)
                filter != null -> repository.getNotesByType(filter.name)
                else -> repository.getAllNotes()
            }
            notesFlow.combine(repository.checklistProgress()) { notes, progress ->
                NoteListUiState(
                    // The pending note stays in the database until the undo window
                    // closes, so it is filtered out of the list rather than deleted.
                    notes = if (pending != null) notes.filter { it.id != pending.id } else notes,
                    searchQuery = query,
                    filterType = filter,
                    pendingDeleteNote = pending,
                    checklistProgress = progress.associateBy { it.noteId },
                    isLoaded = true,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NoteListUiState())

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onFilterTypeChange(type: NoteType?) {
        filterType.value = type
    }

    /**
     * Hides the note, then commits the delete once the undo window elapses.
     *
     * Deleting a second note while one is still pending commits the first
     * immediately — there is only one undo slot.
     */
    fun deleteNote(note: NoteEntity) {
        val previousPending = pendingDelete.value
        deleteJob?.cancel()
        if (previousPending != null) {
            viewModelScope.launch { commitDelete(previousPending.id) }
        }
        pendingDelete.value = note
        deleteJob = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            commitDelete(note.id)
            pendingDelete.value = null
        }
    }

    private suspend fun commitDelete(noteId: Long) {
        // The cascade drops the note_images rows; the files need removing separately.
        val orphanedFiles = repository.deleteNote(noteId)
        imageStore.delete(orphanedFiles)
    }

    fun undoDelete() {
        deleteJob?.cancel()
        deleteJob = null
        pendingDelete.value = null
    }

    fun togglePin(note: NoteEntity) {
        viewModelScope.launch {
            repository.togglePin(note.id, !note.isPinned)
        }
    }

    fun exportNotes(uri: Uri) {
        viewModelScope.launch {
            try {
                backupManager.exportNotes(uri)
                _message.emit("Notes exported")
            } catch (e: Exception) {
                _message.emit("Export failed: ${e.message}")
            }
        }
    }

    fun importNotes(uri: Uri) {
        viewModelScope.launch {
            try {
                val result = backupManager.importNotes(uri)
                _message.emit(
                    "Imported ${result.notesImported} notes, ${result.imagesImported} images",
                )
            } catch (e: Exception) {
                _message.emit("Import failed: ${e.message ?: "The selected backup file is invalid."}")
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NoteListViewModel(
                    repository = container.noteRepository,
                    backupManager = container.backupManager,
                    imageStore = container.imageStore,
                )
            }
        }
    }
}
