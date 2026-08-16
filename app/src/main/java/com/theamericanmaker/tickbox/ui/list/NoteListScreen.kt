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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.theamericanmaker.tickbox.data.ChecklistProgress
import com.theamericanmaker.tickbox.data.NoteEntity
import com.theamericanmaker.tickbox.data.ThemeMode
import com.theamericanmaker.tickbox.data.model.NoteType
import com.theamericanmaker.tickbox.ui.NotesTopBar
import com.theamericanmaker.tickbox.ui.edit.NoteCategorizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val BACKUP_FILE_NAME = "tickbox_notes_backup.zip"

@Composable
fun NoteListScreen(
    onNoteClick: (Long) -> Unit,
    onNewNote: () -> Unit,
    onNewChecklist: () -> Unit,
    onOpenHelp: () -> Unit,
    viewModel: NoteListViewModel = viewModel(factory = NoteListViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
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
        themeMode = themeMode,
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
        onThemeModeChange = viewModel::setThemeMode,
        onOpenHelp = onOpenHelp,
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
    themeMode: ThemeMode,
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
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenHelp: () -> Unit,
) {
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }
    var showAppearanceDialog by rememberSaveable { mutableStateOf(false) }

    if (showAppearanceDialog) {
        AppearanceDialog(
            selected = themeMode,
            onSelect = onThemeModeChange,
            onDismiss = { showAppearanceDialog = false },
        )
    }
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
                            text = { Text("Appearance") },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (themeMode) {
                                        ThemeMode.LIGHT -> Icons.Filled.LightMode
                                        ThemeMode.DARK -> Icons.Filled.DarkMode
                                        ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                                    },
                                    contentDescription = null,
                                )
                            },
                            // The current mode as trailing text, so the menu answers "what is it
                            // set to" without anyone having to open the dialog to find out.
                            trailingIcon = {
                                Text(
                                    text = themeMode.label(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = {
                                showMenu = false
                                showAppearanceDialog = true
                            },
                        )
                        HorizontalDivider()
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
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Help & about") },
                            leadingIcon = {
                                Icon(Icons.Filled.HelpOutline, contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                onOpenHelp()
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = {
            // Lifted clear of the FAB: Scaffold puts both at the bottom and the snackbar spans
            // the full width, so Undo sat underneath the + button and could be tapped through.
            // 56dp FAB plus its 16dp margin.
            SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = 72.dp))
        },
        floatingActionButton = {
            // A small menu rather than filter-dependent behaviour: the FAB used to
            // create whichever type matched the active filter chip, which meant the
            // same button silently did different things.
            Box {
                FloatingActionButton(onClick = { showFabMenu = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add a note or checklist",
                    )
                }
                DropdownMenu(expanded = showFabMenu, onDismissRequest = { showFabMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("New checklist") },
                        leadingIcon = { Icon(Icons.Filled.Checklist, contentDescription = null) },
                        onClick = {
                            showFabMenu = false
                            onNewChecklist()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("New note") },
                        leadingIcon = { Icon(Icons.Filled.Notes, contentDescription = null) },
                        onClick = {
                            showFabMenu = false
                            onNewNote()
                        },
                    )
                }
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
                                progress = uiState.checklistProgress[note.id],
                                onClick = { onNoteClick(note.id) },
                                onDelete = { onDeleteNote(note) },
                                onTogglePin = { onTogglePin(note) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        if (unpinned.isNotEmpty()) {
                            item(key = "other_header") { SectionHeader("Other", isPrimary = false) }
                        }
                    }

                    items(unpinned, key = { it.id }) { note ->
                        SwipeToDismissNoteCard(
                            note = note,
                            progress = uiState.checklistProgress[note.id],
                            onClick = { onNoteClick(note.id) },
                            onDelete = { onDeleteNote(note) },
                            onTogglePin = { onTogglePin(note) },
                            modifier = Modifier.animateItem(),
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
    progress: ChecklistProgress?,
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
        // The inset belongs here rather than on the card inside. Padding the card left the
        // delete background filling the whole row behind it, so the red showed as a frame
        // around every card at rest, with no swipe in progress.
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CardDefaults.shape)
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
        NoteCard(
            note = note,
            progress = progress,
            onClick = onClick,
            onDelete = onDelete,
            onTogglePin = onTogglePin,
        )
    }
}

@Composable
private fun NoteCard(
    note: NoteEntity,
    progress: ChecklistProgress?,
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
        // No padding here: the swipe container owns the inset, so that the delete background
        // behind this card is confined to the card's own bounds.
        modifier = Modifier
            .fillMaxWidth()
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
                    // The list is where you decide which checklist needs attention, so
                    // the card says how far along each one is rather than just what it is.
                    Column {
                        Text(
                            text = when {
                                progress == null || progress.total == 0 -> "Checklist"
                                progress.checked == progress.total ->
                                    "All ${progress.total} done"
                                else -> "${progress.checked} of ${progress.total} done"
                            },
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (progress != null && progress.total > 0) {
                            LinearProgressIndicator(
                                progress = { progress.checked / progress.total.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp, end = 8.dp),
                            )
                        }
                    }
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
                    Text(formatNoteDate(note.updatedAt), style = MaterialTheme.typography.labelSmall)
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

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private fun ThemeMode.description(): String = when (this) {
    ThemeMode.SYSTEM -> "Match the device setting"
    ThemeMode.LIGHT -> "Always light"
    ThemeMode.DARK -> "Always dark"
}

/**
 * Light/dark/system choice.
 *
 * The preference has been stored and applied since the extraction; nothing ever wrote it, so the
 * setting existed with no way to reach it. This is that way.
 *
 * Selection applies immediately rather than on confirm, and the dialog stays open: the whole
 * subject of this dialog is what the app looks like, and you can watch it change behind the
 * scrim. That makes "Done" a dismissal rather than a commit, so there is nothing to cancel.
 */
@Composable
private fun AppearanceDialog(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Appearance") },
        text = {
            // selectableGroup so TalkBack announces these as one set ("2 of 3") rather than
            // three unrelated controls.
            Column(modifier = Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            // The whole row is the target, not just the 20dp radio.
                            .selectable(
                                selected = mode == selected,
                                onClick = { onSelect(mode) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = mode == selected,
                            // Null: the row above already handles the click and carries the
                            // semantics. A handler here would announce the control twice.
                            onClick = null,
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(mode.label(), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = mode.description(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/**
 * "Today" and "Yesterday" beat a calendar date for the notes people actually touch,
 * which in a notes app is most of the visible list.
 */
private fun formatNoteDate(epochMillis: Long): String {
    val noteDate = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (noteDate) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> noteDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }
}
