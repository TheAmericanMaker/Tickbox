// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ui.edit

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.theamericanmaker.tickbox.data.model.ChecklistIconStyle
import com.theamericanmaker.tickbox.data.model.ChecklistItem
import com.theamericanmaker.tickbox.data.model.NoteType
import com.theamericanmaker.tickbox.ui.NotesTopBar
import com.theamericanmaker.tickbox.ui.edit.templates.TemplatePickerBottomSheet
import com.theamericanmaker.tickbox.ui.share.NoteShareFormatter
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val DICTATION_TARGET_TITLE = "title"
private const val DICTATION_TARGET_CONTENT = "content"

/** One beat for a newly added row to compose and lay out before it is measured or focused. */
private const val FOCUS_LAYOUT_SETTLE_MS = 100L

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteEditScreen(
    onBack: () -> Unit,
    viewModel: NoteEditViewModel = viewModel(factory = NoteEditViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ocrHintShown by viewModel.ocrHintShown.collectAsStateWithLifecycle()
    val dictationAcknowledged by viewModel.dictationDisclosureAcknowledged.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val bottomScrollBuffer = (LocalConfiguration.current.screenHeightDp * 0.35f).dp
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showTemplates by rememberSaveable { mutableStateOf(false) }
    var viewingImageIndex by remember { mutableIntStateOf(-1) }
    var contentFieldValue by remember {
        mutableStateOf(TextFieldValue(state.content, TextRange(state.content.length)))
    }
    var contentInitialized by rememberSaveable { mutableStateOf(false) }
    var showDictationDisclosure by rememberSaveable { mutableStateOf(false) }
    var pendingDictationTarget by rememberSaveable { mutableStateOf<String?>(null) }
    // Starts expanded, so nothing a user already had in front of them disappears on upgrade.
    var checkedExpanded by rememberSaveable { mutableStateOf(true) }
    var showEditorMenu by remember { mutableStateOf(false) }
    var showConvertWarning by rememberSaveable { mutableStateOf(false) }

    // Only meaningful for a checklist: converting the other way loses nothing.
    val tickedItemCount = if (state.type == NoteType.CHECKLIST) state.checklistItems.count { it.isChecked } else 0
    val indentedItemCount = if (state.type == NoteType.CHECKLIST) {
        state.checklistItems.count { it.indentLevel > 0 }
    } else {
        0
    }

    if (showConvertWarning) {
        AlertDialog(
            onDismissRequest = { showConvertWarning = false },
            title = { Text("Convert to a note?") },
            text = {
                // Naming the actual counts, because "some formatting will be lost" is the kind of
                // warning people dismiss without reading. This one is losing their ticks.
                val losses = buildList {
                    if (tickedItemCount > 0) {
                        add("$tickedItemCount ticked ${if (tickedItemCount == 1) "item" else "items"}")
                    }
                    if (indentedItemCount > 0) {
                        add("indentation on $indentedItemCount ${if (indentedItemCount == 1) "item" else "items"}")
                    }
                }
                val restoreNote = if (tickedItemCount > 0) {
                    " Switching back will not restore the ticks."
                } else {
                    ""
                }
                Text(
                    "A note is plain text, so ${losses.joinToString(" and ")} will be lost. " +
                        "The item text itself is kept.$restoreNote",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConvertWarning = false
                        viewModel.onToggleType()
                    },
                ) { Text("Convert") }
            },
            dismissButton = { TextButton(onClick = { showConvertWarning = false }) { Text("Cancel") } },
        )
    }

    val lazyListState = rememberLazyListState()
    // Keys, not indices: the checklist displays as two filtered sections, so a row's
    // on-screen position is not its index in the ViewModel's list. tempId is the one
    // identity both sides agree on.
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.onReorderChecklistItems(from.key, to.key)
    }
    val isHeaderCollapsed by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 ||
                (lazyListState.firstVisibleItemScrollOffset > 50 && state.checklistItems.size > 5)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.message.collect { snackbarHostState.showSnackbar(it) }
    }

    // One-shot, once the note has finished loading.
    LaunchedEffect(state.isLoaded) {
        if (state.isLoaded && !contentInitialized) {
            contentFieldValue = TextFieldValue(state.content, TextRange(state.content.length))
            contentInitialized = true
        }
    }

    // Only on deliberate external writes — a template, extracted text. Syncing on
    // every keystroke would form a loop that drags the caret to the end as you type.
    LaunchedEffect(Unit) {
        viewModel.contentExternalUpdate.collect { newContent ->
            contentFieldValue = TextFieldValue(newContent, TextRange(newContent.length))
        }
    }

    LaunchedEffect(state.images.size, ocrHintShown, viewModel.ocrAvailable) {
        if (viewModel.ocrAvailable && state.images.isNotEmpty() && !ocrHintShown) {
            snackbarHostState.showSnackbar("Tip: tap an image to extract text from it")
            viewModel.dismissOcrHint()
        }
    }

    val focusRequesters = remember { mutableStateListOf<FocusRequester>() }
    LaunchedEffect(state.checklistItems.size) {
        while (focusRequesters.size < state.checklistItems.size) focusRequesters.add(FocusRequester())
        while (focusRequesters.size > state.checklistItems.size) focusRequesters.removeAt(focusRequesters.lastIndex)
    }

    LaunchedEffect(Unit) {
        viewModel.focusItemIndex.collect { index ->
            // The row has to exist and be composed before it can take focus — and a row below the
            // fold is not composed at all. Its FocusRequester is never attached, requestFocus()
            // throws, and the caret silently stays put while everything the user types next lands
            // in the *previous* item. Waiting longer cannot fix that; only scrolling can.
            //
            // Unchecked rows are one lazy item each, in list order, so a checklist index maps to a
            // lazy index by counting the unchecked items ahead of it. New rows are always
            // unchecked, which is the only case that reaches here.
            delay(FOCUS_LAYOUT_SETTLE_MS)
            val items = viewModel.uiState.value.checklistItems
            if (items.getOrNull(index)?.isChecked == false) {
                val lazyIndex = items.take(index).count { !it.isChecked }
                // Only when it is genuinely off screen: scrollToItem puts the row at the top of
                // the viewport, which would be a visible lurch for a row already in view.
                val onScreen = lazyListState.layoutInfo.visibleItemsInfo.any { it.index == lazyIndex }
                if (!onScreen) {
                    runCatching { lazyListState.scrollToItem(lazyIndex) }
                    delay(FOCUS_LAYOUT_SETTLE_MS)
                }
            }
            if (index in focusRequesters.indices) {
                // Still guarded: the row can be deleted between the emit and here. What this no
                // longer hides is the routine off-screen case, which the scroll above handles.
                runCatching { focusRequesters[index].requestFocus() }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let { viewModel.addImageFromUri(it) } }

    // rememberSaveable, not remember: the camera is a separate activity, and rotating or folding
    // the device while it is open recreates this one. Plain remember loses the pending uri, and
    // the result callback then reads null and discards a photo the user watched themselves take.
    // The path travels as a String because File is not one of the types the saver handles.
    var cameraImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var cameraTempPath by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val tempFile = cameraTempPath?.let(::File)
        val captured = cameraImageUri
        cameraTempPath = null
        cameraImageUri = null
        when {
            success && captured != null ->
                // The copy runs on a coroutine, so deleting the temp file here would race it —
                // and win. Cleanup waits until the copy has finished reading.
                viewModel.addImageFromUri(captured) { tempFile?.delete() }
            // The camera reported a picture but we no longer know where it went. Saying so beats
            // the silence this used to produce, which was indistinguishable from a dead shutter.
            success -> {
                tempFile?.delete()
                coroutineScope.launch { snackbarHostState.showSnackbar("That photo could not be attached. Try again.") }
            }
            // Cancelled: no picture was taken, so there is nothing to report.
            else -> tempFile?.delete()
        }
    }

    val launchCameraInternal: () -> Unit = {
        val tempDir = File(context.cacheDir, "camera_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "photo_${System.currentTimeMillis()}.jpg")
        cameraTempPath = tempFile.absolutePath
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
        cameraImageUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) launchCameraInternal() }

    val launchCamera: () -> Unit = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }

    var dictationTarget by remember { mutableStateOf(DICTATION_TARGET_CONTENT) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (spoken != null) {
                when {
                    dictationTarget == DICTATION_TARGET_TITLE ->
                        viewModel.onTitleChange(
                            if (state.title.isBlank()) spoken else "${state.title} $spoken",
                        )

                    state.type == NoteType.TEXT -> {
                        // Insert at the caret rather than appending.
                        val selection = contentFieldValue.selection
                        val before = contentFieldValue.text.substring(0, selection.start)
                        val after = contentFieldValue.text.substring(selection.end)
                        val newText = before + spoken + after
                        contentFieldValue = TextFieldValue(newText, TextRange(selection.start + spoken.length))
                        viewModel.onContentChange(newText)
                    }

                    else -> viewModel.onDictatedText(spoken)
                }
            }
        }
    }

    val launchDictationInternal: (String) -> Unit = { target ->
        dictationTarget = target
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                if (target == DICTATION_TARGET_TITLE) "Speak your title…" else "Speak your note…",
            )
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Speech recognition is unavailable on this device.")
            }
        }
    }

    val launchDictation: (String) -> Unit = { target ->
        if (dictationAcknowledged) {
            launchDictationInternal(target)
        } else {
            pendingDictationTarget = target
            showDictationDisclosure = true
        }
    }

    BackHandler {
        viewModel.save()
        onBack()
    }

    if (showDictationDisclosure) {
        DictationDisclosureDialog(
            onDismiss = {
                showDictationDisclosure = false
                pendingDictationTarget = null
            },
            onConfirm = {
                val target = pendingDictationTarget
                showDictationDisclosure = false
                pendingDictationTarget = null
                viewModel.acknowledgeDictationDisclosure()
                if (target != null) launchDictationInternal(target)
            },
        )
    }

    if (showTemplates) {
        TemplatePickerBottomSheet(
            onDismiss = { showTemplates = false },
            onTemplateSelected = { template ->
                viewModel.applyTemplate(template.title, template.type, template.items)
                showTemplates = false
            },
        )
    }

    state.images.getOrNull(viewingImageIndex)?.let { image ->
        val file = viewModel.imageFile(image.filePath)
        if (file.exists()) {
            FullScreenImageViewer(
                imageFile = file,
                onDismiss = { viewingImageIndex = -1 },
                isExtracting = state.isExtractingText,
                onExtractText = if (viewModel.ocrAvailable) {
                    {
                        viewModel.extractTextFrom(image.filePath)
                        viewingImageIndex = -1
                    }
                } else {
                    null
                },
            )
        }
    }

    val screenTitle = when {
        state.isNew && state.type == NoteType.CHECKLIST -> "New checklist"
        state.isNew -> "New note"
        state.type == NoteType.CHECKLIST -> "Edit checklist"
        else -> "Edit note"
    }

    val hasAnyContent = state.title.isNotBlank() ||
        state.content.isNotBlank() ||
        state.checklistItems.any { it.text.isNotBlank() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            NotesTopBar(
                title = screenTitle,
                onBack = {
                    viewModel.save()
                    onBack()
                },
                actions = {
                    IconButton(
                        onClick = {
                            // Converting to a note keeps the item text and drops everything else.
                            // Ask first, but only when there is something to drop — a warning on a
                            // conversion that loses nothing is just a step to dismiss.
                            if (tickedItemCount > 0 || indentedItemCount > 0) {
                                showConvertWarning = true
                            } else {
                                viewModel.onToggleType()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (state.type == NoteType.TEXT) {
                                Icons.Filled.Checklist
                            } else {
                                Icons.Filled.Notes
                            },
                            contentDescription = if (state.type == NoteType.TEXT) {
                                "Switch to checklist"
                            } else {
                                "Switch to note"
                            },
                        )
                    }
                    IconButton(
                        onClick = { context.shareNote(state) },
                        enabled = hasAnyContent,
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                    // Only earns a slot when there is something checked to act on: on a text note
                    // or a fresh checklist both entries would be dead.
                    if (state.type == NoteType.CHECKLIST && state.checklistItems.any { it.isChecked }) {
                        IconButton(onClick = { showEditorMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showEditorMenu,
                            onDismissRequest = { showEditorMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Uncheck all") },
                                leadingIcon = {
                                    Icon(Icons.Filled.CheckBoxOutlineBlank, contentDescription = null)
                                },
                                onClick = {
                                    showEditorMenu = false
                                    viewModel.onUncheckAll()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete checked") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = {
                                    showEditorMenu = false
                                    viewModel.onDeleteChecked()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            val showFullHeader = state.type != NoteType.CHECKLIST || !isHeaderCollapsed

            AnimatedVisibility(
                visible = state.type == NoteType.CHECKLIST && isHeaderCollapsed,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { coroutineScope.launch { lazyListState.animateScrollToItem(0) } }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = state.title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = "Show header",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(
                visible = showFullHeader,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    AnimatedVisibility(visible = state.isNew && !hasAnyContent) {
                        TextButton(
                            onClick = { showTemplates = true },
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) {
                            Text("Use a template")
                        }
                    }

                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::onTitleChange,
                        label = { Text("Title") },
                        trailingIcon = {
                            IconButton(onClick = { launchDictation(DICTATION_TARGET_TITLE) }) {
                                Icon(
                                    Icons.Filled.Mic,
                                    contentDescription = "Voice input for title",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    ColorLabelPicker(
                        selected = state.colorLabel,
                        onSelect = viewModel::onColorLabelChange,
                    )

                    if (state.images.isNotEmpty() || state.type != NoteType.TEXT) {
                        ImageAttachmentRow(
                            images = state.images,
                            getImageFile = viewModel::imageFile,
                            onAddImage = {
                                imagePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            onRemoveImage = viewModel::removeImage,
                            onImageClick = { viewingImageIndex = it },
                            onTakePhoto = launchCamera,
                            showOcrBadge = viewModel.ocrAvailable,
                        )
                    }

                    if (state.type == NoteType.CHECKLIST) {
                        IconStylePicker(
                            selected = state.iconStyle,
                            onSelect = viewModel::onIconStyleChange,
                        )
                    }
                }
            }

            when (state.type) {
                NoteType.TEXT -> {
                    if (state.images.isEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    imagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                            ) {
                                Text("Attach image")
                            }
                            TextButton(onClick = launchCamera) { Text("Take photo") }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        OutlinedTextField(
                            value = contentFieldValue,
                            onValueChange = { newValue ->
                                contentFieldValue = newValue
                                viewModel.onContentChange(newValue.text)
                            },
                            label = { Text("Content") },
                            modifier = Modifier.fillMaxSize(),
                        )
                        IconButton(
                            onClick = { launchDictation(DICTATION_TARGET_CONTENT) },
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = "Voice input",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                NoteType.CHECKLIST -> {
                    val suggestions = remember(state.title) {
                        ChecklistSuggestionProvider.getSuggestions(state.title)
                    }
                    val addedTexts = remember(state.checklistItems) {
                        state.checklistItems.map { it.text.lowercase() }.toSet()
                    }
                    val filteredSuggestions = suggestions.filter { it.lowercase() !in addedTexts }

                    AnimatedVisibility(visible = filteredSuggestions.isNotEmpty()) {
                        Column {
                            Text(
                                text = "Suggestions",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            FlowRow(modifier = Modifier.padding(bottom = 4.dp)) {
                                filteredSuggestions.take(8).forEach { suggestion ->
                                    AssistChip(
                                        onClick = { viewModel.addSuggestedItem(suggestion) },
                                        label = {
                                            Text(suggestion, style = MaterialTheme.typography.labelSmall)
                                        },
                                        modifier = Modifier.padding(end = 4.dp),
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Indices here are positions in the full list; the two sections are
                    // filtered views of it, so `indexed.index` is what the ViewModel needs.
                    val uncheckedItems = state.checklistItems.withIndex().filter { !it.value.isChecked }
                    val checkedItems = state.checklistItems.withIndex().filter { it.value.isChecked }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = bottomScrollBuffer),
                    ) {
                        itemsIndexed(
                            uncheckedItems,
                            key = { _, indexed -> indexed.value.tempId },
                        ) { _, indexed ->
                            val actualIndex = indexed.index
                            ReorderableItem(reorderableState, key = indexed.value.tempId) { isDragging ->
                                ChecklistItemRow(
                                    text = indexed.value.text,
                                    isChecked = false,
                                    onTextChange = { viewModel.onChecklistItemTextChange(actualIndex, it) },
                                    onCheckedChange = {
                                        viewModel.onChecklistItemCheckedChange(actualIndex, it)
                                    },
                                    onEnterPressed = { viewModel.onAddChecklistItem(actualIndex) },
                                    onDelete = { viewModel.onDeleteChecklistItem(actualIndex) },
                                    canDelete = state.checklistItems.size > 1,
                                    focusRequester = focusRequesters.getOrNull(actualIndex)
                                        ?: FocusRequester(),
                                    indentLevel = indexed.value.indentLevel,
                                    iconStyle = state.iconStyle,
                                    onIndent = { viewModel.onIndentItem(actualIndex) },
                                    onOutdent = { viewModel.onOutdentItem(actualIndex) },
                                    dragHandleModifier = Modifier.draggableHandle(),
                                    modifier = Modifier.background(
                                        if (isDragging) {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        } else {
                                            Color.Transparent
                                        },
                                    ),
                                )
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = { viewModel.onAddChecklistItem() }) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = "Add item",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = "Add item",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = { launchDictation(DICTATION_TARGET_CONTENT) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Mic,
                                        contentDescription = "Voice input",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }

                        if (checkedItems.isNotEmpty()) {
                            item {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                // The header is the fold control. A long grocery list ends up
                                // mostly checked, and that dead weight otherwise sits between you
                                // and the items you still need.
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable { checkedExpanded = !checkedExpanded }
                                        .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = if (checkedExpanded) {
                                            Icons.Filled.ExpandLess
                                        } else {
                                            Icons.Filled.ExpandMore
                                        },
                                        contentDescription = if (checkedExpanded) {
                                            "Hide checked items"
                                        } else {
                                            "Show checked items"
                                        },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "${checkedItems.size} checked " +
                                            if (checkedItems.size == 1) "item" else "items",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                            itemsIndexed(
                                if (checkedExpanded) checkedItems else emptyList(),
                                key = { _, indexed -> indexed.value.tempId },
                            ) { _, indexed ->
                                val actualIndex = indexed.index
                                ChecklistItemRow(
                                    text = indexed.value.text,
                                    isChecked = true,
                                    onTextChange = { viewModel.onChecklistItemTextChange(actualIndex, it) },
                                    onCheckedChange = {
                                        viewModel.onChecklistItemCheckedChange(actualIndex, it)
                                    },
                                    onEnterPressed = {},
                                    onDelete = { viewModel.onDeleteChecklistItem(actualIndex) },
                                    canDelete = state.checklistItems.size > 1,
                                    focusRequester = focusRequesters.getOrNull(actualIndex) ?: FocusRequester(),
                                    indentLevel = indexed.value.indentLevel,
                                    iconStyle = state.iconStyle,
                                    // Checked items arrive here from the section above; without
                                    // this they teleport. Safe on this half — these rows are not
                                    // drag targets, so there is no gesture for it to fight.
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DictationDisclosureDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Voice input disclosure") },
        text = {
            Text(
                "Voice input uses your device's speech recognition provider. Depending on your " +
                    "device, spoken audio and transcripts may be processed by that provider under " +
                    "its own privacy terms.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ColorLabelPicker(selected: String?, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .border(
                    width = if (selected == null) 2.dp else 1.dp,
                    color = if (selected == null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape,
                )
                .clickable { onSelect(null) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.FormatColorReset,
                contentDescription = "No colour",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NoteCategorizer.noteColors.forEach { (label, color) ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (selected == label) 2.dp else 0.dp,
                        color = if (selected == label) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(label) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected == label) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = label,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun IconStylePicker(selected: ChecklistIconStyle, onSelect: (ChecklistIconStyle) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Style",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val styles = listOf(
            ChecklistIconStyle.CHECKBOX to Icons.Filled.CheckBox,
            ChecklistIconStyle.CIRCLE to Icons.Filled.RadioButtonUnchecked,
            ChecklistIconStyle.STAR to Icons.Filled.Star,
            ChecklistIconStyle.HEART to Icons.Filled.Favorite,
            ChecklistIconStyle.SQUARE to Icons.Filled.Check,
        )
        styles.forEach { (style, icon) ->
            IconButton(onClick = { onSelect(style) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = icon,
                    contentDescription = style.name.lowercase().replaceFirstChar { it.uppercase() },
                    modifier = Modifier.size(20.dp),
                    tint = if (selected == style) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                )
            }
        }
    }
}

private fun android.content.Context.shareNote(state: NoteEditUiState) {
    val items = state.checklistItems.map {
        ChecklistItem(text = it.text, isChecked = it.isChecked, indentLevel = it.indentLevel)
    }
    val text = NoteShareFormatter.formatForSharing(state.title, state.content, state.type, items)
    val html = NoteShareFormatter.formatAsHtml(state.title, state.content, state.type, items)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, state.title)
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_HTML_TEXT, html)
    }
    startActivity(Intent.createChooser(intent, "Share"))
}
