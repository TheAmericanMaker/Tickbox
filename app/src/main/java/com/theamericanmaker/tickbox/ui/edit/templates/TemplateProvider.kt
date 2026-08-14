// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ui.edit.templates

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.ui.graphics.vector.ImageVector
import com.theamericanmaker.tickbox.data.model.NoteType

data class NoteTemplate(
    val name: String,
    val icon: ImageVector,
    val type: NoteType,
    val title: String,
    val items: List<String>
)

object TemplateProvider {
    val templates = listOf(
        NoteTemplate(
            name = "Grocery List",
            icon = Icons.Filled.ShoppingCart,
            type = NoteType.CHECKLIST,
            title = "Grocery List",
            items = listOf(
                "Fruits & Vegetables", "Milk", "Eggs", "Bread",
                "Chicken", "Rice", "Pasta", "Cheese",
                "Butter", "Cereal"
            )
        ),
        NoteTemplate(
            name = "To-Do List",
            icon = Icons.Filled.TaskAlt,
            type = NoteType.CHECKLIST,
            title = "To-Do",
            items = listOf(
                "High priority task", "Medium priority task", "Low priority task"
            )
        ),
        NoteTemplate(
            name = "Meeting Notes",
            icon = Icons.Filled.Groups,
            type = NoteType.TEXT,
            title = "Meeting Notes",
            items = listOf(
                "Date: ",
                "Attendees: ",
                "",
                "Agenda:",
                "1. ",
                "2. ",
                "3. ",
                "",
                "Discussion:",
                "",
                "Action Items:",
                "- ",
                "- "
            )
        ),
        NoteTemplate(
            name = "Travel Packing",
            icon = Icons.Filled.FlightTakeoff,
            type = NoteType.CHECKLIST,
            title = "Packing List",
            items = listOf(
                "Passport / ID", "Phone charger", "Toothbrush & toiletries",
                "Medications", "Change of clothes", "Underwear & socks",
                "Jacket / layers", "Snacks", "Water bottle",
                "Headphones", "Book / entertainment"
            )
        ),
        NoteTemplate(
            name = "Recipe",
            icon = Icons.Filled.Restaurant,
            type = NoteType.TEXT,
            title = "Recipe: ",
            items = listOf(
                "Servings: ",
                "Prep time: ",
                "Cook time: ",
                "",
                "Ingredients:",
                "- ",
                "- ",
                "- ",
                "",
                "Instructions:",
                "1. ",
                "2. ",
                "3. "
            )
        ),
        NoteTemplate(
            name = "Shopping List",
            icon = Icons.Filled.Checklist,
            type = NoteType.CHECKLIST,
            title = "Shopping List",
            items = listOf(
                "Item 1", "Item 2", "Item 3"
            )
        )
    )
}
