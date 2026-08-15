// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ui.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.theamericanmaker.tickbox.container
import com.theamericanmaker.tickbox.data.NoteImageStore
import com.theamericanmaker.tickbox.data.NoteRepository
import com.theamericanmaker.tickbox.data.UserPreferencesRepository
import com.theamericanmaker.tickbox.data.model.ChecklistIconStyle
import com.theamericanmaker.tickbox.data.model.ChecklistItem
import com.theamericanmaker.tickbox.data.model.Note
import com.theamericanmaker.tickbox.data.model.NoteImage
import com.theamericanmaker.tickbox.data.model.NoteType
import com.theamericanmaker.tickbox.ocr.TextRecognizer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

/** Debounce between the last edit and an automatic save. */
private const val AUTOSAVE_DELAY_MS = 2_000L

/** Indentation is capped at one level for 1.0. */
private const val MAX_INDENT_LEVEL = 1

private const val MAX_IMAGES_PER_NOTE = 5

data class ChecklistItemUiState(
    val id: Long = 0,
    /** Stable key for freshly added items, which have no database id yet. */
    val tempId: String = UUID.randomUUID().toString(),
    val text: String = "",
    val isChecked: Boolean = false,
    val indentLevel: Int = 0,
)

data class NoteImageUiState(
    val id: Long = 0,
    val filePath: String = "",
    val isNew: Boolean = false,
)

data class NoteEditUiState(
    val title: String = "",
    val content: String = "",
    val type: NoteType = NoteType.TEXT,
    val category: String? = null,
    val colorLabel: String? = null,
    val isPinned: Boolean = false,
    val iconStyle: ChecklistIconStyle = ChecklistIconStyle.CHECKBOX,
    val checklistItems: List<ChecklistItemUiState> = emptyList(),
    val images: List<NoteImageUiState> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val isNew: Boolean = true,
    val isLoaded: Boolean = false,
    val isExtractingText: Boolean = false,
)

class NoteEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: NoteRepository,
    private val preferences: UserPreferencesRepository,
    private val imageStore: NoteImageStore,
    private val textRecognizer: TextRecognizer?,
) : ViewModel() {

    private val noteId: Long = savedStateHandle.get<String>("noteId")?.toLongOrNull() ?: -1L
    private val initialType: String = savedStateHandle.get<String>("type") ?: NoteType.TEXT.name

    private val _uiState = MutableStateFlow(NoteEditUiState())
    val uiState: StateFlow<NoteEditUiState> = _uiState.asStateFlow()

    private val _focusItemIndex = MutableSharedFlow<Int>()
    val focusItemIndex = _focusItemIndex.asSharedFlow()

    private val _contentExternalUpdate = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val contentExternalUpdate = _contentExternalUpdate.asSharedFlow()

    private val _message = MutableSharedFlow<String>()
    val message = _message.asSharedFlow()

    val ocrAvailable: Boolean = textRecognizer != null

    val ocrHintShown: StateFlow<Boolean> = preferences.ocrHintShown
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val dictationDisclosureAcknowledged: StateFlow<Boolean> =
        preferences.dictationDisclosureAcknowledged
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var savedNoteId: Long = noteId
    private var autoSaveJob: Job? = null
    private var isDirty = false

    init {
        if (noteId > 0) {
            viewModelScope.launch {
                repository.getNoteWithItems(noteId)?.let { note ->
                    _uiState.value = NoteEditUiState(
                        title = note.title,
                        content = note.content,
                        type = note.type,
                        category = note.category,
                        colorLabel = note.colorLabel,
                        isPinned = note.isPinned,
                        iconStyle = note.iconStyle,
                        checklistItems = note.checklistItems.map {
                            ChecklistItemUiState(
                                id = it.id,
                                text = it.text,
                                isChecked = it.isChecked,
                                indentLevel = it.indentLevel,
                            )
                        }.ifEmpty { listOf(ChecklistItemUiState()) },
                        images = note.images.map { NoteImageUiState(id = it.id, filePath = it.filePath) },
                        createdAt = note.createdAt,
                        isNew = false,
                        isLoaded = true,
                    )
                }
            }
        } else {
            val type = NoteType.fromName(initialType)
            _uiState.value = NoteEditUiState(
                type = type,
                checklistItems = if (type == NoteType.CHECKLIST) listOf(ChecklistItemUiState()) else emptyList(),
                isLoaded = true,
            )
        }
    }

    fun dismissOcrHint() {
        viewModelScope.launch { preferences.setOcrHintShown() }
    }

    fun acknowledgeDictationDisclosure() {
        viewModelScope.launch { preferences.acknowledgeDictationDisclosure() }
    }

    fun onTitleChange(title: String) {
        _uiState.update { it.copy(title = title, category = NoteCategorizer.categorize(title)) }
        scheduleAutoSave()
    }

    fun onContentChange(content: String) {
        _uiState.update { it.copy(content = content) }
        scheduleAutoSave()
    }

    fun onToggleType() {
        if (_uiState.value.type == NoteType.TEXT) {
            _uiState.update { state ->
                state.copy(
                    type = NoteType.CHECKLIST,
                    checklistItems = ChecklistConversion.textToItems(state.content),
                    content = "",
                )
            }
        } else {
            val converted = ChecklistConversion.itemsToText(_uiState.value.checklistItems)
            _uiState.update { state ->
                state.copy(
                    type = NoteType.TEXT,
                    content = converted,
                    checklistItems = emptyList(),
                )
            }
            // The content field holds its own TextFieldValue and only follows deliberate
            // external writes. Without this the converted text lands in state and in the
            // database but the editor shows the empty body it had as a checklist, which
            // reads as having lost the note — and typing into it would then make that true.
            viewModelScope.launch { _contentExternalUpdate.emit(converted) }
        }
        scheduleAutoSave()
    }

    fun onChecklistItemTextChange(index: Int, text: String) {
        updateItem(index) { it.copy(text = text) }
    }

    fun onChecklistItemCheckedChange(index: Int, checked: Boolean) {
        updateItem(index) { it.copy(isChecked = checked) }
    }

    fun onIndentItem(index: Int) {
        updateItem(index) { item ->
            if (item.indentLevel < MAX_INDENT_LEVEL) item.copy(indentLevel = item.indentLevel + 1) else item
        }
    }

    fun onOutdentItem(index: Int) {
        updateItem(index) { item ->
            if (item.indentLevel > 0) item.copy(indentLevel = item.indentLevel - 1) else item
        }
    }

    private fun updateItem(index: Int, transform: (ChecklistItemUiState) -> ChecklistItemUiState) {
        _uiState.update { state ->
            if (index !in state.checklistItems.indices) return@update state
            val items = state.checklistItems.toMutableList()
            items[index] = transform(items[index])
            state.copy(checklistItems = items)
        }
        scheduleAutoSave()
    }

    fun onAddChecklistItem(afterIndex: Int = -1) {
        var insertAt = 0
        _uiState.update { state ->
            val items = state.checklistItems.toMutableList()
            insertAt = if (afterIndex >= 0) afterIndex + 1 else items.size
            val inheritedIndent = items.getOrNull(afterIndex)?.indentLevel ?: 0
            items.add(insertAt, ChecklistItemUiState(indentLevel = inheritedIndent))
            state.copy(checklistItems = items)
        }
        viewModelScope.launch { _focusItemIndex.emit(insertAt) }
        // The new row is blank, so this schedules a save that mostly writes nothing. It has to:
        // every other mutation marks the note dirty through here, and without it, adding an item
        // and pressing back would drop the row.
        scheduleAutoSave()
    }

    fun onDeleteChecklistItem(index: Int) {
        _uiState.update { state ->
            if (state.checklistItems.size <= 1 || index !in state.checklistItems.indices) return@update state
            state.copy(checklistItems = state.checklistItems.toMutableList().apply { removeAt(index) })
        }
        scheduleAutoSave()
    }

    fun onReorderChecklistItems(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val items = state.checklistItems.toMutableList()
            if (fromIndex !in items.indices || toIndex !in items.indices) return@update state
            items.add(toIndex, items.removeAt(fromIndex))
            state.copy(checklistItems = items)
        }
        scheduleAutoSave()
    }

    fun addSuggestedItem(text: String) {
        _uiState.update { state ->
            val items = state.checklistItems.toMutableList()
            val firstBlank = items.indexOfLast { it.text.isBlank() }
            val newItem = ChecklistItemUiState(text = text)
            if (firstBlank >= 0) items.add(firstBlank, newItem) else items.add(newItem)
            state.copy(checklistItems = items)
        }
        scheduleAutoSave()
    }

    fun onColorLabelChange(colorLabel: String?) {
        _uiState.update { it.copy(colorLabel = colorLabel) }
        scheduleAutoSave()
    }

    fun onIconStyleChange(iconStyle: ChecklistIconStyle) {
        _uiState.update { it.copy(iconStyle = iconStyle) }
        scheduleAutoSave()
    }

    fun onTogglePinned() {
        _uiState.update { it.copy(isPinned = !it.isPinned) }
        scheduleAutoSave()
    }

    fun imageFile(fileName: String): File = imageStore.fileFor(fileName)

    /**
     * Copies [uri] into the image store.
     *
     * [onFinished] runs once the copy has finished reading, successfully or not. The camera
     * flow needs it: its source is a temporary file the caller owns, and deleting that file
     * before the copy has read it is exactly the race this callback exists to prevent.
     */
    fun addImageFromUri(uri: Uri, onFinished: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                if (_uiState.value.images.size >= MAX_IMAGES_PER_NOTE) return@launch
                val filePath = imageStore.saveFromUri(uri) ?: run {
                    _message.emit("Could not add that image.")
                    return@launch
                }
                _uiState.update { it.copy(images = it.images + NoteImageUiState(filePath = filePath, isNew = true)) }
                scheduleAutoSave()
            } finally {
                onFinished()
            }
        }
    }

    fun removeImage(index: Int) {
        viewModelScope.launch {
            val removed = _uiState.value.images.getOrNull(index) ?: return@launch
            _uiState.update { state ->
                state.copy(images = state.images.filterIndexed { i, _ -> i != index })
            }
            if (removed.id != 0L) repository.deleteImage(removed.id)
            if (removed.filePath.isNotEmpty()) imageStore.delete(listOf(removed.filePath))
            scheduleAutoSave()
        }
    }

    fun extractTextFrom(fileName: String) {
        val recognizer = textRecognizer ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isExtractingText = true) }
            val result = recognizer.recognize(imageStore.fileFor(fileName))
            _uiState.update { it.copy(isExtractingText = false) }
            result
                .onSuccess { text ->
                    if (text.isBlank()) {
                        _message.emit("No text found in that image.")
                    } else {
                        onExtractedText(text)
                    }
                }
                .onFailure { error ->
                    // Smart Toolkit swallowed this, so the button appeared to do nothing.
                    _message.emit("Could not extract text: ${error.message ?: "unknown error"}")
                }
        }
    }

    fun onExtractedText(text: String) {
        val lines = TextRecognizer.splitIntoItems(text).map { it.capitalizeFirst() }
        if (lines.isEmpty()) return
        insertLines(lines, separator = "\n")
    }

    fun onDictatedText(text: String) {
        val state = _uiState.value
        if (state.type == NoteType.CHECKLIST) {
            val lines = text.split(Regex("[.\\n]"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { it.capitalizeFirst() }
            insertLines(lines, separator = " ")
        } else {
            val separator = if (state.content.isNotBlank()) " " else ""
            _uiState.update { it.copy(content = it.content + separator + text) }
            scheduleAutoSave()
        }
    }

    private fun insertLines(lines: List<String>, separator: String) {
        val state = _uiState.value
        if (state.type == NoteType.CHECKLIST) {
            _uiState.update { current ->
                val items = current.checklistItems.toMutableList()
                val insertAt = items.indexOfLast { it.text.isBlank() }.takeIf { it >= 0 } ?: items.size
                lines.forEachIndexed { offset, line ->
                    items.add(insertAt + offset, ChecklistItemUiState(text = line))
                }
                current.copy(checklistItems = items)
            }
        } else {
            val prefix = if (state.content.isNotBlank()) separator else ""
            val newContent = state.content + prefix + lines.joinToString("\n")
            _uiState.update { it.copy(content = newContent) }
            viewModelScope.launch { _contentExternalUpdate.emit(newContent) }
        }
        scheduleAutoSave()
    }

    private fun String.capitalizeFirst(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    fun applyTemplate(title: String, type: NoteType, items: List<String>) {
        val newContent = if (type == NoteType.TEXT) items.joinToString("\n") else ""
        _uiState.update { state ->
            state.copy(
                title = title,
                type = type,
                content = newContent,
                checklistItems = if (type == NoteType.CHECKLIST) {
                    items.map { ChecklistItemUiState(text = it) } + ChecklistItemUiState()
                } else {
                    emptyList()
                },
            )
        }
        if (type == NoteType.TEXT) {
            viewModelScope.launch { _contentExternalUpdate.emit(newContent) }
        }
        scheduleAutoSave()
    }

    private fun scheduleAutoSave() {
        isDirty = true
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            saveNow()
        }
    }

    /**
     * Called on back press.
     *
     * Skipping the save when nothing changed is what stops merely opening a note from rewriting
     * it. An unconditional save here stamps `updatedAt`, and because the list sorts on that,
     * browsing the library silently reorders it and `updatedAt` comes to mean "last looked at"
     * rather than "last changed".
     *
     * This is only safe because every user-initiated mutation routes through [scheduleAutoSave],
     * which is what sets the flag. A mutation that skips it would be dropped here instead of
     * being caught by the old unconditional save — so new mutating functions must call it, even
     * when the change looks too small to save.
     */
    fun save() {
        if (!isDirty) return
        viewModelScope.launch { saveNow() }
    }

    /**
     * Serialised deliberately.
     *
     * The autosave timer and the back-press both reach [saveNow], and on a note that has not
     * been inserted yet they would each read `savedNoteId` as 0 and insert separately, leaving
     * two copies. The window is only a few milliseconds wide — around the point where the timer
     * fires and back is pressed together — which makes it rare enough to be missed by hand and
     * not worth trying to reproduce. Serialising the two paths removes it outright.
     */
    private val saveMutex = Mutex()

    private suspend fun saveNow() = saveMutex.withLock {
        val state = _uiState.value
        // Cleared against this snapshot, so an edit arriving mid-save re-marks the note and the
        // next save picks it up rather than the change being swallowed.
        isDirty = false
        val hasContent = state.title.isNotBlank() ||
            state.content.isNotBlank() ||
            state.checklistItems.any { it.text.isNotBlank() }
        if (!hasContent) return

        val note = Note(
            id = if (savedNoteId > 0) savedNoteId else 0,
            title = state.title,
            content = state.content,
            type = state.type,
            category = state.category,
            colorLabel = state.colorLabel,
            isPinned = state.isPinned,
            iconStyle = state.iconStyle,
            checklistItems = state.checklistItems.mapIndexed { index, item ->
                // Carrying the id through is what lets the repository update rows in
                // place instead of recreating the whole checklist on every autosave.
                ChecklistItem(
                    id = item.id,
                    text = item.text,
                    isChecked = item.isChecked,
                    position = index,
                    indentLevel = item.indentLevel,
                )
            },
            images = state.images.mapIndexed { index, image ->
                NoteImage(id = image.id, filePath = image.filePath, position = index)
            },
            createdAt = state.createdAt,
            updatedAt = System.currentTimeMillis(),
        )

        val id = repository.saveNote(note)
        if (savedNoteId <= 0) {
            savedNoteId = id
            _uiState.update { it.copy(isNew = false) }
        }

        persistNewImages()
        writeBackChecklistItemIds()
    }

    private suspend fun persistNewImages() {
        _uiState.value.images.forEachIndexed { index, image ->
            if (image.isNew && image.id == 0L) {
                val imageId = repository.addImage(savedNoteId, image.filePath, index)
                _uiState.update { current ->
                    val updated = current.images.toMutableList()
                    val position = updated.indexOfFirst { it.filePath == image.filePath }
                    if (position >= 0) {
                        updated[position] = updated[position].copy(id = imageId, isNew = false)
                        current.copy(images = updated)
                    } else {
                        current
                    }
                }
            }
        }
    }

    /**
     * Gives freshly inserted checklist items their database ids.
     *
     * Skipped if the list changed while the save was in flight — assigning by index
     * would attach the wrong ids, and the next save will fill them in anyway.
     */
    private suspend fun writeBackChecklistItemIds() {
        if (_uiState.value.type != NoteType.CHECKLIST) return
        val ids = repository.getChecklistItemIdsInOrder(savedNoteId)
        _uiState.update { current ->
            if (current.checklistItems.size != ids.size) return@update current
            current.copy(
                checklistItems = current.checklistItems.mapIndexed { index, item ->
                    if (item.id == 0L) item.copy(id = ids[index]) else item
                },
            )
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NoteEditViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    repository = container.noteRepository,
                    preferences = container.preferences,
                    imageStore = container.imageStore,
                    textRecognizer = container.textRecognizer,
                )
            }
        }
    }
}
