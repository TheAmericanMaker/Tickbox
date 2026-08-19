// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ui.edit

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.theamericanmaker.tickbox.data.NoteDatabase
import com.theamericanmaker.tickbox.data.NoteImageStore
import com.theamericanmaker.tickbox.data.NoteRepository
import com.theamericanmaker.tickbox.data.UserPreferencesRepository
import com.theamericanmaker.tickbox.data.model.NoteType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * State-machine tests: every function here mutates [NoteEditUiState] synchronously, so
 * no time control is needed. The autosave coroutines these calls schedule sit unrun on
 * the test dispatcher, which is exactly the point — persistence has its own tests at
 * the repository layer, where it was device-verified.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NoteEditViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: NoteDatabase
    private lateinit var repository: NoteRepository
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var imageStore: NoteImageStore
    private lateinit var applicationScope: CoroutineScope

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = NoteRepository(db, db.noteDao(), db.checklistItemDao(), db.noteImageDao())
        preferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatcher + Job())) {
                File(
                    ApplicationProvider.getApplicationContext<Context>().cacheDir,
                    "test-${System.nanoTime()}.preferences_pb",
                )
            },
        )
        imageStore = NoteImageStore(context)
        // Deliberately its own Job, not a child of any ViewModel's — that separation is
        // the thing under test in `saveOnBackSurvivesTheViewModelBeingCleared`.
        applicationScope = CoroutineScope(dispatcher + Job())
    }

    @After
    fun tearDown() {
        applicationScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    /**
     * Waits for work launched into [applicationScope] to actually finish.
     *
     * `advanceUntilIdle()` only drains the test scheduler. `NoteRepository.saveNote` suspends on
     * Room's `withTransaction`, which runs on Room's own executor, so the write can still be in
     * flight when the scheduler reports itself idle — the assertion then reads the database before
     * the commit. That passes on a fast machine and fails on a slow CI runner, which is exactly
     * what it did. Joining the children waits for completion rather than for quiet.
     */
    private suspend fun awaitApplicationScopeWork() {
        applicationScope.coroutineContext.job.children.toList().forEach { it.join() }
    }

    private fun newChecklistViewModel(): NoteEditViewModel = NoteEditViewModel(
        savedStateHandle = SavedStateHandle(mapOf("noteId" to "-1", "type" to "CHECKLIST")),
        repository = repository,
        preferences = preferences,
        imageStore = imageStore,
        textRecognizer = null,
        applicationScope = applicationScope,
    )

    @Test
    fun newChecklistStartsWithOneBlankItem() {
        val vm = newChecklistViewModel()
        val state = vm.uiState.value
        assertEquals(NoteType.CHECKLIST, state.type)
        assertEquals(1, state.checklistItems.size)
        assertTrue(state.checklistItems.single().text.isBlank())
    }

    @Test
    fun indentIsClampedToOneLevel() {
        val vm = newChecklistViewModel()
        vm.onChecklistItemTextChange(0, "item")
        vm.onIndentItem(0)
        vm.onIndentItem(0)
        assertEquals(1, vm.uiState.value.checklistItems[0].indentLevel)

        vm.onOutdentItem(0)
        vm.onOutdentItem(0)
        assertEquals(0, vm.uiState.value.checklistItems[0].indentLevel)
    }

    @Test
    fun addedItemInheritsIndentAndInsertsAfter() {
        val vm = newChecklistViewModel()
        vm.onChecklistItemTextChange(0, "parent")
        vm.onIndentItem(0)
        vm.onAddChecklistItem(afterIndex = 0)

        val items = vm.uiState.value.checklistItems
        assertEquals(2, items.size)
        assertEquals(1, items[1].indentLevel)
        assertTrue(items[1].text.isBlank())
    }

    @Test
    fun theLastItemCannotBeDeleted() {
        val vm = newChecklistViewModel()
        vm.onDeleteChecklistItem(0)
        assertEquals(1, vm.uiState.value.checklistItems.size)
    }

    @Test
    fun reorderMovesByKeyNotPosition() {
        val vm = newChecklistViewModel()
        vm.onChecklistItemTextChange(0, "a")
        vm.onAddChecklistItem()
        vm.onChecklistItemTextChange(1, "b")
        vm.onAddChecklistItem()
        vm.onChecklistItemTextChange(2, "c")

        val keys = vm.uiState.value.checklistItems.map { it.tempId }
        vm.onReorderChecklistItems(fromKey = keys[0], toKey = keys[2])

        assertEquals(listOf("b", "c", "a"), vm.uiState.value.checklistItems.map { it.text })
    }

    @Test
    fun reorderWithAnUnknownKeyIsANoOp() {
        val vm = newChecklistViewModel()
        vm.onChecklistItemTextChange(0, "only")
        vm.onReorderChecklistItems(fromKey = "nope", toKey = vm.uiState.value.checklistItems[0].tempId)
        assertEquals(listOf("only"), vm.uiState.value.checklistItems.map { it.text })
    }

    @Test
    fun toggleTypeConvertsItemsToContentAndBack() {
        val vm = newChecklistViewModel()
        vm.onChecklistItemTextChange(0, "Milk")
        vm.onAddChecklistItem()
        vm.onChecklistItemTextChange(1, "Eggs")

        vm.onToggleType()
        assertEquals(NoteType.TEXT, vm.uiState.value.type)
        assertEquals("Milk\nEggs", vm.uiState.value.content)

        vm.onToggleType()
        assertEquals(NoteType.CHECKLIST, vm.uiState.value.type)
        assertEquals(listOf("Milk", "Eggs"), vm.uiState.value.checklistItems.map { it.text })
    }

    @Test
    fun toggleTypeRestoresTicksAndIndentWhenTheBodyIsUntouched() {
        val vm = newChecklistViewModel()
        vm.onChecklistItemTextChange(0, "Bread")
        vm.onAddChecklistItem()
        vm.onChecklistItemTextChange(1, "Sourdough")
        vm.onIndentItem(1)
        vm.onChecklistItemCheckedChange(0, true)

        vm.onToggleType()
        // The body carries the words and the indent, and no tick marks.
        assertEquals("Bread\n  Sourdough", vm.uiState.value.content)

        vm.onToggleType()
        val items = vm.uiState.value.checklistItems
        assertEquals(listOf("Bread", "Sourdough"), items.map { it.text })
        assertEquals(listOf(true, false), items.map { it.isChecked })
        assertEquals(listOf(0, 1), items.map { it.indentLevel })
    }

    @Test
    fun toggleTypeDropsRestoredIdsSoTheRowsAreInsertedAfresh() {
        // Saving as text deletes the rows, so carrying their ids back would hand saveNote
        // updates for rows that no longer exist and the items would silently vanish.
        val vm = newChecklistViewModel()
        vm.onChecklistItemTextChange(0, "Bread")
        vm.onToggleType()
        vm.onToggleType()
        assertTrue(vm.uiState.value.checklistItems.all { it.id == 0L })
    }

    @Test
    fun toggleTypeFallsBackToParsingWhenTheBodyWasEdited() {
        val vm = newChecklistViewModel()
        vm.onChecklistItemTextChange(0, "Bread")
        vm.onChecklistItemCheckedChange(0, true)

        vm.onToggleType()
        vm.onContentChange("Bread\nMilk")          // edited: the remembered list is now a lie

        vm.onToggleType()
        val items = vm.uiState.value.checklistItems
        assertEquals(listOf("Bread", "Milk"), items.map { it.text })
        assertTrue("an edited body cannot restore ticks", items.none { it.isChecked })
    }

    @Test
    fun aPastedMarkdownChecklistBecomesItemsWithTicks() {
        val vm = newChecklistViewModel()
        vm.onToggleType()
        vm.onContentChange("- [x] Milk\n- [ ] Bread\n  - [ ] Sourdough")

        vm.onToggleType()
        val items = vm.uiState.value.checklistItems
        assertEquals(listOf("Milk", "Bread", "Sourdough"), items.map { it.text })
        assertEquals(listOf(true, false, false), items.map { it.isChecked })
        assertEquals(listOf(0, 0, 1), items.map { it.indentLevel })
    }

    @Test
    fun dictatedTextSplitsIntoChecklistItemsOnSentenceBreaks() {
        val vm = newChecklistViewModel()
        vm.onDictatedText("buy milk. get eggs. call mom")

        val texts = vm.uiState.value.checklistItems.map { it.text }.filter { it.isNotBlank() }
        assertEquals(listOf("Buy milk", "Get eggs", "Call mom"), texts)
    }

    /**
     * Cancelling `viewModelScope` is exactly what popping the back stack does, and the back
     * press saves and navigates in the same breath. While the write lived in that scope it was
     * racing its own teardown; losing meant Room rolled the transaction back, silently.
     *
     * Written to fail against the old code: with the save in `viewModelScope`, the launched
     * coroutine is still queued on the test dispatcher when the cancel lands, so it never runs
     * and the note is never inserted.
     */
    @Test
    fun saveOnBackSurvivesTheViewModelBeingCleared() = runTest(dispatcher) {
        val vm = newChecklistViewModel()
        vm.onTitleChange("typed just before back")

        vm.save()
        vm.viewModelScope.cancel()
        advanceUntilIdle()
        awaitApplicationScopeWork()

        val saved = repository.getAllNotesWithItems()
        assertEquals(1, saved.size)
        assertEquals("typed just before back", saved.single().title)
    }

    /**
     * The autosave path still reaches disk after the change that moved its write into
     * [applicationScope].
     *
     * Deliberately does **not** cancel `viewModelScope` mid-write. Doing so requires cancelling in
     * the window between the debounce timer firing and the handed-over write running, and that
     * interleaving is not controllable here: the debounce length is computed from
     * `System.currentTimeMillis()` while the delay itself is virtual time, so how much of the work
     * `advanceTimeBy` drains varies by machine. A first version of this test asserted the
     * cancellation property and passed locally 8/8 while failing on CI with
     * `expected:<1> but was:<0>`.
     *
     * The cancellation property is pinned by [saveOnBackSurvivesTheViewModelBeingCleared], which
     * needs no timer and so has no such window. What is left here is the other half worth having:
     * the debounced write still persists, and it persists through the application scope.
     */
    @Test
    fun autosaveStillReachesDiskAfterTheDebounce() = runTest(dispatcher) {
        val vm = newChecklistViewModel()
        vm.onChecklistItemTextChange(0, "milk")

        advanceUntilIdle()
        awaitApplicationScopeWork()

        val saved = repository.getAllNotesWithItems()
        assertEquals(1, saved.size)
        assertEquals(listOf("milk"), saved.single().checklistItems.map { it.text })
    }
}


