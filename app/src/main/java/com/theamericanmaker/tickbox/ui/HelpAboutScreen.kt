// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.theamericanmaker.tickbox.ocr.OcrBuild
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val SOURCE_URL = "https://github.com/TheAmericanMaker/Tickbox"
private const val AUTHOR_URL = "https://github.com/TheAmericanMaker"

/** One instruction: what you want to do, and how you do it. */
private data class HowTo(val task: String, val how: String)

private val EDITING = listOf(
    HowTo("Start a list or a note", "Tap + and choose New checklist or New note."),
    HowTo("Add the next item", "Press Enter at the end of an item."),
    HowTo("Tick something off", "Tap its box. Ticked items drop to their own section at the bottom."),
    HowTo(
        "Indent an item",
        "Drag its tick box to the right. Drag it left to move it back out. " +
            "Tapping the box still ticks it — only a sideways drag indents.",
    ),
    HowTo("Move an item", "Drag the six-dot grip on its left up or down."),
    HowTo("Delete one item", "Tap the item, then the cross that appears on its right."),
    HowTo("Hide the ticked ones", "Tap the \"N checked items\" heading to fold that section away."),
    HowTo(
        "Clear the ticked ones",
        "Tap the three-dot menu in the top corner while editing, then Uncheck all or " +
            "Delete checked. It only appears when something is ticked.",
    ),
    HowTo(
        "Turn a list into a note, or back",
        "The button beside Share in the top corner swaps between the two. Indentation is kept " +
            "either way. Ticks come back too, as long as you switch straight back without " +
            "editing the note or leaving it.",
    ),
    HowTo(
        "Paste a list in from somewhere else",
        "Paste it into a note and switch to a checklist. Bullets, numbers and [x] marks are " +
            "understood, so a list copied from another app arrives with its ticks and " +
            "sub-items intact.",
    ),
)

private val NOTES_AND_LISTS = listOf(
    HowTo("Delete a note", "Swipe it left. Undo appears for a few seconds."),
    HowTo("Keep one at the top", "Tap the pin on its card."),
    HowTo("Find something", "The magnifier searches titles and text."),
    HowTo("Show only one kind", "The All / Notes / Checklists chips."),
    HowTo("Start from a template", "Use a template, at the top of a new checklist."),
    HowTo("Colour-code a note", "The row of dots under the title."),
    HowTo("Change the tick shape", "The Style row — box, circle, star, heart or check."),
)

private val ATTACH_A_PHOTO =
    HowTo("Attach a photo", "Gallery or Camera in the editor. Up to five per note.")

private val EXTRACT_TEXT = HowTo(
    "Pull the text out of a photo",
    "Tap the photo, then Extract text. It runs on the phone and takes a moment. " +
        "Flat, evenly lit pages read best.",
)

private val TALK_INSTEAD = HowTo(
    "Talk instead of typing",
    "The microphone beside the title, the body, or Add item.",
)

/**
 * Omits the extraction how-to in the `noOcr` variant.
 *
 * A how-to for a button that is not there is worse than no how-to: the reader goes looking,
 * fails, and concludes the app is broken rather than that it is a different build.
 */
private fun photosAndVoice(ocrAvailable: Boolean): List<HowTo> =
    if (ocrAvailable) {
        listOf(ATTACH_A_PHOTO, EXTRACT_TEXT, TALK_INSTEAD)
    } else {
        listOf(ATTACH_A_PHOTO, TALK_INSTEAD)
    }

private val KEEPING_IT_SAFE = listOf(
    HowTo(
        "Back everything up",
        "The three-dot menu, then Export notes. It writes one ZIP holding every note and " +
            "photo, wherever you choose.",
    ),
    HowTo(
        "Put it back",
        "The three-dot menu, then Import notes. Importing the same file twice makes a second " +
            "copy of everything.",
    ),
    HowTo("Light or dark", "The three-dot menu, then Appearance."),
)

/**
 * How-to and about, on one screen.
 *
 * One screen rather than two menu entries: the questions overlap — someone hunting for "how do I
 * indent" and someone hunting for the licence are both looking for the page that explains the app.
 * Splitting it would mean two routes and two entries for a page each of which fits on one screen.
 *
 * The how-to half is load-bearing rather than decorative. Indenting by dragging the tick box and
 * reordering by the grip are gestures with no visible affordance, and this is currently the only
 * place either is written down for the person using the app.
 */
@Composable
fun HelpAboutScreen(onBack: () -> Unit, ocrAvailable: Boolean = OcrBuild.AVAILABLE) {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    val open: (String) -> Unit = { url ->
        // No INTERNET permission needed, and none is held: this hands the address to whatever
        // browser is installed rather than fetching anything.
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Scaffold(topBar = { NotesTopBar(title = "Help & about", onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            section("Lists and items", EDITING)
            section("Your notes", NOTES_AND_LISTS)
            section("Photos and voice", photosAndVoice(ocrAvailable))
            section("Backups and appearance", KEEPING_IT_SAFE)

            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tickbox${if (version.isBlank()) "" else " $version"}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Notes and checklists that stay on your phone. No account, no sync, and no " +
                        "way for anything here to reach the internet — the app holds no network " +
                        "permission at all, which you can check in Android's app info.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                LinkRow("Source code", SOURCE_URL) { open(SOURCE_URL) }
                LinkRow("Made by James Sesler", AUTHOR_URL) { open(AUTHOR_URL) }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Free software under the GNU General Public License, version 3 or later. " +
                        "You are free to use, study, share and change it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String, entries: List<HowTo>) {
    item {
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
    }
    items(entries) { entry ->
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Text(entry.task, style = MaterialTheme.typography.bodyLarge)
            Text(
                entry.how,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LinkRow(label: String, url: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            url.removePrefix("https://"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
