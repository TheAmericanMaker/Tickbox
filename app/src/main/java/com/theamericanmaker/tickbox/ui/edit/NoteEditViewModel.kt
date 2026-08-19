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
import kotlinx.coroutines.CoroutineScope
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

/** Ceiling on the debounce, so sustained typing still reaches disk. See `scheduleAutoSave`. */
private const val AUTOSAVE_MAX_WAIT_MS = 10_000L

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
    private val applicationScope: CoroutineScope,
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

    val indentHintShown: StateFlow<Boolean> = preferences.indentHintShown
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val ocrHintShown: StateFlow<Boolean> = preferences.ocrHintShown
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val dictationDisclosureAcknowledged: StateFlow<Boolean> =
        preferences.dictationDisclosureAcknowledged
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var savedNoteId: Long = noteId
    private var autoSaveJob: Job? = null
    private var isDirty = false

    /** When the current run of unsaved edits began, or 0 when there is nothing pending. */
    private var dirtySince = 0L

    /**
     * The checklist as it was the last time this note was converted to text, and the body that
     * conversion produced.
     *
     * Held for as long as the editor is open, which is the span of the thing people actually do:
     * flip to a note to read the list, then flip back. Leaving the note forgets it, and that is
     * the honest outcome — persisting it would mean a schema column carrying a shadow copy of a
     * list that the text may since have contradicted.
     */
    private var itemsBeforeConversion: List<ChecklistItemUiState>? = null
    private var textAtConversion: String? = null

    /**
     * The checklist for [content] — the remembered one if the body has not been touched since it
     * was written, otherwise whatever the text parses to.
     *
     * Restored items lose their database ids on purpose. Saving the note as text deleted those
     * rows, so reusing the ids would hand `saveNote` updates for rows that no longer exist and
     * the items would silently vanish. Fresh ids cost nothing that anyone can see.
     */
    private fun itemsFor(content: String): List<ChecklistItemUiState> {
        val remembered = itemsBeforeConversion
        if (remembered != null && textAtConversion == content) {
            itemsBeforeConversion = null
            textAtConversion = null
            return remembered.map { it.copy(id = 0) }
        }
        return ChecklistConversion.textToItems(content)
    }

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

    fun dismissIndentHint() {
        viewModelScope.launch { preferences.setIndentHintShown() }
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
                    checklistItems = itemsFor(state.content),
                    content = "",
                )
            }
        } else {
            val converted = ChecklistConversion.itemsToText(_uiState.value.checklistItems)
            // Ticks cannot live in the note body without turning it into a form, so they are
            // held here instead, alongside the exact text they produced. If that text comes
            // back untouched, nothing was really edited and they can be put back.
            itemsBeforeConversion = _uiState.value.checklistItems
            textAtConversion = converted
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

    /**
     * Unchecks everything, in place.
     *
     * Positions are untouched, so a weekly shopping list comes back in the order it was built
     * rather than reshuffled by whatever order things were ticked off in.
     */
    fun onUncheckAll() {
        if (_uiState.value.checklistItems.none { it.isChecked }) return
        _uiState.update { state ->
            state.copy(checklistItems = state.checklistItems.map { it.copy(isChecked = false) })
        }
        scheduleAutoSave()
    }

    /**
     * Removes every checked item.
     *
     * Keeps one blank row if that would empty the list, because the editor assumes a checklist is
     * never completely empty — `canDelete` guards the same invariant on the per-row delete.
     */
    fun onDeleteChecked() {
        if (_uiState.value.checklistItems.none { it.isChecked }) return
        _uiState.update { state ->
            val remaining = state.checklistItems.filterNot { it.isChecked }
            state.copy(
                checklistItems = remaining.ifEmpty { listOf(ChecklistItemUiState()) },
            )
        }
        scheduleAutoSave()
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

    /**
     * Moves the item with [fromKey] to the position of the item with [toKey].
     *
     * Keys ([ChecklistItemUiState.tempId]) rather than indices, deliberately: the
     * checklist renders as two filtered sections — unchecked, then checked — so a row's
     * display position does not match its index in this list, and an index-based move
     * computed from what is on screen would move the wrong item. Keys are unambiguous
     * regardless of how the display slices the list. Only unchecked items are drag
     * targets, and they appear in the same relative order on screen as here, so
     * remove-and-insert lands the item exactly where the drag showed it.
     */
    fun onReorderChecklistItems(fromKey: Any?, toKey: Any?) {
        _uiState.update { state ->
            val items = state.checklistItems.toMutableList()
            val fromIndex = items.indexOfFirst { it.tempId == fromKey }
            val toIndex = items.indexOfFirst { it.tempId == toKey }
            if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return@update state
            items.add(toIndex, items.removeAt(fromIndex))
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

    /**
     * Debounced, but with a ceiling.
     *
     * The debounce alone had no upper bound: every keystroke cancelled the pending job and
     * restarted the 2 s timer, so anyone typing steadily never reached it. Measured on device,
     * 30 seconds of continuous typing produced zero writes — nothing was lost, because the save
     * fired the moment typing stopped, but the window of unsaved work grew without limit.
     *
     * So the wait is also capped at [AUTOSAVE_MAX_WAIT_MS] measured from the *first* unsaved edit.
     * A typist who never pauses still gets a save every ten seconds; everyone else is unaffected,
     * because a 2 s pause arrives long before the ceiling does.
     */
    private fun scheduleAutoSave() {
        isDirty = true
        if (dirtySince == 0L) dirtySince = System.currentTimeMillis()
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            val waitedSoFar = System.currentTimeMillis() - dirtySince
            val remainingCeiling = (AUTOSAVE_MAX_WAIT_MS - waitedSoFar).coerceAtLeast(0L)
            delay(minOf(AUTOSAVE_DELAY_MS, remainingCeiling))
            // The wait is cancellable — that is the debounce, and a job cancelled here simply
            // loses a race it was going to lose anyway. The write is not: once it starts it
            // has to reach disk even if the screen is being torn down around it.
            applicationScope.launch { saveNow() }
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
     *
     * Runs in [applicationScope], not `viewModelScope`. The caller saves and navigates in the
     * same breath, and navigating pops the back stack, which clears this ViewModel and cancels
     * its scope — so a write launched there would be racing its own teardown, and losing that
     * race means Room rolls the transaction back and the edit is gone with no crash and no
     * message. Rare, because the debounce means most of the note is already on disk, but silent
     * when it happens, which is the kind of bug that gets blamed on the user's memory.
     */
    fun save() {
        if (!isDirty) return
        applicationScope.launch { saveNow() }
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
        dirtySince = 0L
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
        // Write-back assigns generated ids to id-less items by position, which is only
        // sound while the list still matches the snapshot that was saved. A mid-save
        // edit — now including a drag-reorder — re-marks the note dirty, so skip: the
        // next save reconciles those rows as fresh inserts, costing one id churn on a
        // brand-new row instead of ever attaching an id to the wrong item.
        if (!isDirty) writeBackChecklistItemIds()
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
                    applicationScope = container.applicationScope,
                )
            }
        }
    }
}
