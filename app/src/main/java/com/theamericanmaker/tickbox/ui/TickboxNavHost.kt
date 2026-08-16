// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.theamericanmaker.tickbox.data.model.NoteType
import com.theamericanmaker.tickbox.ui.edit.NoteEditScreen
import com.theamericanmaker.tickbox.ui.list.NoteListScreen

private const val ROUTE_LIST = "notes"
private const val ROUTE_EDIT = "notes/{noteId}?type={type}"
private const val ROUTE_HELP = "help"

/** Sentinel for "this note does not exist yet". */
private const val NEW_NOTE_ID = -1L

@Composable
fun TickboxNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = ROUTE_LIST) {
        composable(ROUTE_LIST) {
            NoteListScreen(
                onNoteClick = { id -> navController.navigate("notes/$id") },
                onNewNote = { navController.navigate("notes/$NEW_NOTE_ID?type=${NoteType.TEXT.name}") },
                onNewChecklist = {
                    navController.navigate("notes/$NEW_NOTE_ID?type=${NoteType.CHECKLIST.name}")
                },
                onOpenHelp = { navController.navigate(ROUTE_HELP) },
            )
        }
        composable(
            route = ROUTE_EDIT,
            arguments = listOf(
                // String, not Long: a Long argument cannot carry the -1 "new note"
                // sentinel through the route.
                navArgument("noteId") { type = NavType.StringType },
                navArgument("type") {
                    type = NavType.StringType
                    defaultValue = NoteType.TEXT.name
                },
            ),
        ) {
            NoteEditScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_HELP) {
            HelpAboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
