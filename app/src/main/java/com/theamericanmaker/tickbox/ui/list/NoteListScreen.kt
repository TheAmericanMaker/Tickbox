// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ui.list

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.theamericanmaker.tickbox.data.NoteEntity
import com.theamericanmaker.tickbox.data.model.NoteType
import com.theamericanmaker.tickbox.ui.NotesTopBar
import com.theamericanmaker.tickbox.ui.edit.NoteCategorizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val BACKUP_FILE_NAME = "tickbox_notes_backup.zip"

@Composable
fun NoteListScreen(
    onNoteClick: (Long) -> Unit,
    onNewNote: () -> Unit,
    onNewChecklist: () -> Unit,
    viewModel: NoteListViewModel = viewModel(factory = NoteListViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? -> uri?.let { viewModel.exportNotes(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let { viewModel.importNotes(it) } }

    LaunchedEffect(Unit) {
        viewModel.message.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(uiState.pendingDeleteNote) {
        if (uiState.pendingDeleteNote != null) {
            val result = snackbarHostState.showSnackbar(
                message = "Note deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            }
        }
    }

    NoteListContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onFilterTypeChange = viewModel::onFilterTypeChange,
        onNoteClick = onNoteClick,
        onDeleteNote = viewModel::deleteNote,
        onTogglePin = viewModel::togglePin,
        onNewNote = onNewNote,
        onNewChecklist = onNewChecklist,
        onExport = { exportLauncher.launch(BACKUP_FILE_NAME) },
        onImport = { importLauncher.launch(arrayOf("application/zip")) },
    )
}

/**
 * Stateless half of the list screen.
 *
 * Split out so previews and UI tests can drive it with hand-built state, without an
 * Application or a ViewModel behind it.
 */
@Composable
fun NoteListContent(
    uiState: NoteListUiState,
    snackbarHostState: SnackbarHostState,
    onSearchQueryChange: (String) -> Unit,
    onFilterTypeChange: (NoteType?) -> Unit,
    onNoteClick: (Long) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    onTogglePin: (NoteEntity) -> Unit,
    onNewNote: () -> Unit,
    onNewChecklist: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    val bottomScrollBuffer = (LocalConfiguration.current.screenHeightDp * 0.35f).dp

    Scaffold(
        topBar = {
            NotesTopBar(
                title = "Tickbox",
                actions = {
                    IconButton(
                        onClick = {
                            showSearch = !showSearch
                            if (!showSearch) {
                                searchText = ""
                                onSearchQueryChange("")
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Export notes") },
                            onClick = {
                                showMenu = false
                                onExport()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Import notes") },
                            onClick = {
                                showMenu = false
                                onImport()
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            val isChecklistFilter = uiState.filterType == NoteType.CHECKLIST
            FloatingActionButton(onClick = { if (isChecklistFilter) onNewChecklist() else onNewNote() }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = if (isChecklistFilter) "New checklist" else "New note",
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { text ->
                        searchText = text
                        onSearchQueryChange(text)
                    },
                    label = { Text("Search notes") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                )
            }

            FilterChipRow(
                selected = uiState.filterType,
                onFilterTypeChange = onFilterTypeChange,
            )

            if (uiState.notes.isEmpty()) {
                EmptyState(uiState = uiState)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomScrollBuffer),
                ) {
                    val pinned = uiState.notes.filter { it.isPinned }
                    val unpinned = uiState.notes.filterNot { it.isPinned }

                    if (pinned.isNotEmpty()) {
                        item(key = "pinned_header") { SectionHeader("Pinned", isPrimary = true) }
                        items(pinned, key = { it.id }) { note ->
                            SwipeToDismissNoteCard(
                                note = note,
                                onClick = { onNoteClick(note.id) },
                                onDelete = { onDeleteNote(note) },
                                onTogglePin = { onTogglePin(note) },
                            )
                        }
                        if (unpinned.isNotEmpty()) {
                            item(key = "other_header") { SectionHeader("Other", isPrimary = false) }
                        }
                    }

                    items(unpinned, key = { it.id }) { note ->
                        SwipeToDismissNoteCard(
                            note = note,
                            onClick = { onNoteClick(note.id) },
                            onDelete = { onDeleteNote(note) },
                            onTogglePin = { onTogglePin(note) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, isPrimary: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = if (isPrimary) 8.dp else 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun FilterChipRow(
    selected: NoteType?,
    onFilterTypeChange: (NoteType?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onFilterTypeChange(null) },
            label = { Text("All") },
        )
        FilterChip(
            selected = selected == NoteType.TEXT,
            onClick = { onFilterTypeChange(if (selected == NoteType.TEXT) null else NoteType.TEXT) },
            label = { Text("Notes") },
            leadingIcon = {
                Icon(Icons.Filled.Notes, contentDescription = null, modifier = Modifier.size(16.dp))
            },
        )
        FilterChip(
            selected = selected == NoteType.CHECKLIST,
            onClick = { onFilterTypeChange(if (selected == NoteType.CHECKLIST) null else NoteType.CHECKLIST) },
            label = { Text("Checklists") },
            leadingIcon = {
                Icon(Icons.Filled.Checklist, contentDescription = null, modifier = Modifier.size(16.dp))
            },
        )
    }
}

/**
 * "Nothing here" and "nothing matched" are different situations, and telling a
 * searching user to tap + reads as the search having broken.
 */
@Composable
private fun EmptyState(uiState: NoteListUiState) {
    val (icon: ImageVector, message: String) = when {
        uiState.searchQuery.isNotBlank() ->
            Icons.Filled.SearchOff to "No notes match \"${uiState.searchQuery}\"."

        uiState.filterType == NoteType.CHECKLIST ->
            Icons.Filled.Checklist to "No checklists yet. Tap + to start one."

        uiState.filterType == NoteType.TEXT ->
            Icons.Filled.Notes to "No text notes yet. Tap + to write one."

        else ->
            Icons.Filled.Notes to "No notes yet. Tap + to write your first one."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwipeToDismissNoteCard(
    note: NoteEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
    ) {
        NoteCard(note = note, onClick = onClick, onDelete = onDelete, onTogglePin = onTogglePin)
    }
}

@Composable
private fun NoteCard(
    note: NoteEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val noteColor = note.colorLabel?.let { NoteCategorizer.getNoteColor(it) }
    val categoryColor = note.category?.let { NoteCategorizer.getCategoryColor(it) }
    val effectiveColor = noteColor ?: categoryColor

    val surfaceColor = MaterialTheme.colorScheme.surface
    val cardContainerColor = if (effectiveColor != null) {
        // Blended rather than translucent: a see-through card lets the red delete
        // background behind the swipe bleed through the note.
        lerp(surfaceColor, effectiveColor, 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val isChecklist = note.type == NoteType.CHECKLIST.name

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Text(note.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                if (isChecklist) {
                    Text("Checklist", maxLines = 1, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        text = note.content.take(80),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            overlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    Text(formatter.format(Date(note.updatedAt)), style = MaterialTheme.typography.labelSmall)
                    val label = note.colorLabel ?: note.category
                    if (label != null) {
                        Text(
                            text = " · $label",
                            style = MaterialTheme.typography.labelSmall,
                            color = effectiveColor ?: MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            leadingContent = {
                Icon(
                    imageVector = if (isChecklist) Icons.Filled.Checklist else Icons.Filled.Notes,
                    contentDescription = null,
                    tint = effectiveColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                Row {
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (note.isPinned) "Unpin" else "Pin",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
    }
}
